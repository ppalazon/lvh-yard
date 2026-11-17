package command_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func newCAFolder(t *testing.T) (*commandtest.Harness, string) {
	t.Helper()
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	return yard, folder
}

func caKeyFile(t *testing.T, folder string) string {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(folder, "ca", "ca.key"))
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func TestWSTLSCACreateNamesTheCAAfterTheWorkstationAndLeavesTheKeyPlain(t *testing.T) {
	yard, folder := newCAFolder(t)
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if name := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt")).Subject.CommonName; name != "LVH Yard CA - ws-a" {
		t.Fatalf("name = %q", name)
	}
	if !strings.Contains(caKeyFile(t, folder), "BEGIN EC "+"PRIVATE KEY") {
		t.Fatal("the key has a password that nobody gave")
	}
}

func TestWSTLSCACreateAsksForNameAndPassword(t *testing.T) {
	yard, folder := newCAFolder(t)
	yard.Process.Terminal = true
	// The name, the validity, and the password twice.
	yard.Process.Stdin = "Acme Yard CA\n\nhunter2\nhunter2\n"
	r := yard.Run("ws", "tls", "ca", "create", "--dir", folder)
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	if yard.Process.SecretReads != 2 {
		t.Fatalf("the password was asked %d times without hiding it, want 2", yard.Process.SecretReads)
	}
	for _, want := range []string{"CA common name [LVH Yard CA - ws-a]", "Password of the CA key (empty for none)", "Repeat the password"} {
		if !strings.Contains(r.Stdout, want) {
			t.Fatalf("no question %q:\n%s", want, r.Stdout)
		}
	}
	if strings.Contains(r.Stdout+r.Stderr, "hunter2 ") || !strings.Contains(caKeyFile(t, folder), "BEGIN ENCRYPTED "+"PRIVATE KEY") {
		t.Fatalf("the key is not encrypted:\n%s", r.Stdout)
	}
	if name := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt")).Subject.CommonName; name != "Acme Yard CA" {
		t.Fatalf("name = %q", name)
	}

	// Empty answers take the default name and no password, which is asked once.
	yard.Process.SecretReads = 0
	yard.Process.Stdin = "\n\n\n"
	if r := yard.Run("ws", "tls", "ca", "create", "--force", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if name := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt")).Subject.CommonName; name != "LVH Yard CA - ws-a" || !strings.Contains(caKeyFile(t, folder), "BEGIN EC "+"PRIVATE KEY") {
		t.Fatalf("empty answers did not take the defaults: %q", name)
	}
	if yard.Process.SecretReads != 1 {
		t.Fatalf("an empty password was confirmed: %d reads", yard.Process.SecretReads)
	}
}

func TestWSTLSCACreateAsksAgainAfterDifferentPasswords(t *testing.T) {
	yard, folder := newCAFolder(t)
	yard.Process.Terminal = true
	// Default name and validity. The first pair differs. The second keeps its spaces.
	yard.Process.Stdin = "\n\none\ntwo\n  spaced pass \n  spaced pass \n"
	r := yard.Run("ws", "tls", "ca", "create", "--dir", folder)
	if r.ExitCode != 0 || !strings.Contains(r.Stdout, "The passwords differ") {
		t.Fatalf("%+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder, "--ca-key-password", "  spaced pass "); r.ExitCode != 0 {
		t.Fatalf("the spaces of the password were lost: %+v", r)
	}
}

func TestWSTLSCACreateRefusesBadNames(t *testing.T) {
	yard, folder := newCAFolder(t)
	for _, name := range []string{"", "   ", strings.Repeat("x", 65), "two\nlines"} {
		if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder, "--name", name); r.ExitCode == 0 {
			t.Fatalf("name %q was accepted", name)
		}
	}
	if _, err := os.Stat(filepath.Join(folder, "ca", "ca.key")); err == nil {
		t.Fatal("a refused name left a CA")
	}
}

