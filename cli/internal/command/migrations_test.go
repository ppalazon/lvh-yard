package command_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

const oldDataFolderNote = "compose.yaml still keeps the Consul data in ./consul-data"

// oldFolder is a Workstation Folder from before the layout file, with Consul data in ./consul-data.
func oldFolder(t *testing.T) string {
	t.Helper()
	folder := t.TempDir()
	writeUpgradeFile(t, folder, "compose.yaml", "services:\n  dashboard:\n    image: ghcr.io/ppalazon/lvh-yard-dashboard:latest\n  consul:\n    image: hashicorp/consul:2.1.0\n    volumes:\n      - ./consul-data:/consul/data\n  traefik:\n    image: traefik:v3.7.16\n")
	writeUpgradeFile(t, folder, ".env", "YARD_WORKSTATION=mine\n")
	return folder
}

func TestInitRecordsTheLayoutSoNoMigrationRuns(t *testing.T) {
	folder := filepath.Join(t.TempDir(), "yard")
	yard := commandtest.New(t)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	if got := strings.TrimSpace(readInitFile(t, folder, ".lvh-yard-layout")); got != "3" {
		t.Fatalf("layout = %q", got)
	}
	run := yard.Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || strings.Contains(run.Stdout, "Note:") {
		t.Fatalf("upgrade: %+v", run)
	}
}

func TestUpgradeRunsEachMigrationOnce(t *testing.T) {
	folder := oldFolder(t)
	yard := commandtest.New(t)
	run := yard.Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || !strings.Contains(run.Stdout, oldDataFolderNote) {
		t.Fatalf("first upgrade: %+v", run)
	}
	if got := strings.TrimSpace(readInitFile(t, folder, ".lvh-yard-layout")); got != "3" {
		t.Fatalf("layout after the upgrade = %q", got)
	}
	if !strings.Contains(readInitFile(t, folder, "compose.yaml"), "./consul-data:/consul/data") {
		t.Fatal("the migration changed compose.yaml, which is the user's file")
	}
	run = yard.Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || strings.Contains(run.Stdout, oldDataFolderNote) {
		t.Fatalf("second upgrade: %+v", run)
	}
}

func TestMigrationStaysQuietForAFolderThatItDoesNotConcern(t *testing.T) {
	folder := t.TempDir()
	writeUpgradeFile(t, folder, "compose.yaml", "services:\n  dashboard:\n    image: ghcr.io/ppalazon/lvh-yard-dashboard:latest\n  consul:\n    image: hashicorp/consul:2.1.0\n    volumes:\n      - consul-data:/consul/data\n  traefik:\n    image: traefik:v3.7.16\n")
	run := commandtest.New(t).Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || strings.Contains(run.Stdout, "Note:") {
		t.Fatalf("upgrade: %+v", run)
	}
	if got := strings.TrimSpace(readInitFile(t, folder, ".lvh-yard-layout")); got != "3" {
		t.Fatalf("layout = %q", got)
	}
}

func TestUpgradeLeavesAFolderOfANewerReleaseAlone(t *testing.T) {
	folder := oldFolder(t)
	writeUpgradeFile(t, folder, ".lvh-yard-layout", "99\n")
	run := commandtest.New(t).Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || !strings.Contains(run.Stdout, "newer lvh-yard") || strings.Contains(run.Stdout, oldDataFolderNote) {
		t.Fatalf("upgrade: %+v", run)
	}
	if got := strings.TrimSpace(readInitFile(t, folder, ".lvh-yard-layout")); got != "99" {
		t.Fatalf("layout = %q, the upgrade must not lower it", got)
	}
}

func TestUpgradeRefusesAnUnreadableLayout(t *testing.T) {
	folder := oldFolder(t)
	writeUpgradeFile(t, folder, ".lvh-yard-layout", "soon\n")
	run := commandtest.New(t).Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 1 || !strings.Contains(run.Stderr, ".lvh-yard-layout") {
		t.Fatalf("upgrade: %+v", run)
	}
	if _, err := os.Stat(filepath.Join(folder, ".lvh-yard-layout")); err != nil {
		t.Fatal(err)
	}
}
