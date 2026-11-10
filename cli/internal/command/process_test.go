package command_test

import (
	"errors"
	"fmt"
	"io"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"github.com/spf13/cobra"
)

func TestCommandReceivesInputAndFakeProcess(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.Env["EDITOR"] = "code --wait"
	yard.Process.Stdin = "a profile\n"
	yard.Process.Terminal = true
	yard.Process.Identity = func(flag string) string {
		if flag != "-u" {
			t.Fatalf("unexpected id flag: %s", flag)
		}
		return "1000"
	}
	yard.Process.Editor = func(editor, path string) error {
		if editor != "code --wait" || path != "/tmp/a profile.yaml" {
			t.Fatalf("unexpected editor call: %s %s", editor, path)
		}
		return nil
	}
	yard.Groups = []func(*command.Options) *cobra.Command{
		func(options *command.Options) *cobra.Command {
			return &cobra.Command{
				Use: "process",
				RunE: func(cmd *cobra.Command, args []string) error {
					input, err := io.ReadAll(cmd.InOrStdin())
					if err != nil {
						return err
					}
					process := options.Process
					if err := process.Edit(process.Getenv("EDITOR"), "/tmp/a profile.yaml"); err != nil {
						return err
					}
					fmt.Fprintf(cmd.OutOrStdout(), "%t %s %s", process.IsTerminal(), process.ID("-u"), input)
					return nil
				},
			}
		},
	}
	run := yard.Run("process")
	if run.ExitCode != 0 || run.Stderr != "" || run.Stdout != "true 1000 a profile\n" {
		t.Fatalf("process seam: %+v", run)
	}
}

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
