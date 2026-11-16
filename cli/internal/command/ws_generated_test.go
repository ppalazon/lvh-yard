package command_test

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWSGeneratedFiles(t *testing.T) {
	folder := t.TempDir()
	yard := commandtest.New(t)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	generated := readInitFile(t, folder, "consul.d/00-lvh-yard.hcl")
	for _, name := range []string{"consul.d", "traefik", "traefik/dynamic"} {
		info, err := os.Stat(filepath.Join(folder, name))
		if err != nil || !info.IsDir() || (commandtest.UnixModes && info.Mode().Perm() != 0700) {
			t.Fatalf("private configuration folder %s: %v, %v", name, info, err)
		}
	}
	if !strings.HasPrefix(generated, "# Generated, do not edit.") || !strings.Contains(generated, "server = true") {
		t.Fatalf("fixed Consul options: %s", generated)
	}
	entries, err := os.ReadDir(filepath.Join(folder, "traefik/dynamic"))
	if err != nil || len(entries) != 0 {
		t.Fatalf("dynamic folder: %v, %v", entries, err)
	}
	for _, name := range []string{"consul.d/90-user.hcl", "traefik/dynamic/90-user.yaml"} {
		if err := os.WriteFile(filepath.Join(folder, name), []byte("# user\r\n\x00"), 0600); err != nil {
			t.Fatal(err)
		}
	}
	if err := os.WriteFile(filepath.Join(folder, "consul.d/00-lvh-yard.hcl"), []byte("hand edit\n"), 0600); err != nil {
		t.Fatal(err)
	}
	compose := readInitFile(t, folder, "compose.yaml")
	if run := yard.Run("ws", "compose", "upgrade", "--dir", folder); run.ExitCode != 0 {
		t.Fatalf("upgrade: %+v", run)
	}
	// Windows mounts the folder as a source that its script copies, because NTFS does not tell Traefik about changes.
	dynamicMount := "./traefik/dynamic:/etc/traefik/dynamic:ro"
	if runtime.GOOS == "windows" {
		dynamicMount = "./traefik/dynamic:/source/dynamic:ro"
	}
	for _, option := range []string{
		"-config-dir=/consul/config",
		"./consul.d:/consul/config:ro",
		dynamicMount,
		"--providers.file.directory=/etc/traefik/dynamic",
		"--providers.file.watch=true",
	} {
		if !strings.Contains(compose, option) {
			t.Fatalf("missing configuration contract %s", option)
		}
	}
	if got := readInitFile(t, folder, "consul.d/00-lvh-yard.hcl"); got != generated {
		t.Fatalf("generated file was not restored: %s", got)
	}
	if got := readInitFile(t, folder, "compose.yaml"); got != compose {
		t.Fatal("current compose changed")
	}
	for _, name := range []string{"consul.d/90-user.hcl", "traefik/dynamic/90-user.yaml"} {
		if got := readInitFile(t, folder, name); got != "# user\r\n\x00" {
			t.Fatalf("user file changed: %s", name)
		}
	}
}
