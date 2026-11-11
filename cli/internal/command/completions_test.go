package command_test

import (
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestCompletionsPrintsAScriptForEachShell(t *testing.T) {
	for shell, marker := range map[string]string{"bash": "bash completion V2 for lvh-yard", "zsh": "#compdef lvh-yard"} {
		run := commandtest.New(t).Run("completions", shell)
		if run.ExitCode != 0 || run.Stderr != "" || !strings.Contains(run.Stdout, marker) {
			t.Errorf("%s: exit %d, stderr %q, stdout starts %.60q", shell, run.ExitCode, run.Stderr, run.Stdout)
		}
	}
}

func TestCompletionsRejectsOtherShells(t *testing.T) {
	for _, args := range [][]string{{"completions"}, {"completions", "fish"}, {"completions", "bash", "zsh"}} {
		run := commandtest.New(t).Run(args...)
		if run.ExitCode != 1 || run.Stdout != "" || run.Stderr == "" {
			t.Errorf("%v: %+v", args, run)
		}
	}
}

func TestCompletionsIsHidden(t *testing.T) {
	run := commandtest.New(t).Run("--help")
	if strings.Contains(run.Stdout, "completions") || strings.Contains(run.Stdout, "completion ") {
		t.Fatalf("help names completions:\n%s", run.Stdout)
	}
}

func TestShellCompletesFixedValues(t *testing.T) {
	cases := []struct {
		args []string
		want []string
	}{
		{[]string{"catalog", "--state", ""}, []string{"unpublished", "unavailable", "unhealthy", "degraded", "healthy"}},
		{[]string{"publish", "web", "--scope", ""}, []string{"local", "yard"}},
		{[]string{"publish", "web", "--access-log", ""}, []string{"on", "off"}},
		{[]string{"register", "static", "web", "--check", ""}, []string{"tcp", "http"}},
		{[]string{"completions", ""}, []string{"bash", "zsh"}},
	}
	for _, c := range cases {
		run := commandtest.New(t).Run(append([]string{"__complete"}, c.args...)...)
		lines := strings.Split(strings.TrimSpace(run.Stdout), "\n")
		// The last line is the directive: :4 is ShellCompDirectiveNoFileComp.
		if run.ExitCode != 0 || lines[len(lines)-1] != ":4" || strings.Join(lines[:len(lines)-1], " ") != strings.Join(c.want, " ") {
			t.Errorf("%v: %q", c.args, run.Stdout)
		}
	}
}
