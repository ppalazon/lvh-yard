package command_test

import (
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWindowsWorkstationUsesLocalAppData(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.Env["LOCALAPPDATA"] = t.TempDir()
	yard.Process.Env["XDG_CONFIG_HOME"] = "ignored"
	options := command.Options{Process: yard.Process}
	if got, want := options.WorkstationFolder(), filepath.Join(yard.Process.Env["LOCALAPPDATA"], "lvh-yard", "workstation"); got != want {
		t.Fatalf("folder = %q, want %q", got, want)
	}
	yard.Process.Env["YARD_CONFIG_DIR"] = filepath.Join(t.TempDir(), "O'Brien 日本")
	if got := options.WorkstationFolder(); got != yard.Process.Env["YARD_CONFIG_DIR"] {
		t.Fatalf("explicit folder = %q", got)
	}
}

func TestWindowsWorkstationRequiresUnambiguousLANIdentity(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.LANIP = ""
	folder := filepath.Join(t.TempDir(), "O'Brien café 日本")
	run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native")
	if run.ExitCode == 0 || !strings.Contains(run.Stderr, "--address") {
		t.Fatalf("missing address: %+v", run)
	}
	run = yard.Run("ws", "init", "--dir", folder, "--workstation", "native", "--address", "192.168.122.117")
	if run.ExitCode != 0 {
		t.Fatalf("explicit address: %+v", run)
	}
	text, err := os.ReadFile(filepath.Join(folder, ".env"))
	if err != nil || !strings.Contains(string(text), "YARD_BIND_ADDRESS=192.168.122.117") {
		t.Fatalf("advertisement: %s, %v", text, err)
	}
	for _, name := range []string{"dashboard-bind-address", "traefik-bind-address"} {
		run = yard.Run("ws", "set", "--dir", folder, name, "192.168.122.117")
		if run.ExitCode == 0 {
			t.Fatalf("native listener binding was accepted: %+v", run)
		}
	}
}

func TestWindowsWorkstationRejectsInvalidOutputNames(t *testing.T) {
	yard := commandtest.New(t)
	folder := filepath.Join(t.TempDir(), "private")
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native", "--address", "192.168.122.117"); run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	if run := yard.Run("ws", "tls", "ca", "create", "--dir", folder); run.ExitCode != 0 {
		t.Fatalf("CA: %+v", run)
	}
	for _, name := range []string{"CON", "nul.txt", "LPT1", "COM¹", "trailing.", "trailing ", "alternate:stream", "star*", "question?"} {
		run := yard.Run("ws", "tls", "ca", "issue", "user", "--dir", folder, name, "--password", "test-password")
		if run.ExitCode == 0 || !strings.Contains(run.Stderr, "filename") {
			t.Errorf("unsafe output %q: %+v", name, run)
		}
	}
}

func TestWindowsWorkstationRejectsJunctionEscape(t *testing.T) {
	yard := commandtest.New(t)
	folder, outside := t.TempDir(), t.TempDir()
	run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native", "--address", "192.168.122.117")
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	secret := filepath.Join(outside, "unchanged")
	if err := os.WriteFile(secret, []byte("outside"), 0600); err != nil {
		t.Fatal(err)
	}
	junction := filepath.Join(folder, "escape")
	if out, err := exec.Command("cmd.exe", "/c", "mklink", "/J", junction, outside).CombinedOutput(); err != nil {
		t.Fatalf("junction: %s: %v", out, err)
	}
	t.Cleanup(func() { _ = os.Remove(junction) })
	run = yard.Run("ws", "set", "--dir", folder, "base-domain", "localhost")
	if run.ExitCode == 0 {
		t.Fatalf("junction was accepted: %+v", run)
	}
	if text, err := os.ReadFile(secret); err != nil || string(text) != "outside" {
		t.Fatalf("outside changed: %q, %v", text, err)
	}
}

