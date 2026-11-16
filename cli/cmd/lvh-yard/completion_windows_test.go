//go:build integration

package main_test

import (
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

// Each PowerShell on the PATH loads the completion script and completes as the Tab key does.
func TestPowerShellCompletesCommandsAndValues(t *testing.T) {
	if filepath.Base(executable) != executableName("lvh-yard") {
		t.Fatalf("the executable must have the name lvh-yard: %s", executable)
	}
	for name, shell := range shells(t) {
		for typed, want := range map[string][]string{
			"lvh-yard ws comp":              {"compose"},
			"lvh-yard ws compose ":          {"logs", "recreate", "restart", "start", "stop", "upgrade"},
			"lvh-yard publish web --scope ": {"local", "yard"},
			"lvh-yard completions p":        {"powershell"},
		} {
			t.Run(name+" "+typed, func(t *testing.T) {
				cmd := shell("lvh-yard completions powershell | Out-String | Invoke-Expression\n" +
					"$typed = '" + typed + "'\n" +
					"(TabExpansion2 -inputScript $typed -cursorColumn $typed.Length).CompletionMatches | ForEach-Object { $_.CompletionText.Trim() }")
				cmd.Env = append(os.Environ(), pathWith(filepath.Dir(executable)))
				output, err := cmd.Output()
				if err != nil {
					t.Fatalf("%v\n%s", err, output)
				}
				got := strings.Fields(string(output))
				slices.Sort(got)
				if !slices.Equal(got, want) {
					t.Fatalf("completions of %q: %q, want %q", typed, got, want)
				}
			})
		}
	}
}
