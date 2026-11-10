package command_test

import (
	"errors"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"github.com/spf13/cobra"
)

func TestCommandFailureUsesStderrAndExitOne(t *testing.T) {
	yard := commandtest.New(t)
	yard.Groups = []func(*command.Options) *cobra.Command{
		func(options *command.Options) *cobra.Command {
			return &cobra.Command{
				Use: "fail",
				RunE: func(cmd *cobra.Command, args []string) error {
					return errors.New("dashboard does not answer")
				},
			}
		},
	}
	run := yard.Run("fail")
	if run.ExitCode != 1 || run.Stdout != "" || !strings.Contains(run.Stderr, "dashboard does not answer") {
		t.Fatalf("command error: %+v", run)
	}
}