func TestWindowsWorkstationCreatesDefaultBesideExecutables(t *testing.T) {
	yard := commandtest.New(t)
	local := t.TempDir()
	yard.Process.Env["LOCALAPPDATA"] = local
	bin := filepath.Join(local, "lvh-yard", "bin")
	if err := os.MkdirAll(bin, 0700); err != nil {
		t.Fatal(err)
	}
	executable := filepath.Join(bin, "lvh-yard.exe")
	if err := os.WriteFile(executable, []byte("installed executable"), 0600); err != nil {
		t.Fatal(err)
	}
	run := yard.Run("ws", "init", "--workstation", "native", "--address", "192.168.122.117")
	if run.ExitCode != 0 {
		t.Fatalf("default init: %+v", run)
	}
	if _, err := os.Stat(filepath.Join(local, "lvh-yard", "workstation", "compose.yaml")); err != nil {
		t.Fatal(err)
	}
	if text, err := os.ReadFile(executable); err != nil || string(text) != "installed executable" {
		t.Fatalf("installed executable changed: %q, %v", text, err)
	}
}

func TestWindowsWorkstationRejectsSharedAndSyncedFolders(t *testing.T) {
	for _, folder := range []string{`\\server\share\private`, filepath.Join(t.TempDir(), "OneDrive", "private")} {
		yard := commandtest.New(t)
		run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native", "--address", "192.168.122.117")
		if run.ExitCode == 0 || !strings.Contains(run.Stderr, "NTFS") {
			t.Fatalf("unsupported storage %q: %+v", folder, run)
		}
	}
}

func TestWindowsInitNamesTheComposeProjectOfEachFolder(t *testing.T) {
	yard := commandtest.New(t)
	root := t.TempDir()
	names := map[string]string{}
	for _, parent := range []string{"a", "b"} {
		folder := filepath.Join(root, parent, "Work Station 日本")
		if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native", "--address", "192.168.122.117"); run.ExitCode != 0 {
			t.Fatalf("init: %+v", run)
		}
		text, err := os.ReadFile(filepath.Join(folder, ".env"))
		if err != nil {
			t.Fatal(err)
		}
		match := regexp.MustCompile(`(?m)^COMPOSE_PROJECT_NAME=(work-station-[0-9a-f]{8})$`).FindStringSubmatch(string(text))
		if match == nil {
			t.Fatalf("project name: %s", text)
		}
		names[parent] = match[1]
		// The Instance host follows the LAN address until a person sets it.
		if !strings.Contains(string(text), "\nYARD_INSTANCE_HOST=\n") || !strings.Contains(string(text), "\nYARD_BIND_ADDRESS=192.168.122.117\n") {
			t.Fatalf("addresses: %s", text)
		}
	}
	if names["a"] == names["b"] {
		t.Fatalf("two folders share the project %s", names["a"])
	}
}

func TestWindowsSetsTheInstanceHostApartFromTheAdvertisedAddress(t *testing.T) {
	yard := commandtest.New(t)
	folder := filepath.Join(t.TempDir(), "private")
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native", "--address", "192.168.122.117"); run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	if run := yard.Run("ws", "set", "--dir", folder, "instance-host", "192.168.122.200"); run.ExitCode != 0 {
		t.Fatalf("set instance-host: %+v", run)
	}
	text, err := os.ReadFile(filepath.Join(folder, ".env"))
	if err != nil || !strings.Contains(string(text), "\nYARD_INSTANCE_HOST=192.168.122.200\n") || !strings.Contains(string(text), "\nYARD_BIND_ADDRESS=192.168.122.117\n") {
		t.Fatalf("settings: %s, %v", text, err)
	}
}

func TestWindowsRefusesAnInstanceHostThatNoConnectionCanReach(t *testing.T) {
	yard := commandtest.New(t)
	folder := filepath.Join(t.TempDir(), "private")
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native", "--address", "192.168.122.117"); run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	before, err := os.ReadFile(filepath.Join(folder, ".env"))
	if err != nil {
		t.Fatal(err)
	}
	for _, value := range []string{"0.0.0.0", "224.0.0.1", "255.255.255.255"} {
		if run := yard.Run("ws", "set", "--dir", folder, "instance-host", value); run.ExitCode == 0 || !strings.Contains(run.Stdout+run.Stderr, "instance-host") {
			t.Fatalf("set instance-host %s: %+v, want a refusal", value, run)
		}
	}
	if after, err := os.ReadFile(filepath.Join(folder, ".env")); err != nil || string(after) != string(before) {
		t.Fatalf(".env changed after refused values: %v", err)
	}
	// A process on loopback is a normal Instance for Local Scope.
	if run := yard.Run("ws", "set", "--dir", folder, "instance-host", "127.0.0.1"); run.ExitCode != 0 {
		t.Fatalf("set instance-host 127.0.0.1: %+v", run)
	}
}
