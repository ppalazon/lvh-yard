package command_test

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"github.com/ppalazon/lvh-yard/cli/internal/update"
)

// updateFixture is an installed executable, a fake releases server that has one release, and a Workstation Folder.
type updateFixture struct {
	yard       *commandtest.Harness
	executable string
	folder     string
}

// releaseArchive packs an executable that says "new executable", as a release does for goos: a ZIP for windows and a
// tar.gz for the others. It gives the file extension of the archive too.
func releaseArchive(t *testing.T, goos, executableName string) (data []byte, extension string) {
	t.Helper()
	body := "new executable"
	var archive bytes.Buffer
	if goos == "windows" {
		writer := zip.NewWriter(&archive)
		file, err := writer.Create(executableName)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := file.Write([]byte(body)); err != nil {
			t.Fatal(err)
		}
		if err := writer.Close(); err != nil {
			t.Fatal(err)
		}
		return archive.Bytes(), ".zip"
	}
	zipper := gzip.NewWriter(&archive)
	tarball := tar.NewWriter(zipper)
	if err := tarball.WriteHeader(&tar.Header{Name: executableName, Mode: 0o755, Size: int64(len(body)), Typeflag: tar.TypeReg}); err != nil {
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
	return archive.Bytes(), ".tar.gz"
}

// newUpdateFixture serves release version for linux, which is the system that the test harness reports on every
// computer. The executable that it holds says "new executable".
func newUpdateFixture(t *testing.T, installed, version string) *updateFixture {
	t.Helper()
	return newUpdateFixtureFor(t, "linux", installed, version)
}

// newUpdateFixtureFor serves release version for the platform of goos, and makes the harness report goos. The archive
// code reads the platform that the command reports, so a test runs the same on every computer.
func newUpdateFixtureFor(t *testing.T, goos, installed, version string) *updateFixture {
	t.Helper()
	platform, err := update.Platform(goos, runtime.GOARCH)
	if err != nil {
		t.Skip("no release of " + goos + " for " + runtime.GOARCH)
	}
	executableName := update.Executable(platform)
	archive, extension := releaseArchive(t, goos, executableName)
	sum := sha256.Sum256(archive)
	name := "lvh-yard-" + version + "-" + platform + extension
	sums := hex.EncodeToString(sum[:]) + "  " + name + "\n"
	mux := http.NewServeMux()
	for _, base := range []string{"/latest/download/", "/download/v" + version + "/"} {
		mux.HandleFunc(base+"SHA256SUMS", func(w http.ResponseWriter, _ *http.Request) { fmt.Fprint(w, sums) })
		mux.HandleFunc(base+name, func(w http.ResponseWriter, _ *http.Request) { _, _ = w.Write(archive) })
	}
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)

	executable := filepath.Join(t.TempDir(), executableName)
	if err := os.WriteFile(executable, []byte("old executable"), 0o755); err != nil {
		t.Fatal(err)
	}
	folder := filepath.Join(t.TempDir(), "workstation")
	yard := commandtest.New(t)
	yard.Process.System = goos
	yard.Version = installed
	yard.InstallMethod = command.InstallMethodStandalone
	yard.Releases = server.URL
	yard.Executable = executable
	yard.Process.Env["YARD_CONFIG_DIR"] = folder
	return &updateFixture{yard: yard, executable: executable, folder: folder}
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

func TestUpdateReplacesTheExecutableAndInvitesToUpdateTheWorkstation(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	f.writeCompose(t)
	run := f.yard.Run("update")
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("update: %+v", run)
	}
	if f.installed(t) != "new executable" {
		t.Fatal("the executable was not replaced")
	}
	// Only the new executable has the templates of the new release, so the user runs the Workstation update with it.
	if len(f.yard.Process.Commands) != 0 {
		t.Fatalf("update ran %+v, and the Workstation is the next command's work", f.yard.Process.Commands)
	}
	for _, want := range []string{"Updating lvh-yard 1.0.0 to 2.0.0", "\n  lvh-yard ws compose update\n"} {
		if !strings.Contains(run.Stdout, want) {
			t.Fatalf("output has no %q: %q", want, run.Stdout)
		}
	}
	if _, err := os.Stat(filepath.Join(f.folder, "backups")); !os.IsNotExist(err) {
		t.Fatalf("update touched the Workstation Folder: %v", err)
	}
}

// A Windows release is a ZIP that holds lvh-yard.exe. The command reads the platform from the system that it reports, so
// this runs on every computer, and a Windows computer runs it natively too.
func TestUpdateInstallsTheZipOfAWindowsRelease(t *testing.T) {
	f := newUpdateFixtureFor(t, "windows", "1.0.0", "2.0.0")
	f.writeCompose(t)
	if filepath.Base(f.executable) != "lvh-yard.exe" {
		t.Fatalf("the installed executable is %s", f.executable)
	}
	run := f.yard.Run("update")
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("update: %+v", run)
	}
	if f.installed(t) != "new executable" {
		t.Fatal("the executable was not replaced")
	}
	if leftovers, _ := filepath.Glob(filepath.Join(filepath.Dir(f.executable), ".lvh-yard.*")); len(leftovers) != 0 {
		t.Fatalf("the update left %v", leftovers)
	}
	if !strings.Contains(run.Stdout, "Updating lvh-yard 1.0.0 to 2.0.0") {
		t.Fatalf("output: %q", run.Stdout)
	}
}

func TestUpdateWithoutAWorkstationFolderInvitesToInitInstead(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	run := f.yard.Run("update")
	if run.ExitCode != 0 || f.installed(t) != "new executable" {
		t.Fatalf("update: %+v", run)
	}
	if !strings.Contains(run.Stdout, "ws init") || strings.Contains(run.Stdout, "ws compose update") {
		t.Fatalf("output does not point to ws init only: %q", run.Stdout)
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

func TestUpdateRefusesAnUnknownRelease(t *testing.T) {
	f := newUpdateFixture(t, "1.0.0", "2.0.0")
	if run := f.yard.Run("update", "9.9.9"); run.ExitCode != 1 || !strings.Contains(run.Stderr, "9.9.9") {
		t.Fatalf("unknown release: %+v", run)
	}
	if f.installed(t) != "old executable" {
		t.Fatal("a refused update changed the executable")
	}
}

// A package manager owns the executable of a package, and a build from the sources has no install method. Neither
// updates itself, not even to look: the release on GitHub is not the one that the owner would install.
func TestUpdateLeavesAnExecutableThatIsNotStandalone(t *testing.T) {
	for _, method := range []string{"", "deb", "homebrew"} {
		for _, args := range [][]string{{"update"}, {"update", "--check"}, {"update", "2.0.0"}} {
			t.Run(method+"/"+strings.Join(args, " "), func(t *testing.T) {
				f := newUpdateFixture(t, "1.0.0", "2.0.0")
				f.yard.InstallMethod = method
				run := f.yard.Run(args...)
				if run.ExitCode != 1 || !strings.Contains(run.Stderr, "does not update itself") || !strings.Contains(run.Stderr, "\"lvh-yard ws compose update\"") {
					t.Fatalf("update: %+v", run)
				}
				if run.Stdout != "" || f.installed(t) != "old executable" {
					t.Fatalf("a refused update printed or changed something: %q, %q", run.Stdout, f.installed(t))
				}
			})
		}
	}
}