func TestSigningNeedsThePasswordOfTheCAKey(t *testing.T) {
	yard, folder := newCAFolder(t)
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder, "--ca-key-password", "hunter2"); r.ExitCode != 0 || strings.Contains(r.Stdout+r.Stderr, "hunter2") {
		t.Fatalf("create: %+v", r)
	}
	issue := []string{"ws", "tls", "ca", "issue", "traefik", "--dir", folder}
	if r := yard.Run(issue...); r.ExitCode == 0 || !strings.Contains(r.Stderr, "--ca-key-password") {
		t.Fatalf("no password: %+v", r)
	}
	if r := yard.Run(append(issue, "--ca-key-password", "wrong")...); r.ExitCode == 0 || !strings.Contains(r.Stderr, "wrong password") {
		t.Fatalf("wrong password: %+v", r)
	}
	if r := yard.Run(append(issue, "--ca-key-password", "hunter2")...); r.ExitCode != 0 {
		t.Fatalf("right password: %+v", r)
	}
	ca := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt"))
	leaf := readTLSCertificate(t, filepath.Join(folder, "ca", "certs", "ws-a", "traefik.crt"))
	if err := leaf.CheckSignatureFrom(ca); err != nil {
		t.Fatalf("the CA did not sign: %v", err)
	}

	yard.Process.Terminal = true
	yard.Process.Stdin = "hunter2\n\n"
	r := yard.Run("ws", "tls", "ca", "issue", "user", "alice", "--password", "pw", "--dir", folder)
	if r.ExitCode != 0 || !strings.Contains(r.Stdout, "Password of the CA key") || yard.Process.SecretReads != 1 {
		t.Fatalf("question: %+v", r)
	}
}

func TestInstallAndExportNeedNoCAKeyPassword(t *testing.T) {
	yard, folder := newCAFolder(t)
	for _, args := range [][]string{
		{"ws", "tls", "ca", "create", "--dir", folder, "--ca-key-password", "hunter2"},
		{"ws", "tls", "ca", "issue", "traefik", "--dir", folder, "--ca-key-password", "hunter2"},
		{"ws", "tls", "install", "--dir", folder},
		{"ws", "tls", "ca", "export", "ca", "--dir", folder},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatalf("%v: %+v", args, r)
		}
	}
}

func writePasswordFile(t *testing.T, content string, mode os.FileMode) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "password")
	if err := os.WriteFile(path, []byte(content), mode); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestTheCAKeyPasswordComesFromAFileOrTheStandardInput(t *testing.T) {
	yard, folder := newCAFolder(t)
	// A password manager prints the password and a line break. The break is not part of it.
	file := writePasswordFile(t, "from-file\n", 0600)
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder, "--ca-key-password-file", file); r.ExitCode != 0 || strings.Contains(r.Stdout+r.Stderr, "from-file") {
		t.Fatalf("create: %+v", r)
	}
	if !strings.Contains(caKeyFile(t, folder), "BEGIN ENCRYPTED "+"PRIVATE KEY") {
		t.Fatal("the key is not encrypted")
	}
	if r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder, "--ca-key-password", "from-file"); r.ExitCode != 0 {
		t.Fatalf("the password in the file is not the one that was set: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder, "--ca-key-password-file", file); r.ExitCode != 0 {
		t.Fatalf("issue with the file: %+v", r)
	}

	yard.Process.Stdin = "piped\r\n"
	if r := yard.Run("ws", "tls", "ca", "create", "--force", "--dir", folder, "--ca-key-password-file", "-"); r.ExitCode != 0 {
		t.Fatalf("create with the standard input: %+v", r)
	}
	yard.Process.Stdin = "piped\n"
	if r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder, "--ca-key-password-file", "-"); r.ExitCode != 0 {
		t.Fatalf("issue with the standard input: %+v", r)
	}
}

func TestTheCAKeyPasswordFileRefusals(t *testing.T) {
	yard, folder := newCAFolder(t)
	for name, tc := range map[string]struct {
		args []string
		want string
	}{
		"empty file":   {[]string{"--ca-key-password-file", writePasswordFile(t, "\n", 0600)}, "holds no password"},
		"missing file": {[]string{"--ca-key-password-file", filepath.Join(t.TempDir(), "none")}, "Cannot read"},
		"both flags":   {[]string{"--ca-key-password-file", writePasswordFile(t, "x", 0600), "--ca-key-password", "x"}, "only one of"},
	} {
		r := yard.Run(append([]string{"ws", "tls", "ca", "create", "--dir", folder}, tc.args...)...)
		if r.ExitCode == 0 || !strings.Contains(r.Stderr, tc.want) {
			t.Fatalf("%s: %+v", name, r)
		}
	}
	if commandtest.UnixModes {
		r := yard.Run("ws", "tls", "ca", "create", "--dir", folder, "--ca-key-password-file", writePasswordFile(t, "x", 0644))
		if r.ExitCode == 0 || !strings.Contains(r.Stderr, "can be read by other users") {
			t.Fatalf("a readable file was accepted: %+v", r)
		}
	}
	if _, err := os.Stat(filepath.Join(folder, "ca", "ca.key")); err == nil {
		t.Fatal("a refused password left a CA")
	}
}
