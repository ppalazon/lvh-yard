package command_test

import (
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

// dev/install.sh reads this line, so it must hold the method and nothing else.
func TestInstallMethodPrintsOnlyTheMethod(t *testing.T) {
	for method, want := range map[string]string{"standalone": "standalone\n", "deb": "deb\n", "": "\n"} {
		yard := commandtest.New(t)
		yard.InstallMethod = method
		run := yard.Run("install-method")
		if run.ExitCode != 0 || run.Stdout != want || run.Stderr != "" {
			t.Errorf("install-method for %q: %+v", method, run)
		}
	}
}

func TestInstallMethodIsHidden(t *testing.T) {
	if run := commandtest.New(t).Run("--help"); strings.Contains(run.Stdout, "install-method") {
		t.Fatalf("help names install-method:\n%s", run.Stdout)
	}
}
