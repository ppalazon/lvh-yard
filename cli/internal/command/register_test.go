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

func TestRegisterStaticAsksForTheFlagsThatAreMissing(t *testing.T) {
	for _, tc := range []struct {
		name, stdin     string
		args            []string
		body            string
		asked, notAsked []string
	}{
		{"port and host", "4000\n127.0.0.1\n", nil, `{"host":"127.0.0.1","port":4000}`, []string{"Port of the app: ", "Host of the app"}, nil},
		{"Enter keeps the LAN address", "4000\n\n", nil, `{"port":4000}`, []string{"Port of the app: ", "Host of the app"}, nil},
		{"a wrong port is asked again", "abc\n70000\n4000\n\n", nil, `{"port":4000}`, []string{"Give a port from 1 to 65535."}, nil},
		{"host only", "127.0.0.1\n", []string{"--port", "4000"}, `{"host":"127.0.0.1","port":4000}`, []string{"Host of the app"}, []string{"Port of the app"}},
		{"port only", "4000\n", []string{"--host", "10.1.2.3"}, `{"host":"10.1.2.3","port":4000}`, []string{"Port of the app: "}, []string{"Host of the app"}},
		{"both flags", "", []string{"--host", "10.1.2.3", "--port", "4000"}, `{"host":"10.1.2.3","port":4000}`, nil, []string{"Port of the app", "Host of the app"}},
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
				if !reflect.DeepEqual(body, want) {
					t.Errorf("registered %+v, want %+v", body, want)
				}
				w.WriteHeader(201)
				w.Write([]byte(`{"id":"7b1c","host":"10.1.2.3","port":4000}`))
			}))
			defer server.Close()
			h := commandtest.New(t)
			h.Process.Terminal = true
			h.Process.Stdin = tc.stdin
			result := h.Run(append([]string{"--dashboard", server.URL, "register", "static", "web"}, tc.args...)...)
			if result.ExitCode != 0 || result.Stderr != "" || !strings.HasSuffix(result.Stdout, "Registered web at 10.1.2.3:4000, Instance 7b1c.\n") {
				t.Fatalf("unexpected result: %+v", result)
			}
			for _, text := range tc.asked {
				if !strings.Contains(result.Stdout, text) {
					t.Errorf("the output does not ask %q:\n%s", text, result.Stdout)
				}
			}
			for _, text := range tc.notAsked {
				if strings.Contains(result.Stdout, text) {
					t.Errorf("the output asks %q, but a flag gave the answer:\n%s", text, result.Stdout)
				}
			}
		})
	}
}

func TestRegisterStaticWithoutAPortAsksNothingWhenNoTerminalIsOpen(t *testing.T) {
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { requests++ }))
	defer server.Close()
	result := commandtest.New(t).Run("--dashboard", server.URL, "register", "static", "web")
	if result.ExitCode != 1 || result.Stdout != "" || !strings.Contains(result.Stderr, "Missing --port") || requests != 0 {
		t.Fatalf("unexpected result: %+v, %d requests", result, requests)
	}
}

func TestRegisterStaticStopsWhenTheInputEndsBeforeTheAnswer(t *testing.T) {
	h := commandtest.New(t)
	h.Process.Terminal = true
	result := h.Run("register", "static", "web")
	if result.ExitCode != 1 || !strings.Contains(result.Stderr, "the input ended before the answer to") {
		t.Fatalf("unexpected result: %+v", result)
	}
}
