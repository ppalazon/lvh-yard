// Package update finds a release of lvh-yard on GitHub, downloads it, checks it against SHA256SUMS and replaces the
// running executable. The install scripts in dev/ do the same steps for a first install.
package update

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// Releases is the address of the releases of the GitHub repository.
const Releases = "https://github.com/ppalazon/lvh-yard/releases"

const (
	maxManifest   = 1 << 20
	maxArchive    = 256 << 20
	maxExecutable = 256 << 20
)

// Client is the HTTP client of an update. A download of a few megabytes needs no more than this.
var Client = &http.Client{Timeout: 5 * time.Minute}

// Release is one archive of a release, for one platform.
type Release struct {
	// Version has no leading v.
	Version  string
	Archive  string
	URL      string
	Checksum string
}

// Platform is the name that release archives use for a system, such as linux-amd64.
func Platform(goos, goarch string) (string, error) {
	switch goos + "/" + goarch {
	case "linux/amd64":
		return "linux-amd64", nil
	case "linux/arm64":
		return "linux-arm64", nil
	case "darwin/arm64":
		return "macos-arm64", nil
	case "windows/amd64":
		return "windows-amd64", nil
	}
	return "", fmt.Errorf("there is no lvh-yard release for %s %s. Releases cover Linux (amd64, arm64), macOS (arm64) and Windows (amd64)", goos, goarch)
}

func isWindows(platform string) bool { return strings.HasPrefix(platform, "windows-") }

func extension(platform string) string {
	if isWindows(platform) {
		return ".zip"
	}
	return ".tar.gz"
}

// Executable is the name of the executable inside an archive.
func Executable(platform string) string {
	if isWindows(platform) {
		return "lvh-yard.exe"
	}
	return "lvh-yard"
}

var manifestLine = regexp.MustCompile(`^([0-9a-fA-F]{64}) [ *]lvh-yard-([0-9A-Za-z][0-9A-Za-z.+-]*)-([a-z0-9]+-[a-z0-9]+)\.(?:tar\.gz|zip)$`)

// Find reads SHA256SUMS of a release, and gives the archive of the platform. An empty version selects the latest
// release, which is never a pre-release.
func Find(client *http.Client, releases, version, platform string) (Release, error) {
	base := releases + "/latest/download"
	if version != "" {
		base = releases + "/download/v" + strings.TrimPrefix(version, "v")
	}
	body, err := get(client, base+"/SHA256SUMS", maxManifest)
	if err != nil {
		if version != "" {
			return Release{}, fmt.Errorf("cannot read the release %s: %w", version, err)
		}
		return Release{}, fmt.Errorf("cannot read the latest release: %w", err)
	}
	suffix := "-" + platform + extension(platform)
	for line := range strings.SplitSeq(string(body), "\n") {
		match := manifestLine.FindStringSubmatch(strings.TrimSpace(line))
		if match == nil || match[3] != platform {
			continue
		}
		archive := "lvh-yard-" + match[2] + suffix
		return Release{Version: match[2], Archive: archive, URL: base + "/" + archive, Checksum: strings.ToLower(match[1])}, nil
	}
	return Release{}, fmt.Errorf("the release has no archive for %s", platform)
}

// Download gives the archive, after it matches the checksum of SHA256SUMS.
func (r Release) Download(client *http.Client) ([]byte, error) {
	data, err := get(client, r.URL, maxArchive)
	if err != nil {
		return nil, fmt.Errorf("cannot download %s: %w", r.Archive, err)
	}
	sum := sha256.Sum256(data)
	if hex.EncodeToString(sum[:]) != r.Checksum {
		return nil, fmt.Errorf("the checksum of %s does not match SHA256SUMS", r.Archive)
	}
	return data, nil
}

func get(client *http.Client, url string, limit int64) ([]byte, error) {
	response, err := client.Get(url)
	if err != nil {
		return nil, err
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%s answered %s", url, response.Status)
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, limit+1))
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > limit {
		return nil, fmt.Errorf("%s is larger than %d bytes", url, limit)
	}
	return data, nil
}

