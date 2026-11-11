package command_test

import (
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWSReplacesTopLevelInit(t *testing.T) {
	run := commandtest.New(t).Run("init")
	if run.ExitCode != 1 || !strings.Contains(run.Stderr, "unknown command") {
		t.Fatalf("old init accepted: %+v", run)
	}
	for _, args := range [][]string{{"--help"}, {"__complete", ""}} {
		run := commandtest.New(t).Run(args...)
		if run.ExitCode != 0 || !strings.Contains(run.Stdout, "ws") {
			t.Fatalf("missing ws: %+v", run)
		}
		for _, line := range strings.Split(run.Stdout, "\n") {
			if fields := strings.Fields(line); len(fields) > 0 && fields[0] == "init" {
				t.Fatalf("root still lists init: %s", run.Stdout)
			}
		}
	}
	for _, args := range [][]string{{"ws", "--help"}, {"__complete", "ws", ""}} {
		run := commandtest.New(t).Run(args...)
		if run.ExitCode != 0 || !strings.Contains(run.Stdout, "init") || !strings.Contains(run.Stdout, "upgrade") {
			t.Fatalf("missing ws commands: %+v", run)
		}
	}
}

func TestWSDirWorksBeforeSubcommands(t *testing.T) {
	folder := filepath.Join(t.TempDir(), "workstation")
	yard := commandtest.New(t)
	run := yard.Run("ws", "--dir", folder, "init", "--workstation", "ws-a")
	if run.ExitCode != 0 || !strings.Contains(readInitFile(t, folder, ".env"), "YARD_WORKSTATION=ws-a\n") {
		t.Fatalf("init with group dir: %+v", run)
	}
	run = yard.Run("ws", "--dir", folder, "upgrade")
	if run.ExitCode != 0 || !strings.Contains(run.Stdout, "already this version's") {
		t.Fatalf("upgrade with group dir: %+v", run)
	}
}
