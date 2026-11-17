package command_test

import (
	"archive/zip"
	"bytes"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func exportYard(t *testing.T) (*commandtest.Harness, string) {
	t.Helper()
	yard := commandtest.New(t)
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
		{"ws", "tls", "ca", "issue", "user", "alice", "--password", "browser-secret", "--dir", folder},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	return yard, folder
}

func zipNames(t *testing.T, path string) (names []string, raw []byte) {
	t.Helper()
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	reader, err := zip.NewReader(bytes.NewReader(raw), int64(len(raw)))
	if err != nil {
		t.Fatal(err)
	}
	for _, file := range reader.File {
		names = append(names, file.Name)
	}
	return names, raw
}

func assertOwnerOnly(t *testing.T, path string) {
	t.Helper()
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if commandtest.UnixModes && info.Mode().Perm() != 0600 {
		t.Fatalf("%s has mode %o", path, info.Mode().Perm())
	}
}

func TestWSTLSExportUserHoldsThePKCS12FileAndNoPassword(t *testing.T) {
	yard, folder := exportYard(t)
	output := filepath.Join(t.TempDir(), "alice.zip")
	r := yard.Run("ws", "tls", "ca", "export", "user", "alice", "--output", output, "--dir", folder)
	if r.ExitCode != 0 || strings.Contains(r.Stdout+r.Stderr, "browser-secret") {
		t.Fatal(r)
	}
	names, raw := zipNames(t, output)
	if strings.Join(names, " ") != "alice.p12 ca.crt README.txt" {
		t.Fatalf("wrong files: %v", names)
	}
	reader, _ := zip.NewReader(bytes.NewReader(raw), int64(len(raw)))
	in, err := reader.File[0].Open()
	if err != nil {
		t.Fatal(err)
	}
	got, _ := io.ReadAll(in)
	want, err := os.ReadFile(filepath.Join(folder, "ca", "clients", "alice.p12"))
	if err != nil || !bytes.Equal(got, want) {
		t.Fatal("the zip file does not hold the .p12 file of the CA")
	}
	assertOwnerOnly(t, output)
}

func TestWSTLSExportCARefusesWhatWasNotSigned(t *testing.T) {
	yard, folder := exportYard(t)
	if r := yard.Run("ws", "tls", "ca", "export", "ca", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if names, raw := zipNames(t, filepath.Join(folder, "certificates", "ca.zip")); len(names) != 2 || bytes.Contains(raw, []byte("PRIVATE KEY")) {
		t.Fatalf("the CA zip file holds %v", names)
	}
	for _, args := range [][]string{{"user", "bob"}, {"user", "../escape"}} {
		r := yard.Run(append([]string{"ws", "tls", "ca", "export"}, append(args, "--dir", folder)...)...)
		if r.ExitCode == 0 || !strings.Contains(r.Stderr, "tls ca issue") && args[1] != "../escape" {
			t.Fatalf("%v: %+v", args, r)
		}
	}
}

func TestFilePasswordQuestionsHideTheAnswerAndAskTwice(t *testing.T) {
	yard, folder := exportYard(t)
	yard.Process.Terminal = true
	yard.Process.Stdin = "bob-pass\nbob-pass\n\n"
	r := yard.Run("ws", "tls", "ca", "issue", "user", "bob", "--dir", folder)
	if r.ExitCode != 0 || yard.Process.SecretReads != 2 || strings.Contains(r.Stdout, "Password: ") {
		t.Fatalf("issue user: %d reads: %+v", yard.Process.SecretReads, r)
	}
}
