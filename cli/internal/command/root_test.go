package command_test

import (
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestVersionNamesThisBuild(t *testing.T) {
	for _, version := range []string{"", "0.1.0", "0.2.0-rc.1"} {
		t.Run(version, func(t *testing.T) {
			yard := commandtest.New(t)
			yard.Version = version
			run := yard.Run("--version")
			want := version
			if want == "" {
				want = "dev"
			}
			if run.ExitCode != 0 || run.Stdout != "lvh-yard "+want+"\n" || run.Stderr != "" {
				t.Fatalf("version: %+v", run)
			}
		})
	}
}

func TestUsageErrorsUseStderrAndExitOne(t *testing.T) {
	for _, args := range [][]string{{"--unknown"}, {"--dashboard"}, {"unknown"}} {
		run := commandtest.New(t).Run(args...)
		if run.ExitCode != 1 || run.Stdout != "" || run.Stderr == "" {
			t.Fatalf("usage error for %v: %+v", args, run)
		}
	}
}
