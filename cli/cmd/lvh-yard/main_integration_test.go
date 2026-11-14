//go:build integration

package main_test

import (
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// builtVersion is the version of the executable under test.
const builtVersion = "9.8.7"

// executable is the lvh-yard that every test here runs.
var executable string

// TestMain builds the executable once. On a computer without Go, YARD_TEST_EXECUTABLE names an
// executable that was built for it with -ldflags "-X main.version=9.8.7". A copy of this test
// binary also stands in for the editor and for docker: see standIn.
func TestMain(m *testing.M) {
	os.Exit(runTests(m))
}

func runTests(m *testing.M) int {
	if role := os.Getenv(standInRole); role != "" {
		return actAs(role)
	}
	executable = os.Getenv("YARD_TEST_EXECUTABLE")
	if executable == "" {
		folder, err := os.MkdirTemp("", "lvh-yard-build-")
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			return 1
		}
		defer func() { _ = os.RemoveAll(folder) }()
		executable = filepath.Join(folder, executableName("lvh-yard"))
		build := exec.Command("go", "build", "-o", executable, "-ldflags", "-X main.version="+builtVersion, ".")
		if output, err := build.CombinedOutput(); err != nil {
			fmt.Fprintf(os.Stderr, "build: %v\n%s", err, output)
			return 1
		}
	}
	return m.Run()
}

func executableName(name string) string {
	if runtime.GOOS == "windows" {
		return name + ".exe"
	}
	return name
}

type result struct {
	stdout, stderr string
	code           int
}

// run starts the executable with the environment of the test and the given settings on top.
func run(t *testing.T, env []string, args ...string) result {
	t.Helper()
	cmd := exec.Command(executable, args...)
	cmd.Env = append(append(os.Environ(), "YARD_CONFIG_DIR="+t.TempDir()), env...)
	var stdout, stderr strings.Builder
	cmd.Stdout, cmd.Stderr = &stdout, &stderr
	err := cmd.Run()
	if exit, ok := errors.AsType[*exec.ExitError](err); ok {
		return result{stdout.String(), stderr.String(), exit.ExitCode()}
	}
	if err != nil {
		t.Fatal(err)
	}
	return result{stdout.String(), stderr.String(), 0}
}

func TestBuiltExecutableAgainstAFakeDashboard(t *testing.T) {
	overall := "ok"
	dashboard := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/status" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"overall":"` + overall + `","parts":[{"name":"Consul","state":"` + overall + `","reason":"fake","details":[]}]}`))
	}))
	defer dashboard.Close()
	env := []string{"YARD_DASHBOARD_URL=" + dashboard.URL}

	if got := run(t, env, "--version"); got.code != 0 || got.stdout != "lvh-yard "+builtVersion+"\n" || got.stderr != "" {
		t.Fatalf("version: %+v", got)
	}
	if got := run(t, env, "status"); got.code != 0 || !strings.Contains(got.stdout, "LVH Yard on this Workstation: ok") || got.stderr != "" {
		t.Fatalf("status ok: %+v", got)
	}
	overall = "failing"
	if got := run(t, env, "status"); got.code != 1 || !strings.Contains(got.stdout, "failing") {
		t.Fatalf("status failing: %+v", got)
	}
	if got := run(t, env, "unknown"); got.code != 1 || got.stdout != "" || got.stderr == "" {
		t.Fatalf("unknown command: %+v", got)
	}
	dashboard.Close()
	if got := run(t, env, "status"); got.code != 1 || got.stdout != "" || got.stderr == "" {
		t.Fatalf("status without a dashboard: %+v", got)
	}
}
