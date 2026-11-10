package command_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestServiceCreateAndDelete(t *testing.T) {
	for _, tc := range []struct {
		name, action string
		status       int
		body, output string
	}{
		{"new", "create", 201, `{"name":"web","created":true}`, "Created web."},
		{"existing", "create", 200, `{"name":"web","created":true}`, "web exists already."},
		{"delete", "delete", 204, "", "Deleted web"},
		{"missing", "delete", 404, `{"detail":"This Workstation has no Service web."}`, "Deleted web"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				method, path := "DELETE", "/api/workstation/services/web"
				if tc.action == "create" {
					method, path = "POST", "/api/workstation/services"
					var body map[string]string
					if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body["name"] != "web" {
						t.Errorf("unexpected create body: %+v, %v", body, err)
					}
				}
				if r.Method != method || r.URL.Path != path {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
				w.WriteHeader(tc.status)
				w.Write([]byte(tc.body))
			}))
			defer server.Close()
			result := commandtest.New(t).Run("--dashboard", server.URL, "service", tc.action, "web")
			if result.ExitCode != 0 || result.Stderr != "" || !strings.Contains(result.Stdout, tc.output) {
				t.Fatalf("unexpected result: %+v", result)
			}
		})
	}
}

func TestServiceGroupAndRequiredNames(t *testing.T) {
	h := commandtest.New(t)
	result := h.Run("service")
	if result.ExitCode != 0 || !strings.Contains(result.Stdout, "create") || !strings.Contains(result.Stdout, "delete") {
		t.Fatalf("unexpected group help: %+v", result)
	}
	for _, action := range []string{"create", "delete"} {
		result := h.Run("service", action)
		if result.ExitCode != 1 || result.Stderr == "" {
			t.Fatalf("missing name accepted: %+v", result)
		}
	}
}

func TestServiceChangesPrintRemoteRefusalOnOneLine(t *testing.T) {
	for _, args := range [][]string{
		{"service", "create", "web"},
		{"service", "delete", "web"},
		{"register", "static", "web", "--port", "80"},
		{"remove", "web", "--id", "one"},
	} {
		t.Run(strings.Join(args, " "), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(409)
				w.Write([]byte(`{"detail":"The Services of desk can be changed only on desk."}`))
			}))
			defer server.Close()
			result := commandtest.New(t).Run(append([]string{"--dashboard", server.URL}, args...)...)
			if result.ExitCode != 1 || result.Stdout != "" || result.Stderr != "The Services of desk can be changed only on desk.\n" {
				t.Fatalf("unexpected refusal: %+v", result)
			}
		})
	}
}