// Extract gives the executable of an archive.
func Extract(archive []byte, platform string) ([]byte, error) {
	name := Executable(platform)
	if isWindows(platform) {
		reader, err := zip.NewReader(bytes.NewReader(archive), int64(len(archive)))
		if err != nil {
			return nil, fmt.Errorf("cannot read the archive: %w", err)
		}
		for _, file := range reader.File {
			if file.Name != name {
				continue
			}
			member, err := file.Open()
			if err != nil {
				return nil, err
			}
			defer func() { _ = member.Close() }()
			return readExecutable(member)
		}
		return nil, fmt.Errorf("the archive has no %s", name)
	}
	unzipped, err := gzip.NewReader(bytes.NewReader(archive))
	if err != nil {
		return nil, fmt.Errorf("cannot read the archive: %w", err)
	}
	defer func() { _ = unzipped.Close() }()
	reader := tar.NewReader(unzipped)
	for {
		header, err := reader.Next()
		if errors.Is(err, io.EOF) {
			return nil, fmt.Errorf("the archive has no %s", name)
		}
		if err != nil {
			return nil, fmt.Errorf("cannot read the archive: %w", err)
		}
		if header.Name == name && header.Typeflag == tar.TypeReg {
			return readExecutable(reader)
		}
	}
}

func readExecutable(from io.Reader) ([]byte, error) {
	data, err := io.ReadAll(io.LimitReader(from, maxExecutable+1))
	if err != nil {
		return nil, err
	}
	if len(data) == 0 || len(data) > maxExecutable {
		return nil, errors.New("the executable in the archive has a wrong size")
	}
	return data, nil
}

// Replace writes the new executable in place of the one at path. It moves the old file aside first, because
// Windows cannot overwrite a running executable but can rename it. A failure leaves the old executable in place.
func Replace(path string, executable []byte) error {
	dir := filepath.Dir(path)
	next, old := filepath.Join(dir, ".lvh-yard.new"), filepath.Join(dir, ".lvh-yard.old")
	// An earlier update on Windows could not delete its old executable, because it was running.
	_ = os.Remove(old)
	if err := os.WriteFile(next, executable, 0o755); err != nil { // #nosec G306 -- an executable
		return err
	}
	if err := os.Chmod(next, 0o755); err != nil { // #nosec G302 -- an executable
		_ = os.Remove(next)
		return err
	}
	if err := os.Rename(path, old); err != nil {
		_ = os.Remove(next)
		return err
	}
	if err := os.Rename(next, path); err != nil {
		_ = os.Rename(old, path)
		_ = os.Remove(next)
		return err
	}
	// Windows refuses while the old executable runs. The next update deletes it.
	_ = os.Remove(old)
	return nil
}

// Compare says whether version a is older than b (-1), the same (0) or newer (1). A pre-release, such as 1.2.0-rc.1,
// is older than its release. It reports false when a version is not numbers separated by dots.
func Compare(a, b string) (int, bool) {
	coreA, preA, okA := parse(a)
	coreB, preB, okB := parse(b)
	if !okA || !okB {
		return 0, false
	}
	for i := range coreA {
		if coreA[i] != coreB[i] {
			if coreA[i] < coreB[i] {
				return -1, true
			}
			return 1, true
		}
	}
	switch {
	case preA == preB:
		return 0, true
	case preA == "":
		return 1, true
	case preB == "":
		return -1, true
	}
	return strings.Compare(preA, preB), true
}

func parse(version string) ([3]int, string, bool) {
	var core [3]int
	version = strings.TrimPrefix(version, "v")
	numbers, pre, _ := strings.Cut(version, "-")
	parts := strings.Split(numbers, ".")
	if len(parts) == 0 || len(parts) > 3 {
		return core, "", false
	}
	for i, part := range parts {
		n, err := strconv.Atoi(part)
		if err != nil || n < 0 {
			return core, "", false
		}
		core[i] = n
	}
	return core, pre, true
}
