package command_test

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"go.yaml.in/yaml/v3"
)

func TestInitKeepsDashboardEnvironmentExplicit(t *testing.T) {
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me")
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	var compose struct {
		Services map[string]struct {
			Environment map[string]string `yaml:"environment"`
			EnvFile     any               `yaml:"env_file"`
		} `yaml:"services"`
	}
	if err := yaml.Unmarshal([]byte(readInitFile(t, folder, "compose.yaml")), &compose); err != nil {
		t.Fatal(err)
	}
	dashboard := compose.Services["dashboard"]
	if dashboard.EnvFile != nil || len(dashboard.Environment) == 0 {
		t.Fatal("dashboard must use an explicit environment list")
	}
	if _, exposed := dashboard.Environment["YARD_GOSSIP_KEY"]; exposed {
		t.Fatal("dashboard receives the Gossip Key")
	}
}

func TestWSSetLeavesDashboardSettingsUnchanged(t *testing.T) {
	folder := t.TempDir()
	yard := commandtest.New(t)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	const settings = "# my settings\nlvh-yard:\n  events:\n    debounce-window: 500ms\n"
	if err := os.WriteFile(filepath.Join(folder, "lvh-yard.yaml"), []byte(settings), 0600); err != nil {
		t.Fatal(err)
	}
	if run := yard.Run("ws", "set", "--dir", folder, "base-domain", "example.test"); run.ExitCode != 0 {
		t.Fatalf("set: %+v", run)
	}
	if got := readInitFile(t, folder, "lvh-yard.yaml"); got != settings {
		t.Fatalf("set changed dashboard settings: %q", got)
	}
}
