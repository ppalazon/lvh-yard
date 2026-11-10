package command_test

import (
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

const rawBackup = "# Keep this comment and the quoted string.\nversion: 2\nroute-profiles: {text: \"100\"}\n"

func TestExportPrintsRawBackup(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.URL.Path != "/api/workstation/backup" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
		io.WriteString(w, rawBackup)
	}))
	defer server.Close()
	result := commandtest.New(t).Run("--dashboard", server.URL, "export")
	if result.ExitCode != 0 || result.Stdout != rawBackup || result.Stderr != "" {
		t.Fatalf("unexpected export: %+v", result)
	}
}

func TestExportWritesOwnerOnlyBackup(t *testing.T) {
	for _, tc := range []struct {
		name     string
		flag     string
		existing bool
	}{
		{"new file", "-o", false},
		{"existing wider file", "--output", true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				io.WriteString(w, rawBackup)
			}))
			defer server.Close()
			file := filepath.Join(t.TempDir(), "ws-a.yaml")
			if tc.existing {
				if err := os.WriteFile(file, []byte(strings.Repeat("older and longer Backup\n", 20)), 0644); err != nil {
					t.Fatal(err)
				}
				if err := os.Chmod(file, 0644); err != nil {
					t.Fatal(err)
				}
			}
			result := commandtest.New(t).Run("--dashboard", server.URL, "export", tc.flag, file)
			if result.ExitCode != 0 || result.Stdout != "Wrote the Backup to "+file+".\n" || result.Stderr != "" {
				t.Fatalf("unexpected export: %+v", result)
			}
			data, err := os.ReadFile(file)
			if err != nil || string(data) != rawBackup {
				t.Fatalf("got file %q, %v; want %q", data, err, rawBackup)
			}
			info, err := os.Stat(file)
			if err != nil {
				t.Fatal(err)
			}
			if info.Mode().Perm() != 0600 {
				t.Fatalf("got permissions %o; want 600", info.Mode().Perm())
			}
		})
	}
}

func TestImportPrintsPlanAndSummary(t *testing.T) {
	const entries = `"entries":[{"kind":"route-profile","name":"gzip","action":"add"},{"kind":"service","name":"web","action":"skip","reason":"It exists already."},{"kind":"static-instance","name":"10.1.2.3:8080","service":"web","action":"add"},{"kind":"publication","name":"web","action":"skip","reason":"It exists already."}]`
	const lines = "Backup of ws-a at 2026-11-05T10:00:00Z.\nadd Route Profile gzip\nskip Service web: It exists already.\nadd Static Instance 10.1.2.3:8080 of web\nskip Publication of web: It exists already.\n"
	for _, tc := range []struct {
		name     string
		flags    []string
		stdin    bool
		query    string
		response string
		want     string
	}{
		{"add file", nil, false, "mode=add", `{"exportedFrom":{"workstation":"ws-a","at":"2026-11-05T10:00:00Z"},"mode":"add","dryRun":false,` + entries + `}`, lines + "Added 2, skipped 2.\n"},
		{"stdin", nil, true, "mode=add", `{"exportedFrom":{"workstation":"ws-a","at":"2026-11-05T10:00:00Z"},"mode":"add","dryRun":false,` + entries + `}`, lines + "Added 2, skipped 2.\n"},
		{"dry run", []string{"--dry-run"}, false, "mode=add&dry-run=true", `{"exportedFrom":{"workstation":"ws-a","at":"2026-11-05T10:00:00Z"},"mode":"add","dryRun":true,` + entries + `}`, lines + "Dry run: nothing changed.\n"},
		{"safety Backup", nil, false, "mode=add", `{"exportedFrom":{"workstation":"ws-a","at":"2026-11-05T10:00:00Z"},"mode":"add","dryRun":false,"safetyBackup":"before-import.yaml",` + entries + `}`, lines + "Added 2, skipped 2.\nSafety Backup: before-import.yaml, in the Backups folder.\n"},
		{"update", []string{"--update"}, false, "mode=update", `{"mode":"update","dryRun":false,"entries":[{"kind":"service","name":"web","action":"skip","reason":"It exists already."},{"kind":"static-instance","name":"10.1.2.3:8080","service":"web","action":"update"},{"kind":"publication","name":"web","action":"update"},{"kind":"instance-priority","name":"web","action":"update"},{"kind":"alias","name":"ws-b/grafana","service":"dash","action":"add"},{"kind":"container-binding","name":"web:8080","service":"web","action":"add"}]}`, "skip Service web: It exists already.\nupdate Static Instance 10.1.2.3:8080 of web\nupdate Publication of web\nupdate Instance Priority of web\nadd Alias to ws-b/grafana of dash\nadd Container Binding web:8080 of web\nAdded 2, updated 3, skipped 1.\n"},
		{"update dry run", []string{"--update", "--dry-run"}, false, "mode=update&dry-run=true", `{"mode":"update","dryRun":true,"entries":[{"kind":"publication","name":"web","action":"update"}]}`, "update Publication of web\nDry run: nothing changed.\n"},
		{"only updates", []string{"--update"}, false, "mode=update", `{"exportedFrom":{"workstation":"ws-a"},"mode":"update","dryRun":false,"entries":[{"kind":"publication","name":"web","action":"update"}]}`, "Backup of ws-a.\nupdate Publication of web\nAdded 0, updated 1, skipped 0.\n"},
		{"all skipped", nil, false, "mode=add", `{"mode":"add","dryRun":false,"entries":[{"kind":"service","name":"web","action":"skip","reason":"It exists already."}]}`, "skip Service web: It exists already.\nNothing changed: skipped 1.\n"},
		{"empty Backup", nil, false, "mode=add", `{"mode":"add","dryRun":false,"entries":[]}`, "Nothing changed: skipped 0.\n"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				body, err := io.ReadAll(r.Body)
				if err != nil {
					t.Error(err)
				}
				if r.Method != "POST" || r.URL.Path != "/api/workstation/backup" || r.URL.RawQuery != tc.query || r.Header.Get("Content-Type") != "application/yaml" || string(body) != rawBackup {
					t.Errorf("unexpected import: %s %s %v %q", r.Method, r.URL, r.Header, body)
				}
				io.WriteString(w, tc.response)
			}))
			defer server.Close()
			h := commandtest.New(t)
			file := "-"
			if tc.stdin {
				h.Process.Stdin = rawBackup
			} else {
				file = filepath.Join(t.TempDir(), "ws-a.yaml")
				if err := os.WriteFile(file, []byte(rawBackup), 0600); err != nil {
					t.Fatal(err)
				}
			}
			args := append([]string{"--dashboard", server.URL, "import"}, tc.flags...)
			result := h.Run(append(args, file)...)
			if result.ExitCode != 0 || result.Stdout != tc.want || result.Stderr != "" {
				t.Fatalf("got %+v; want output %q", result, tc.want)
			}
		})
	}
}

