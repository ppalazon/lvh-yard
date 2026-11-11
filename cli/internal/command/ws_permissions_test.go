package command_test

import (
	"io/fs"
	"os"
	"path/filepath"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWSInitCreatesPrivateFolder(t *testing.T) {
	folder := filepath.Join(t.TempDir(), "yard")
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a")
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	for _, name := range []string{"backups", "consul-data"} {
		info, err := os.Stat(filepath.Join(folder, name))
		if err != nil || !info.IsDir() {
			t.Fatalf("%s folder: %v", name, err)
		}
	}
	assertPrivateWorkstation(t, folder)
}

func TestWSUpgradeRepairsExistingFolderModesEvenWhenImagesAreCurrent(t *testing.T) {
	folder := t.TempDir()
	yard := commandtest.New(t)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	if err := os.MkdirAll(filepath.Join(folder, "custom", "nested"), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(folder, "custom", "nested", "secret"), []byte("private"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := filepath.WalkDir(folder, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() {
			return os.Chmod(path, 0755)
		}
		return os.Chmod(path, 0644)
	}); err != nil {
		t.Fatal(err)
	}
	outside := filepath.Join(t.TempDir(), "outside")
	if err := os.WriteFile(outside, []byte("outside"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(outside, filepath.Join(folder, "link")); err != nil {
		t.Fatal(err)
	}
	if run := yard.Run("ws", "upgrade", "--dir", folder); run.ExitCode != 0 {
		t.Fatalf("upgrade: %+v", run)
	}
	assertPrivateWorkstation(t, folder)
	info, err := os.Stat(outside)
	if err != nil || info.Mode().Perm() != 0644 {
		t.Fatalf("outside permissions changed: %v, %v", info, err)
	}
}

func assertPrivateWorkstation(t *testing.T, folder string) {
	t.Helper()
	if err := filepath.WalkDir(folder, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.Type()&os.ModeSymlink != 0 {
			return nil
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		want := os.FileMode(0600)
		if entry.IsDir() {
			want = 0700
		}
		if info.Mode().Perm() != want {
			t.Errorf("%s mode = %o, want %o", path, info.Mode().Perm(), want)
		}
		return nil
	}); err != nil {
		t.Fatal(err)
	}
}
