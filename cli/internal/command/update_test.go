package command_test

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"reflect"
	"runtime"
	"strings"
	"sync/atomic"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"github.com/ppalazon/lvh-yard/cli/internal/update"
)

// updateFixture is an installed executable, a fake releases server that has one release, and a Workstation Folder.
type updateFixture struct {
	yard       *commandtest.Harness
	executable string
	folder     string
	// dashboardStatus is the HTTP status of the fake dashboard's Backup. Zero means 200.
	dashboardStatus atomic.Int32
}

// newUpdateFixture serves release version for the platform of the test computer. The executable that it holds says
// "new executable".
func newUpdateFixture(t *testing.T, installed, version string) *updateFixture {
	t.Helper()
	platform, err := update.Platform(runtime.GOOS, runtime.GOARCH)
	if err != nil || runtime.GOOS == "windows" {
		t.Skip("no tar.gz release for this computer")
	}
	var archive bytes.Buffer
	zipper := gzip.NewWriter(&archive)
	tarball := tar.NewWriter(zipper)
	body := "new executable"
	if err := tarball.WriteHeader(&tar.Header{Name: "lvh-yard", Mode: 0o755, Size: int64(len(body)), Typeflag: tar.TypeReg}); err != nil {
		t.Fatal(err)
	}
	if _, err := tarball.Write([]byte(body)); err != nil {
		t.Fatal(err)
	}
	if err := tarball.Close(); err != nil {
		t.Fatal(err)
	}
	if err := zipper.Close(); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(archive.Bytes())
	name := "lvh-yard-" + version + "-" + platform + ".tar.gz"
	sums := hex.EncodeToString(sum[:]) + "  " + name + "\n"
	mux := http.NewServeMux()
	for _, base := range []string{"/latest/download/", "/download/v" + version + "/"} {
		mux.HandleFunc(base+"SHA256SUMS", func(w http.ResponseWriter, _ *http.Request) { fmt.Fprint(w, sums) })
		mux.HandleFunc(base+name, func(w http.ResponseWriter, _ *http.Request) { _, _ = w.Write(archive.Bytes()) })
	}
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)

	executable := filepath.Join(t.TempDir(), "lvh-yard")
	if err := os.WriteFile(executable, []byte("old executable"), 0o755); err != nil {
		t.Fatal(err)
	}
	folder := filepath.Join(t.TempDir(), "workstation")
	yard := commandtest.New(t)
	yard.Version = installed
	yard.Releases = server.URL
	yard.Executable = executable
	yard.Process.Env["YARD_CONFIG_DIR"] = folder
	f := &updateFixture{yard: yard, executable: executable, folder: folder}
	dashboard := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if status := int(f.dashboardStatus.Load()); status != 0 {
			http.Error(w, "down", status)
			return
		}
		_, _ = io.WriteString(w, rawBackup)
	}))
	t.Cleanup(dashboard.Close)
	yard.Process.Env["YARD_DASHBOARD_URL"] = dashboard.URL
	return f
}

