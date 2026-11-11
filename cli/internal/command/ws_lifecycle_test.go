package command_test

import (
	"io"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWSLifecycleRunsCompose(t *testing.T) {
	for _, test := range []struct {
		command string
		args    []string
	}{
		{"start", []string{"up", "-d"}},
		{"stop", []string{"stop"}},
		{"restart", []string{"restart"}},
		{"logs", []string{"logs"}},
	} {
		t.Run(test.command, func(t *testing.T) {
			yard := commandtest.New(t)
			folder := filepath.Join(t.TempDir(), "workstation's folder")
			yard.Process.Env["YARD_CONFIG_DIR"] = folder
			yard.Process.Env["YARD_DASHBOARD_URL"] = ":not-an-http-address"
			yard.Process.RunCommand = func(name string, args []string, dir string, stdout, stderr io.Writer) (int, error) {
				io.WriteString(stdout, "service output\n")
				io.WriteString(stderr, "service error\n")
				return 0, nil
			}
			run := yard.Run("ws", test.command)
			want := append([]string{"compose", "--project-directory", folder}, test.args...)
			if len(yard.Process.Commands) != 1 {
				t.Fatalf("commands: %+v", yard.Process.Commands)
			}
			got := yard.Process.Commands[0]
			if got.Name != "docker" || got.Dir != folder || !reflect.DeepEqual(got.Args, want) {
				t.Fatalf("compose command: %+v, want args %v in %s", got, want, folder)
			}
			line := "docker compose --project-directory '" + strings.ReplaceAll(folder, "'", "'\"'\"'") + "' " + strings.Join(test.args, " ") + "\n"
			if run.ExitCode != 0 || run.Stdout != line+"service output\n" || run.Stderr != "service error\n" {
				t.Fatalf("command output: %+v", run)
			}
		})
	}
}

func TestWSLogsSelectsServicesAndFollows(t *testing.T) {
	for _, service := range []string{"", "consul", "traefik", "dashboard"} {
		for _, follow := range []string{"", "-f", "--follow"} {
			yard := commandtest.New(t)
			folder := t.TempDir()
			args := []string{"ws", "--dir", folder, "logs"}
			want := []string{"compose", "--project-directory", folder, "logs"}
			if service != "" {
				args = append(args, service)
			}
			if follow != "" {
				args = append(args, follow)
				want = append(want, "-f")
			}
			if service != "" {
				want = append(want, service)
			}
			run := yard.Run(args...)
			if run.ExitCode != 0 || len(yard.Process.Commands) != 1 || !reflect.DeepEqual(yard.Process.Commands[0].Args, want) {
				t.Fatalf("logs %q %q: %+v, commands %+v", service, follow, run, yard.Process.Commands)
			}
		}
	}
}

func TestWSLogsRejectsUnknownServicesBeforeRunning(t *testing.T) {
	for _, args := range [][]string{{"ws", "logs", "other"}, {"ws", "logs", "consul", "traefik"}} {
		yard := commandtest.New(t)
		run := yard.Run(args...)
		if run.ExitCode != 1 || len(yard.Process.Commands) != 0 || run.Stdout != "" {
			t.Fatalf("invalid logs: %+v, commands %+v", run, yard.Process.Commands)
		}
		for _, name := range []string{"consul", "traefik", "dashboard"} {
			if !strings.Contains(run.Stderr, name) {
				t.Fatalf("missing valid service %s: %+v", name, run)
			}
		}
	}
}

func TestWSLifecycleReturnsComposeExitCode(t *testing.T) {
	for _, subcommand := range []string{"start", "stop", "restart", "logs"} {
		yard := commandtest.New(t)
		yard.Process.RunCommand = func(string, []string, string, io.Writer, io.Writer) (int, error) {
			return 37, nil
		}
		run := yard.Run("ws", subcommand, "--dir", t.TempDir())
		if run.ExitCode != 37 || run.Stderr != "" {
			t.Fatalf("%s exit code: %+v", subcommand, run)
		}
	}
}

func TestWSLifecycleReportsMissingDocker(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.RunCommand = func(string, []string, string, io.Writer, io.Writer) (int, error) {
		return 0, &exec.Error{Name: "docker", Err: exec.ErrNotFound}
	}
	run := yard.Run("ws", "start", "--dir", t.TempDir())
	if run.ExitCode != 1 || !strings.Contains(run.Stderr, "docker is not on the PATH") {
		t.Fatalf("missing docker: %+v", run)
	}
}
