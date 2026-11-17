package command_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWSInitRefusesEscapingGeneratedDirectory(t *testing.T) {
	commandtest.SkipOnWindows(t, "a symbolic link needs a privilege that an ordinary Windows user lacks; TestWindowsWorkstationRejectsJunctionEscape covers escapes on Windows")
	folder := t.TempDir()
	outside := t.TempDir()
	if err := os.Symlink(outside, filepath.Join(folder, "consul.d")); err != nil {
		t.Fatal(err)
	}

	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me", "--force")
	if run.ExitCode == 0 {
		t.Fatal("initialization accepted a generated directory outside the Workstation Folder")
	}
	if _, err := os.Stat(filepath.Join(outside, "00-lvh-yard.hcl")); !os.IsNotExist(err) {
		t.Fatalf("initialization wrote outside the Workstation Folder: %v", err)
	}
}

func TestWSTLSRefusesEscapingCA(t *testing.T) {
	commandtest.SkipOnWindows(t, "a symbolic link needs a privilege that an ordinary Windows user lacks; TestWindowsWorkstationRejectsJunctionEscape covers escapes on Windows")
	yard := commandtest.New(t)
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if run := yard.Run(args...); run.ExitCode != 0 {
			t.Fatal(run)
		}
	}
	outside := filepath.Join(t.TempDir(), "ca")
	if err := os.Rename(filepath.Join(folder, "ca"), outside); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(outside, filepath.Join(folder, "ca")); err != nil {
		t.Fatal(err)
	}

	run := yard.Run("ws", "tls", "ca", "issue", "service", "grafana", "--dir", folder)
	if run.ExitCode == 0 {
		t.Fatal("signing read a CA outside the Workstation Folder")
	}
	if _, err := os.Stat(filepath.Join(outside, "service")); !os.IsNotExist(err) {
		t.Fatalf("signing wrote outside the Workstation Folder: %v", err)
	}
}

func TestWSCommandsRefuseEscapingSettingsAndCompose(t *testing.T) {
	commandtest.SkipOnWindows(t, "a symbolic link needs a privilege that an ordinary Windows user lacks; TestWindowsWorkstationRejectsJunctionEscape covers escapes on Windows")
	for _, tc := range []struct {
		name string
		file string
		args []string
	}{
		{"show settings", ".env", []string{"show"}},
		{"change settings", ".env", []string{"set", "dashboard-port", "7790"}},
		{"upgrade images", "compose.yaml", []string{"compose", "upgrade"}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			yard, folder := newSecurityWorkstation(t)
			path := filepath.Join(folder, tc.file)
			before, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			outside := filepath.Join(t.TempDir(), tc.file)
			if err := os.Rename(path, outside); err != nil {
				t.Fatal(err)
			}
			if err := os.Chmod(outside, 0644); err != nil {
				t.Fatal(err)
			}
			if err := os.Symlink(outside, path); err != nil {
				t.Fatal(err)
			}

			args := append([]string{"ws", "--dir", folder}, tc.args...)
			if run := yard.Run(args...); run.ExitCode == 0 {
				t.Fatalf("%s accepted an escaping file", tc.name)
			}
			after, err := os.ReadFile(outside)
			if err != nil || string(after) != string(before) {
				t.Fatalf("%s changed the outside file: %v", tc.name, err)
			}
			info, err := os.Stat(outside)
			if err != nil {
				t.Fatal(err)
			}
			if info.Mode().Perm() != 0644 {
				t.Fatalf("%s changed outside permissions to %v", tc.name, info.Mode().Perm())
			}
		})
	}
}

