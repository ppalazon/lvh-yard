package command_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestRegisterStaticOptions(t *testing.T) {
	for _, tc := range []struct {
		name string
		args []string
		body string
	}{
		{"defaults", []string{"--port", "8080"}, `{"port":8080}`},
		{"host", []string{"--host", "10.1.2.3", "--port", "8080"}, `{"host":"10.1.2.3","port":8080}`},
		{"path", []string{"--port", "8080", "--path", "/health"}, `{"port":8080,"check":{"path":"/health"}}`},
		{"all", []string{"--port", "8080", "--check", "http", "--path", "/health", "--interval", "5s", "--timeout", "1s", "--tag", "backend", "--tag", "v2,blue", "--meta", "owner=pablo", "--meta", "team=core=api"}, `{"port":8080,"check":{"type":"http","path":"/health","interval":"5s","timeout":"1s"},"tags":["backend","v2,blue"],"meta":{"owner":"pablo","team":"core=api"}}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				var body, want map[string]any
				if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
					t.Error(err)
				}
				if err := json.Unmarshal([]byte(tc.body), &want); err != nil {
					t.Fatal(err)
				}
				if r.Method != "POST" || r.URL.Path != "/api/workstation/services/web/instances" || !reflect.DeepEqual(body, want) {
					t.Errorf("unexpected registration: %s %s %+v, want %+v", r.Method, r.URL, body, want)
				}
				w.WriteHeader(201)
				w.Write([]byte(`{"id":"7b1c","host":"10.1.2.3","port":8080}`))
			}))
			defer server.Close()
			args := append([]string{"--dashboard", server.URL, "register", "static", "web"}, tc.args...)
			result := commandtest.New(t).Run(args...)
			if result.ExitCode != 0 || result.Stderr != "" || result.Stdout != "Registered web at 10.1.2.3:8080, Instance 7b1c.\n" {
				t.Fatalf("unexpected result: %+v", result)
			}
		})
	}
}

func TestRegisterStaticDashboardRefusals(t *testing.T) {
	for _, tc := range []struct {
		status       int
		body, reason string
	}{
		{404, `{"detail":"This Workstation has no Service web.","service":"web"}`, "lvh-yard service create web"},
		{400, `{"detail":"Script checks are not offered: use an http or a tcp check."}`, "Script checks are not offered"},
	} {
		t.Run(tc.reason, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.status)
				w.Write([]byte(tc.body))
			}))
			defer server.Close()
			result := commandtest.New(t).Run("--dashboard", server.URL, "register", "static", "web", "--port", "8080", "--check", "script")
			if result.ExitCode != 1 || result.Stdout != "" || !strings.Contains(result.Stderr, tc.reason) || strings.Count(result.Stderr, "\n") != 1 {
				t.Fatalf("unexpected refusal: %+v", result)
			}
		})
	}
}

func TestRegisterStaticUsage(t *testing.T) {
	h := commandtest.New(t)
	for _, args := range [][]string{
		{"register", "static", "web"},
		{"register", "static", "--port", "80"},
		{"register", "static", "web", "--port", "80", "--no-publish"},
		{"register", "static", "web", "--port", "80", "--meta", "owner"},
		{"register", "web", "--port", "80"},
	} {
		result := h.Run(args...)
		if result.ExitCode != 1 || result.Stderr == "" || result.Stdout != "" {
			t.Fatalf("invalid arguments accepted: %v: %+v", args, result)
		}
	}
	result := h.Run("register")
	if result.ExitCode != 0 || !strings.Contains(result.Stdout, "static") {
		t.Fatalf("unexpected group help: %+v", result)
	}
}