func TestImportPrintsRefusalAndRecoveryDetails(t *testing.T) {
	for _, tc := range []struct {
		name     string
		status   int
		response string
		want     string
	}{
		{"refused Backup", 400, `{"detail":"The Backup is refused.","problems":[{"place":"services.web.sources[0].static-instance.port","reason":"The port is missing."},{"place":"route-profiles.auth","reason":"The Route Profile is not valid."},{"place":"","reason":"The file is not valid YAML."}]}`, "services.web.sources[0].static-instance.port: The port is missing.\nroute-profiles.auth: The Route Profile is not valid.\nThe file is not valid YAML.\n"},
		{"partly applied", 500, `{"detail":"The import stopped at Publication of web: Consul does not answer.","applied":[{"kind":"route-profile","name":"gzip","action":"add"},{"kind":"static-instance","name":"10.1.2.3:8080","service":"web","action":"add"}],"safetyBackup":"before-import.yaml"}`, "The import stopped at Publication of web: Consul does not answer.\nIt applied:\nadd Route Profile gzip\nadd Static Instance 10.1.2.3:8080 of web\nSafety Backup: before-import.yaml, in the Backups folder. To go back, import it.\n"},
		{"nothing applied", 500, `{"detail":"The import stopped at Route Profile gzip: Consul does not answer.","applied":[],"safetyBackup":"before-import.yaml"}`, "The import stopped at Route Profile gzip: Consul does not answer.\nIt applied nothing.\nSafety Backup: before-import.yaml, in the Backups folder. To go back, import it.\n"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.status)
				io.WriteString(w, tc.response)
			}))
			defer server.Close()
			h := commandtest.New(t)
			h.Process.Stdin = rawBackup
			result := h.Run("--dashboard", server.URL, "import", "-")
			if result.ExitCode != 1 || result.Stdout != "" || result.Stderr != tc.want {
				t.Fatalf("got %+v; want error %q", result, tc.want)
			}
		})
	}
}

func TestImportMissingFileDoesNotCallDashboard(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("missing file must not call the dashboard")
	}))
	defer server.Close()
	file := filepath.Join(t.TempDir(), "missing.yaml")
	result := commandtest.New(t).Run("--dashboard", server.URL, "import", file)
	if result.ExitCode != 1 || result.Stdout != "" || !strings.HasPrefix(result.Stderr, "Cannot read "+file+":") {
		t.Fatalf("unexpected missing file result: %+v", result)
	}
}

func TestExportCannotWriteFile(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, rawBackup)
	}))
	defer server.Close()
	file := filepath.Join(t.TempDir(), "missing-folder", "ws-a.yaml")
	result := commandtest.New(t).Run("--dashboard", server.URL, "export", "-o", file)
	if result.ExitCode != 1 || result.Stdout != "" || !strings.HasPrefix(result.Stderr, "Cannot write "+file+":") {
		t.Fatalf("unexpected write failure: %+v", result)
	}
}

func TestBackupCommandsRejectWrongArgumentsBeforeRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Error("invalid arguments must not call the dashboard")
	}))
	defer server.Close()
	for _, args := range [][]string{{"export", "extra"}, {"import"}, {"import", "one", "two"}} {
		result := commandtest.New(t).Run(append([]string{"--dashboard", server.URL}, args...)...)
		if result.ExitCode != 1 || result.Stdout != "" || result.Stderr == "" {
			t.Fatalf("unexpected argument failure: %+v", result)
		}
	}
}