func TestWSInitRefusesEscapingOutputFile(t *testing.T) {
	commandtest.SkipOnWindows(t, "a symbolic link needs a privilege that an ordinary Windows user lacks; TestWindowsWorkstationRejectsJunctionEscape covers escapes on Windows")
	folder := t.TempDir()
	outside := filepath.Join(t.TempDir(), "settings")
	if err := os.WriteFile(outside, []byte("keep me"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(outside, filepath.Join(folder, ".env")); err != nil {
		t.Fatal(err)
	}

	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me", "--force")
	if run.ExitCode == 0 {
		t.Fatal("initialization accepted an escaping output file")
	}
	if data, err := os.ReadFile(outside); err != nil || string(data) != "keep me" {
		t.Fatalf("initialization changed an outside file: %v", err)
	}
	info, err := os.Stat(outside)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0644 {
		t.Fatalf("initialization changed outside permissions to %v", info.Mode().Perm())
	}
}

func TestWSSettingsAcceptAnInRootRelativeLink(t *testing.T) {
	commandtest.SkipOnWindows(t, "a symbolic link needs a privilege that an ordinary Windows user lacks; TestWindowsWorkstationRejectsJunctionEscape covers escapes on Windows")
	yard, folder := newSecurityWorkstation(t)
	if err := os.Mkdir(filepath.Join(folder, "settings"), 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.Rename(filepath.Join(folder, ".env"), filepath.Join(folder, "settings", "env")); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(filepath.Join("settings", "env"), filepath.Join(folder, ".env")); err != nil {
		t.Fatal(err)
	}

	if run := yard.Run("ws", "set", "--dir", folder, "dashboard-port", "7790"); run.ExitCode != 0 {
		t.Fatal(run)
	}
	if text := readInitFile(t, folder, "settings/env"); !strings.Contains(text, "YARD_DASHBOARD_PORT=7790") {
		t.Fatal("the setting did not reach the in-root link target")
	}
}

func TestWSTLSRefusesEscapingPrivateOutputs(t *testing.T) {
	commandtest.SkipOnWindows(t, "a symbolic link needs a privilege that an ordinary Windows user lacks; TestWindowsWorkstationRejectsJunctionEscape covers escapes on Windows")
	for _, tc := range []struct {
		name string
		file string
		args []string
	}{
		{"CA replacement", "ca/ca.key", []string{"ca", "create", "--force"}},
		{"Client Certificate", "ca/client/alice.p12", []string{"ca", "issue", "user", "alice", "--password", "fixture"}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			yard, folder := newSecurityWorkstation(t)
			if run := yard.Run("ws", "tls", "ca", "create", "--dir", folder); run.ExitCode != 0 {
				t.Fatal(run)
			}
			path := filepath.Join(folder, tc.file)
			if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
				t.Fatal(err)
			}
			if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
				t.Fatal(err)
			}
			outside := filepath.Join(t.TempDir(), "private")
			if err := os.WriteFile(outside, []byte("keep me"), 0644); err != nil {
				t.Fatal(err)
			}
			if err := os.Symlink(outside, path); err != nil {
				t.Fatal(err)
			}

			args := append([]string{"ws", "tls", "--dir", folder}, tc.args...)
			if run := yard.Run(args...); run.ExitCode == 0 {
				t.Fatalf("%s accepted an escaping output file", tc.name)
			}
			if data, err := os.ReadFile(outside); err != nil || string(data) != "keep me" {
				t.Fatalf("%s changed the outside file: %v", tc.name, err)
			}
		})
	}
}

func TestWSTLSClientCreateRefusesEscapingTraefikDirectory(t *testing.T) {
	commandtest.SkipOnWindows(t, "a symbolic link needs a privilege that an ordinary Windows user lacks; TestWindowsWorkstationRejectsJunctionEscape covers escapes on Windows")
	yard, folder := newSecurityWorkstation(t)
	for _, args := range [][]string{
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if run := yard.Run(args...); run.ExitCode != 0 {
			t.Fatal(run)
		}
	}
	path := filepath.Join(folder, "tls", "traefik")
	if err := os.Remove(path); err != nil {
		t.Fatal(err)
	}
	outside := t.TempDir()
	if err := os.Symlink(outside, path); err != nil {
		t.Fatal(err)
	}

	if run := yard.Run("ws", "tls", "client", "create", "--dir", folder); run.ExitCode == 0 {
		t.Fatal("installation accepted an escaping role directory")
	}
	for _, name := range []string{"client-ca.crt", "https.crt", "https.key"} {
		if _, err := os.Stat(filepath.Join(outside, name)); !os.IsNotExist(err) {
			t.Fatalf("installation wrote %s outside the Workstation Folder: %v", name, err)
		}
	}
}

func newSecurityWorkstation(t *testing.T) (*commandtest.Harness, string) {
	t.Helper()
	yard := commandtest.New(t)
	folder := t.TempDir()
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); run.ExitCode != 0 {
		t.Fatal(run)
	}
	return yard, folder
}
