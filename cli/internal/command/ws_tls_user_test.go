package command_test

import (
	"os"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

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

func TestFilePasswordQuestionsHideTheAnswerAndAskTwice(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	yard.Process.Terminal = true
	yard.Process.Stdin = "bob-pass\nbob-pass\n\n"
	r := yard.Run("ws", "tls", "ca", "issue", "user", "bob", "--dir", folder)
	if r.ExitCode != 0 || yard.Process.SecretReads != 2 || strings.Contains(r.Stdout, "Password: ") {
		t.Fatalf("issue user: %d reads: %+v", yard.Process.SecretReads, r)
	}
}
