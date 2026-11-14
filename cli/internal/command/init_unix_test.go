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
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--docker-socket", socket)
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("Docker group: %+v", run)
	}
	want := "YARD_DOCKER_GID=" + strconv.FormatUint(uint64(stat.Gid), 10)
	if !strings.Contains(readInitFile(t, folder, ".env"), "\n"+want+"\n") {
		t.Fatalf("missing %s", want)
	}
}
