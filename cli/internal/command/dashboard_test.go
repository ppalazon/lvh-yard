package command_test

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"github.com/spf13/cobra"
)

func dashboardProbe(options *command.Options) *cobra.Command {
	return &cobra.Command{
		Use: "address",
		RunE: func(cmd *cobra.Command, args []string) error {
			_, err := fmt.Fprintln(cmd.OutOrStdout(), options.DashboardAddress())
			return err
		},
	}
}

func writeEnv(t *testing.T, folder, text string) {
	t.Helper()
	if err := os.MkdirAll(folder, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(folder, ".env"), []byte(text), 0o600); err != nil {
		t.Fatal(err)
	}
}

func assertAddress(t *testing.T, yard *commandtest.Harness, want string, args ...string) {
	t.Helper()
	yard.Groups = []func(*command.Options) *cobra.Command{dashboardProbe}
	run := yard.Run(append(args, "address")...)
	if run.ExitCode != 0 || run.Stderr != "" || run.Stdout != want+"\n" {
		t.Fatalf("dashboard address: %+v, want %s", run, want)
	}
}

func TestDashboardFlagWinsOverEnvironmentAndEnvFile(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	writeEnv(t, folder, "YARD_DASHBOARD_PORT=9001\n")
	yard.Process.Env["YARD_CONFIG_DIR"] = folder
	yard.Process.Env["YARD_DASHBOARD_URL"] = "http://environment:9002"
	assertAddress(t, yard, "http://flag:9003", "--dashboard", "http://flag:9003")
}

func TestDashboardEnvironmentWinsOverEnvFile(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	writeEnv(t, folder, "YARD_DASHBOARD_PORT=9001\n")
	yard.Process.Env["YARD_CONFIG_DIR"] = folder
	yard.Process.Env["YARD_DASHBOARD_URL"] = "http://environment:9002"
	assertAddress(t, yard, "http://environment:9002")
}

func TestDashboardUsesWorkstationFolder(t *testing.T) {
	for _, source := range []string{"YARD_CONFIG_DIR", "XDG_CONFIG_HOME", "HOME"} {
		t.Run(source, func(t *testing.T) {
			yard := commandtest.New(t)
			home := yard.Process.Env["HOME"]
			xdg := t.TempDir()
			override := t.TempDir()
			writeEnv(t, filepath.Join(home, ".config", "lvh-yard"), "YARD_DASHBOARD_PORT=9001\n")
			writeEnv(t, filepath.Join(xdg, "lvh-yard"), "YARD_DASHBOARD_PORT=9002\n")
			writeEnv(t, override, "YARD_DASHBOARD_BIND_ADDRESS=10.1.2.3\nYARD_DASHBOARD_PORT=9003\n")
			switch source {
			case "YARD_CONFIG_DIR":
				yard.Process.Env["YARD_CONFIG_DIR"] = override
				yard.Process.Env["XDG_CONFIG_HOME"] = xdg
				assertAddress(t, yard, "http://10.1.2.3:9003")
			case "XDG_CONFIG_HOME":
				yard.Process.Env["XDG_CONFIG_HOME"] = xdg
				assertAddress(t, yard, "http://127.0.0.1:9002")
			case "HOME":
				assertAddress(t, yard, "http://127.0.0.1:9001")
			}
		})
	}
}

func TestWildcardBindUsesLoopback(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	writeEnv(t, folder, "YARD_DASHBOARD_BIND_ADDRESS=0.0.0.0\nYARD_DASHBOARD_PORT=9001\n")
	yard.Process.Env["YARD_CONFIG_DIR"] = folder
	assertAddress(t, yard, "http://127.0.0.1:9001")
}

func TestMissingOrUnreadableEnvUsesLoopback(t *testing.T) {
	for _, unreadable := range []bool{false, true} {
		yard := commandtest.New(t)
		folder := t.TempDir()
		yard.Process.Env["YARD_CONFIG_DIR"] = folder
		if unreadable {
			if err := os.Mkdir(filepath.Join(folder, ".env"), 0o700); err != nil {
				t.Fatal(err)
			}
		}
		assertAddress(t, yard, "http://127.0.0.1:7780")
	}
}

func TestEnvSettingsUseComposeDefaults(t *testing.T) {
	for _, test := range []struct {
		name string
		text string
		want string
	}{
		{"missing settings", "YARD_WORKSTATION=ws-a\n", "http://127.0.0.1:7780"},
		{"blank settings", "YARD_DASHBOARD_BIND_ADDRESS= \nYARD_DASHBOARD_PORT=\n", "http://127.0.0.1:7780"},
		{"commented settings", "#YARD_DASHBOARD_BIND_ADDRESS=10.0.0.1\n#YARD_DASHBOARD_PORT=9000\n", "http://127.0.0.1:7780"},
		{"default bind", "YARD_DASHBOARD_PORT=9001\n", "http://127.0.0.1:9001"},
		{"default port", "YARD_DASHBOARD_BIND_ADDRESS=10.1.2.3\n", "http://10.1.2.3:7780"},
		{"first active setting", "YARD_DASHBOARD_PORT=9001\nYARD_DASHBOARD_PORT=9002\n", "http://127.0.0.1:9001"},
		{"Windows line endings", "YARD_DASHBOARD_PORT=9001\r\n", "http://127.0.0.1:9001"},
	} {
		t.Run(test.name, func(t *testing.T) {
			yard := commandtest.New(t)
			folder := t.TempDir()
			writeEnv(t, folder, test.text)
			yard.Process.Env["YARD_CONFIG_DIR"] = folder
			assertAddress(t, yard, test.want)
		})
	}
}

func TestBlankEnvironmentSettingsFallThrough(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.Env["YARD_DASHBOARD_URL"] = " \t"
	yard.Process.Env["YARD_CONFIG_DIR"] = " \t"
	yard.Process.Env["XDG_CONFIG_HOME"] = " \t"
	writeEnv(t, filepath.Join(yard.Process.Env["HOME"], ".config", "lvh-yard"), "YARD_DASHBOARD_PORT=9001\n")
	assertAddress(t, yard, "http://127.0.0.1:9001")
}
