//go:build integration

package main_test

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

// The one test that runs the built executable: main, the version and the exit code.
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

	executable := filepath.Join(t.TempDir(), "lvh-yard")
	build := exec.Command("go", "build", "-o", executable, "-ldflags", "-X main.version=9.8.7", ".")
	if output, err := build.CombinedOutput(); err != nil {
		t.Fatalf("build: %v\n%s", err, output)
	}
	run := func(args ...string) (string, string, int) {
		cmd := exec.Command(executable, args...)
		cmd.Env = append(os.Environ(), "YARD_DASHBOARD_URL="+dashboard.URL, "YARD_CONFIG_DIR="+t.TempDir())
		var stdout, stderr strings.Builder
		cmd.Stdout, cmd.Stderr = &stdout, &stderr
		err := cmd.Run()
		var exit *exec.ExitError
		if errors.As(err, &exit) {
			return stdout.String(), stderr.String(), exit.ExitCode()
		}
		if err != nil {
			t.Fatal(err)
		}
		return stdout.String(), stderr.String(), 0
	}

	if out, errText, code := run("--version"); code != 0 || out != "lvh-yard 9.8.7\n" || errText != "" {
		t.Fatalf("version: %d %q %q", code, out, errText)
	}
	if out, errText, code := run("status"); code != 0 || !strings.Contains(out, "LVH Yard on this Workstation: ok") || errText != "" {
		t.Fatalf("status ok: %d %q %q", code, out, errText)
	}
	overall = "failing"
	if out, _, code := run("status"); code != 1 || !strings.Contains(out, "failing") {
		t.Fatalf("status failing: %d %q", code, out)
	}
	if _, errText, code := run("unknown"); code != 1 || errText == "" {
		t.Fatalf("unknown command: %d %q", code, errText)
	}
}