func (f *updateFixture) installed(t *testing.T) string {
	t.Helper()
	data, err := os.ReadFile(f.executable)
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func (f *updateFixture) writeCompose(t *testing.T) {
	t.Helper()
	if err := os.MkdirAll(f.folder, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(f.folder, "compose.yaml"), []byte("services: {}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
}

func TestUpdateReplacesTheExecutableThenTheNewOneUpgradesTheWorkstation(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	f.writeCompose(t)
	run := f.yard.Run("update")
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("update: %+v", run)
	}
	if f.installed(t) != "new executable" {
		t.Fatal("the executable was not replaced")
	}
	// Only the new executable has the templates of the new release, so it does the upgrade.
	want := []commandtest.Command{{Name: f.executable, Args: []string{"ws", "compose", "upgrade"}, Dir: f.folder}}
	if !reflect.DeepEqual(f.yard.Process.Commands, want) {
		t.Fatalf("commands = %+v, want %+v", f.yard.Process.Commands, want)
	}
	if !strings.Contains(run.Stdout, "Updating lvh-yard 1.0.0 to 2.0.0") {
		t.Fatalf("output: %q", run.Stdout)
	}
}

func TestUpdateNamesAGivenFolderForTheUpgrade(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	other := t.TempDir()
	if err := os.WriteFile(filepath.Join(other, "compose.yaml"), []byte("services: {}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if run := f.yard.Run("update", "--dir", other); run.ExitCode != 0 {
		t.Fatalf("update: %+v", run)
	}
	want := []string{"ws", "--dir", other, "compose", "upgrade"}
	if len(f.yard.Process.Commands) != 1 || !reflect.DeepEqual(f.yard.Process.Commands[0].Args, want) {
		t.Fatalf("commands = %+v, want args %v", f.yard.Process.Commands, want)
	}
}

func TestUpdateOffersToRecreateOnlyAtATerminal(t *testing.T) {
	for _, test := range []struct {
		name     string
		terminal bool
		answer   string
		recreate bool
	}{
		{"yes", true, "y\n", true},
		{"no", true, "n\n", false},
		{"no terminal", false, "y\n", false},
	} {
		t.Run(test.name, func(t *testing.T) {
			f := newUpdateFixture(t, "1.0.0", "2.0.0")
			f.writeCompose(t)
			f.yard.Process.Terminal = test.terminal
			f.yard.Process.Stdin = test.answer
			if run := f.yard.Run("update"); run.ExitCode != 0 {
				t.Fatalf("update: %+v", run)
			}
			commands := f.yard.Process.Commands
			if !test.recreate {
				if len(commands) != 1 {
					t.Fatalf("commands = %+v, want only the upgrade", commands)
				}
				return
			}
			want := []string{"compose", "--project-directory", f.folder, "up", "-d", "--force-recreate"}
			if len(commands) != 2 || commands[1].Name != "docker" || !reflect.DeepEqual(commands[1].Args, want) {
				t.Fatalf("commands = %+v, want the upgrade, then docker %v", commands, want)
			}
		})
	}
}

func TestUpdateWithoutAWorkstationFolderOnlyReplacesTheExecutable(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	run := f.yard.Run("update")
	if run.ExitCode != 0 || f.installed(t) != "new executable" || len(f.yard.Process.Commands) != 0 {
		t.Fatalf("update: %+v, commands %+v", run, f.yard.Process.Commands)
	}
	if !strings.Contains(run.Stdout, "ws init") {
		t.Fatalf("output does not point to ws init: %q", run.Stdout)
	}
}

func TestUpdateKeepsTheNewExecutableWhenTheUpgradeFails(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	f.writeCompose(t)
	f.yard.Process.RunCommand = func(string, []string, string, io.Writer, io.Writer) (int, error) { return 3, nil }
	run := f.yard.Run("update")
	if run.ExitCode != 1 || !strings.Contains(run.Stderr, "lvh-yard is updated, but") || !strings.Contains(run.Stderr, "exit code 3") {
		t.Fatalf("update: %+v", run)
	}
	if f.installed(t) != "new executable" {
		t.Fatal("the new executable was rolled back")
	}
}

func TestUpdateChangesNothingWhenThereIsNothingToInstall(t *testing.T) {
	for _, test := range []struct {
		name, installed string
		args            []string
		output          string
	}{
		{"current", "2.0.0", nil, "already installed"},
		{"newer than the latest", "3.0.0", nil, "newer than the latest release"},
		{"check", "1.0.0", []string{"--check"}, "lvh-yard update 2.0.0"},
		{"check of a current one", "2.0.0", []string{"--check"}, "already installed"},
	} {
		t.Run(test.name, func(t *testing.T) {
			f := newUpdateFixture(t, test.installed, "2.0.0")
			f.writeCompose(t)
			run := f.yard.Run(append([]string{"update"}, test.args...)...)
			if run.ExitCode != 0 || !strings.Contains(run.Stdout, test.output) {
				t.Fatalf("update: %+v", run)
			}
			if f.installed(t) != "old executable" || len(f.yard.Process.Commands) != 0 {
				t.Fatalf("update changed something: %q, commands %+v", f.installed(t), f.yard.Process.Commands)
			}
		})
	}
}

func TestUpdateInstallsAGivenOlderRelease(t *testing.T) {
	f := newUpdateFixture(t, "3.0.0", "2.0.0")
	for _, argument := range []string{"2.0.0", "v2.0.0"} {
		run := f.yard.Run("update", argument)
		if run.ExitCode != 0 || f.installed(t) != "new executable" {
			t.Fatalf("update %s: %+v", argument, run)
		}
		if err := os.WriteFile(f.executable, []byte("old executable"), 0o755); err != nil {
			t.Fatal(err)
		}
	}
}

func TestUpdateRefusesADevelopmentBuildAndAnUnknownRelease(t *testing.T) {
	f := newUpdateFixture(t, "dev", "2.0.0")
	if run := f.yard.Run("update"); run.ExitCode != 1 || !strings.Contains(run.Stderr, "development build") {
		t.Fatalf("dev update: %+v", run)
	}
	// A development build may still look.
	if run := f.yard.Run("update", "--check"); run.ExitCode != 0 || !strings.Contains(run.Stdout, "2.0.0") {
		t.Fatalf("dev check: %+v", run)
	}
	f.yard.Version = "1.0.0"
	if run := f.yard.Run("update", "9.9.9"); run.ExitCode != 1 || !strings.Contains(run.Stderr, "9.9.9") {
		t.Fatalf("unknown release: %+v", run)
	}
	if f.installed(t) != "old executable" {
		t.Fatal("a refused update changed the executable")
	}
}

func TestUpdateWritesABackupBeforeItReplacesTheExecutable(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	f.writeCompose(t)
	if err := os.WriteFile(filepath.Join(f.folder, ".env"), []byte("YARD_WORKSTATION=ws-a\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	// The Backup must exist while the old executable is still in place, so check it when the upgrade starts.
	var atUpgrade, executableAtUpgrade string
	f.yard.Process.RunCommand = func(string, []string, string, io.Writer, io.Writer) (int, error) {
		files, _ := filepath.Glob(filepath.Join(f.folder, "backups", "ws-a-*-before-update.yaml"))
		if len(files) == 1 {
			data, _ := os.ReadFile(files[0])
			atUpgrade = string(data)
		}
		executableAtUpgrade = f.installed(t)
		return 0, nil
	}
	run := f.yard.Run("update")
	if run.ExitCode != 0 || run.Stderr != "" || !strings.Contains(run.Stdout, "Backup before the update: "+filepath.Join(f.folder, "backups", "ws-a-")) {
		t.Fatalf("update: %+v", run)
	}
	if atUpgrade != rawBackup {
		t.Fatalf("Backup = %q, want %q", atUpgrade, rawBackup)
	}
	files, _ := filepath.Glob(filepath.Join(f.folder, "backups", "*.yaml"))
	info, err := os.Stat(files[0])
	if err != nil || (commandtest.UnixModes && info.Mode().Perm() != 0o600) {
		t.Fatalf("Backup mode: %v, %v", info, err)
	}
	if executableAtUpgrade != "new executable" {
		t.Fatal("the update did not replace the executable")
	}
}

func TestUpdateGoesOnWhenTheDashboardCannotGiveABackup(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	f.writeCompose(t)
	f.dashboardStatus.Store(http.StatusServiceUnavailable)
	run := f.yard.Run("update")
	if run.ExitCode != 0 || !strings.Contains(run.Stderr, "no Backup was written before the update") {
		t.Fatalf("update: %+v", run)
	}
	if f.installed(t) != "new executable" || len(f.yard.Process.Commands) != 1 {
		t.Fatalf("the update did not finish: %q, commands %+v", f.installed(t), f.yard.Process.Commands)
	}
	if files, _ := filepath.Glob(filepath.Join(f.folder, "backups", "*")); len(files) != 0 {
		t.Fatalf("files in backups: %v", files)
	}
}

func TestUpdateWritesNoBackupWhenItInstallsNothing(t *testing.T) {
	f := newUpdateFixture(t, "2.0.0", "2.0.0")
	f.writeCompose(t)
	if run := f.yard.Run("update"); run.ExitCode != 0 {
		t.Fatalf("update: %+v", run)
	}
	if _, err := os.Stat(filepath.Join(f.folder, "backups")); !os.IsNotExist(err) {
		t.Fatalf("backups folder: %v", err)
	}
}
