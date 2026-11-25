package command_test

import (
	"path/filepath"
	"regexp"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

var (
	commandInText = regexp.MustCompile(`\b(lvh-yard (ws|catalog|register|publish|status|remove|service|update|export|import|profiles|priority|alias|containers|test)\b|ws (yard|tls|compose|init|set|show)\b|docker compose\b)`)
	quotedText    = regexp.MustCompile(`"[^"]*"`)
)

// badCommandLines gives the lines that show a command inside a sentence, without double quotes.
// A command that starts an indented line stands on its own and is fine.
func badCommandLines(text string) []string {
	var bad []string
	for line := range strings.SplitSeq(text, "\n") {
		unquoted := quotedText.ReplaceAllString(line, "")
		if loc := commandInText.FindStringIndex(unquoted); loc != nil && loc[0] != len(unquoted)-len(strings.TrimLeft(unquoted, " ")) {
			bad = append(bad, line)
		}
	}
	return bad
}

func TestCommandsInTextStandAloneOrInQuotes(t *testing.T) {
	if len(badCommandLines("Run ws yard share first.\n  1. Then: lvh-yard ws init")) != 2 || len(badCommandLines("Run \"ws yard share\" first.\n  lvh-yard ws init\n       docker compose up -d")) != 0 {
		t.Fatal("the check does not tell a command in a sentence from a command on its own line")
	}
}

func TestHelpShowsCommandsOnTheirOwnLineOrInQuotes(t *testing.T) {
	for path := range commands {
		run := commandtest.New(t).Run(append(strings.Fields(path), "--help")...)
		if run.ExitCode != 0 {
			t.Fatalf("%s --help: %+v", path, run)
		}
		for _, line := range badCommandLines(run.Stdout) {
			t.Errorf("%s --help has a command in a sentence, without quotes:\n%s", path, line)
		}
	}
}

// The same rule holds for what the commands print, and for their errors.
func TestPrintedCommandsStandAloneOrInQuotes(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.LANIP = "192.168.1.10"
	folder := filepath.Join(t.TempDir(), "ws")
	empty := filepath.Join(t.TempDir(), "none")
	for _, args := range [][]string{
		{"ws", "--dir", empty, "show"},
		{"ws", "--dir", empty, "compose", "update"},
		{"ws", "--dir", folder, "init", "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "--dir", folder, "set", "workstation", "ws-b"},
		{"ws", "--dir", folder, "set", "peers", "192.168.1.11"},
		{"ws", "--dir", folder, "yard", "init"},
		{"ws", "--dir", folder, "yard", "share", "ws-b", "--password", "pw"},
		{"ws", "--dir", folder, "tls", "https", "create"},
		{"ws", "--dir", folder, "tls", "client", "enable"},
		{"ws", "--dir", folder, "tls", "ca", "create", "--ca-key-password="},
		{"ws", "--dir", folder, "yard", "init"},
		{"ws", "--dir", folder, "yard", "init"},
		{"ws", "--dir", folder, "yard", "share", "ws-b", "--password", "pw"},
		{"ws", "--dir", folder, "yard", "join", filepath.Join(folder, "certificates", "ws-b-yard.zip"), "--password", "pw"},
		{"ws", "--dir", folder, "tls", "ca", "issue", "service", "svc"},
		{"ws", "--dir", folder, "tls", "https", "create"},
		{"ws", "--dir", folder, "tls", "client", "create"},
		{"ws", "--dir", folder, "tls", "mtls", "create"},
		{"ws", "--dir", folder, "tls", "mtls", "enable"},
		{"ws", "--dir", folder, "compose", "update"},
		{"ws", "--dir", folder, "yard", "standalone"},
	} {
		run := yard.Run(args...)
		for _, line := range badCommandLines(run.Stdout + "\n" + run.Stderr) {
			t.Errorf("%v prints a command in a sentence, without quotes:\n%s", args, line)
		}
	}
}
