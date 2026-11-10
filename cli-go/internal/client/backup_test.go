package client_test

import (
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestBackupReadsRawYAML(t *testing.T) {
	const backup = "# Keep this comment.\nversion: 2\nroute-profiles: {text: \"100\"}\n"
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.URL.EscapedPath() != "/prefix/api/workstation/backup" || r.Header.Get("Accept") != "application/yaml, application/problem+json" || r.Header.Get("Content-Type") != "" {
			t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
		}
		w.Header().Set("Content-Type", "application/yaml")
		io.WriteString(w, backup)
	}))
	defer server.Close()
	got, err := client.New(server.URL + "/prefix").Backup()
	if err != nil || got != backup {
		t.Fatalf("got %q, %v; want raw Backup %q", got, err, backup)
	}
}

func TestBackupMatchesOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t, func(c *client.Client) { _, _ = c.Backup() })
}

func TestImportBackupSendsRawYAMLAndReadsPlan(t *testing.T) {
	const backup = "# Do not parse this YAML.\nprofiles: {text: \"100\"}\n"
	for _, tc := range []struct {
		mode   string
		dryRun bool
		query  string
	}{
		{"add", false, "mode=add"},
		{"update", false, "mode=update"},
		{"update", true, "mode=update&dry-run=true"},
		{"add", true, "mode=add&dry-run=true"},
		{"a&b", false, "mode=a%26b"},
	} {
		t.Run(tc.query, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				body, err := io.ReadAll(r.Body)
				if err != nil {
					t.Error(err)
				}
				if r.Method != "POST" || r.URL.EscapedPath() != "/prefix/api/workstation/backup" || r.URL.RawQuery != tc.query || r.Header.Get("Content-Type") != "application/yaml" || r.Header.Get("Accept") != "application/json" || string(body) != backup {
					t.Errorf("unexpected request: %s %s %v %q", r.Method, r.URL, r.Header, body)
				}
				io.WriteString(w, `{"exportedFrom":{"workstation":"ws-a","at":"2026-11-05T10:00:00Z"},"mode":"update","dryRun":true,"entries":[{"kind":"publication","name":"web","action":"update","reason":"Changed."}],"safetyBackup":"before-import.yaml"}`)
			}))
			defer server.Close()
			result, err := client.New(server.URL+"/prefix").ImportBackup(backup, tc.mode, tc.dryRun)
			if err != nil {
				t.Fatal(err)
			}
			if result.ExportedFrom == nil || result.ExportedFrom.Workstation != "ws-a" || result.ExportedFrom.At != "2026-11-05T10:00:00Z" || result.Mode != "update" || !result.DryRun || result.SafetyBackup != "before-import.yaml" || len(result.Entries) != 1 || result.Entries[0].Reason != "Changed." {
				t.Fatalf("unexpected result: %+v", result)
			}
		})
	}
}

func TestImportBackupMatchesOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t, func(c *client.Client) { _, _ = c.ImportBackup("version: 2\n", "add", true) })
}

func TestBackupFailures(t *testing.T) {
	for _, tc := range []struct {
		name   string
		status int
		body   string
		want   string
	}{
		{"refused", 400, `{"detail":"The Backup is refused.","problems":[{"place":"route-profiles.auth","reason":"Not valid."},{"place":"","reason":"The file is not valid YAML."}]}`, "route-profiles.auth: Not valid.\nThe file is not valid YAML."},
		{"partly applied", 500, `{"detail":"The import stopped at Publication of web: Consul does not answer.","applied":[{"kind":"route-profile","name":"gzip","action":"add"},{"kind":"static-instance","name":"10.1.2.3:8080","service":"web","action":"add"}],"safetyBackup":"before-import.yaml"}`, "The import stopped at Publication of web: Consul does not answer.\nIt applied:\nadd Route Profile gzip\nadd Static Instance 10.1.2.3:8080 of web\nSafety Backup: before-import.yaml, in the Backups folder. To go back, import it."},
		{"nothing applied", 500, `{"detail":"The import stopped.","applied":[],"safetyBackup":"before-import.yaml"}`, "The import stopped.\nIt applied nothing.\nSafety Backup: before-import.yaml, in the Backups folder. To go back, import it."},
		{"ordinary failure", 500, `{"detail":"Consul does not answer."}`, "Consul does not answer."},
		{"non JSON failure", 502, `Bad gateway`, "The dashboard refused with HTTP status 502."},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.status)
				io.WriteString(w, tc.body)
			}))
			defer server.Close()
			_, err := client.New(server.URL).ImportBackup("raw YAML", "add", false)
			if err == nil || err.Error() != tc.want {
				t.Fatalf("got %v; want %q", err, tc.want)
			}
			_, err = client.New(server.URL).Backup()
			want := tc.want
			if tc.name == "partly applied" {
				want = "The import stopped at Publication of web: Consul does not answer."
			} else if tc.name == "nothing applied" {
				want = "The import stopped."
			}
			if err == nil || err.Error() != want {
				t.Fatalf("export got %v; want %q", err, want)
			}
		})
	}
}

func TestImportBackupRejectsNonJSONAnswer(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, "<html>not a dashboard</html>")
	}))
	defer server.Close()
	_, err := client.New(server.URL).ImportBackup("version: 2\n", "add", false)
	want := server.URL + "/ is not the LVH Yard dashboard: its answer is not the API's JSON."
	if err == nil || err.Error() != want {
		t.Fatalf("got %v; want %q", err, want)
	}
}
