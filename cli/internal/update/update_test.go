package update_test

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
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/update"
)

func tarGz(t *testing.T, files map[string]string) []byte {
	t.Helper()
	var buffer bytes.Buffer
	zipper := gzip.NewWriter(&buffer)
	archive := tar.NewWriter(zipper)
	for name, text := range files {
		if err := archive.WriteHeader(&tar.Header{Name: name, Mode: 0o755, Size: int64(len(text)), Typeflag: tar.TypeReg}); err != nil {
			t.Fatal(err)
		}
		if _, err := archive.Write([]byte(text)); err != nil {
			t.Fatal(err)
		}
	}
	if err := archive.Close(); err != nil {
		t.Fatal(err)
	}
	if err := zipper.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

func zipFile(t *testing.T, files map[string]string) []byte {
	t.Helper()
	var buffer bytes.Buffer
	archive := zip.NewWriter(&buffer)
	for name, text := range files {
		member, err := archive.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := member.Write([]byte(text)); err != nil {
			t.Fatal(err)
		}
	}
	if err := archive.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

func checksum(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

// release serves SHA256SUMS and the archive of a release for the Linux amd64 platform, both as the latest release and
// under its tag.
func release(t *testing.T, version string, archive []byte) *httptest.Server {
	t.Helper()
	name := "lvh-yard-" + version + "-linux-amd64.tar.gz"
	sums := fmt.Sprintf("%s  lvh-yard-%s-linux-arm64.tar.gz\n%s  %s\n%s  lvh-yard-%s-windows-amd64.zip\n",
		strings.Repeat("0", 64), version, checksum(archive), name, strings.Repeat("1", 64), version)
	mux := http.NewServeMux()
	for _, base := range []string{"/latest/download/", "/download/v" + version + "/"} {
		mux.HandleFunc(base+"SHA256SUMS", func(w http.ResponseWriter, _ *http.Request) { _, _ = w.Write([]byte(sums)) })
		mux.HandleFunc(base+name, func(w http.ResponseWriter, _ *http.Request) { _, _ = w.Write(archive) })
	}
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	return server
}

func TestFindDownloadAndExtractGiveTheExecutable(t *testing.T) {
	archive := tarGz(t, map[string]string{"LICENSE": "license", "lvh-yard": "new executable"})
	server := release(t, "1.2.3-rc.1", archive)
	for _, version := range []string{"", "1.2.3-rc.1", "v1.2.3-rc.1"} {
		found, err := update.Find(server.Client(), server.URL, version, "linux-amd64")
		if err != nil || found.Version != "1.2.3-rc.1" || found.Archive != "lvh-yard-1.2.3-rc.1-linux-amd64.tar.gz" {
			t.Fatalf("Find(%q) = %+v, %v", version, found, err)
		}
		data, err := found.Download(server.Client())
		if err != nil {
			t.Fatal(err)
		}
		executable, err := update.Extract(data, "linux-amd64")
		if err != nil || string(executable) != "new executable" {
			t.Fatalf("Extract = %q, %v", executable, err)
		}
	}
}

func TestFindRefusesWhatItCannotTrust(t *testing.T) {
	archive := tarGz(t, map[string]string{"lvh-yard": "x"})
	server := release(t, "1.0.0", archive)
	if _, err := update.Find(server.Client(), server.URL, "9.9.9", "linux-amd64"); err == nil || !strings.Contains(err.Error(), "9.9.9") {
		t.Errorf("unknown version: %v", err)
	}
	if _, err := update.Find(server.Client(), server.URL, "", "macos-arm64"); err == nil || !strings.Contains(err.Error(), "macos-arm64") {
		t.Errorf("missing platform: %v", err)
	}
	found, err := update.Find(server.Client(), server.URL, "", "linux-amd64")
	if err != nil {
		t.Fatal(err)
	}
	found.Checksum = strings.Repeat("a", 64)
	if _, err := found.Download(server.Client()); err == nil || !strings.Contains(err.Error(), "checksum") {
		t.Errorf("wrong checksum: %v", err)
	}
}

func TestExtractReadsTheWindowsZip(t *testing.T) {
	executable, err := update.Extract(zipFile(t, map[string]string{"LICENSE": "license", "lvh-yard.exe": "exe"}), "windows-amd64")
	if err != nil || string(executable) != "exe" {
		t.Fatalf("Extract = %q, %v", executable, err)
	}
	if _, err := update.Extract(zipFile(t, map[string]string{"other.exe": "exe"}), "windows-amd64"); err == nil {
		t.Error("an archive without lvh-yard.exe was accepted")
	}
	if _, err := update.Extract(tarGz(t, map[string]string{"LICENSE": "license"}), "linux-amd64"); err == nil {
		t.Error("an archive without lvh-yard was accepted")
	}
}

func TestReplaceSwapsTheExecutableAndLeavesNoStagingFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "lvh-yard")
	if err := os.WriteFile(path, []byte("old"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, ".lvh-yard.old"), []byte("older"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := update.Replace(path, []byte("new")); err != nil {
		t.Fatal(err)
	}
	if got, _ := os.ReadFile(path); string(got) != "new" {
		t.Fatalf("executable = %q", got)
	}
	entries, _ := os.ReadDir(dir)
	if len(entries) != 1 {
		t.Fatalf("files left in %s: %v", dir, entries)
	}
}

func TestReplaceKeepsTheOldExecutableWhenItCannotWrite(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "lvh-yard")
	if err := os.WriteFile(path, []byte("old"), 0o755); err != nil {
		t.Fatal(err)
	}
	// A folder where the staging file belongs makes the write fail.
	if err := os.Mkdir(filepath.Join(dir, ".lvh-yard.new"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := update.Replace(path, []byte("new")); err == nil {
		t.Fatal("Replace succeeded")
	}
	if got, _ := os.ReadFile(path); string(got) != "old" {
		t.Fatalf("executable = %q", got)
	}
}

func TestCompare(t *testing.T) {
	for _, test := range []struct {
		a, b string
		want int
	}{
		{"1.2.3", "1.2.3", 0},
		{"1.2.3", "1.2.4", -1},
		{"1.10.0", "1.9.9", 1},
		{"v1.2", "1.2.0", 0},
		{"1.2.0-rc.1", "1.2.0", -1},
		{"1.2.0", "1.2.0-rc.1", 1},
		{"1.2.0-rc.1", "1.2.0-rc.2", -1},
		{"1.2.0-rc.1", "1.1.9", 1},
	} {
		if got, ok := update.Compare(test.a, test.b); !ok || got != test.want {
			t.Errorf("Compare(%q, %q) = %d, %v, want %d", test.a, test.b, got, ok, test.want)
		}
	}
	for _, bad := range []string{"dev", "", "1.x", "1.2.3.4"} {
		if _, ok := update.Compare(bad, "1.0.0"); ok {
			t.Errorf("Compare(%q) accepted it", bad)
		}
	}
}
