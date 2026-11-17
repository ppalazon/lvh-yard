//go:build !windows

package command_test

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestInitFindsDockerSocketGroup(t *testing.T) {
	socket := filepath.Join(t.TempDir(), "docker.sock")
	if err := os.WriteFile(socket, nil, 0600); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(socket)
	if err != nil {
		t.Fatal(err)
	}
	stat := info.Sys().(*syscall.Stat_t)
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me", "--docker-socket", socket)
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("Docker group: %+v", run)
	}
	want := "YARD_DOCKER_GID=" + strconv.FormatUint(uint64(stat.Gid), 10)
	if !strings.Contains(readInitFile(t, folder, ".env"), "\n"+want+"\n") {
		t.Fatalf("missing %s", want)
	}
}

// A refused init must not touch a folder that already exists, not even its modes.
func TestInitRefusalLeavesExistingModes(t *testing.T) {
	folder := t.TempDir()
	if err := os.Chmod(folder, 0755); err != nil {
		t.Fatal(err)
	}
	existing := filepath.Join(folder, "compose.yaml")
	if err := os.WriteFile(existing, []byte("mine\n"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(existing, 0644); err != nil {
		t.Fatal(err)
	}
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me")
	if run.ExitCode != 1 || !strings.Contains(run.Stderr, "--force") {
		t.Fatalf("overwrite refused: %+v", run)
	}
	for path, want := range map[string]os.FileMode{folder: 0755, existing: 0644} {
		info, err := os.Stat(path)
		if err != nil || info.Mode().Perm() != want {
			t.Errorf("%s: mode %v, want %v (%v)", path, info.Mode().Perm(), want, err)
		}
	}
}
