package command_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestAliasAdd(t *testing.T) {
	for _, tc := range []struct {
		name, service, body, stdout, stderr string
		status, exitCode                    int
	}{
		{"existing Service", "web", `{"id":"ws-b/grafana","workstation":"ws-b","service":"grafana","state":"active","serviceCreated":false,"aliasCreated":true}`, "Added the Alias to grafana on ws-b to the existing Service web.\n", "", 201, 0},
		{"new Service", "dash", `{"id":"ws-b/grafana","workstation":"ws-b","service":"grafana","state":"active","serviceCreated":true,"aliasCreated":true}`, "Created Service dash with Local Scope and added the Alias to grafana on ws-b.\n", "", 201, 0},
		{"already added", "web", `{"id":"ws-b/grafana","workstation":"ws-b","service":"grafana","state":"active","serviceCreated":false,"aliasCreated":false}`, "Service web has the Alias to grafana on ws-b already.\n", "", 200, 0},
		{"no reachable Instances", "web", `{"id":"ws-b/grafana","workstation":"ws-b","service":"grafana","state":"unavailable","reason":"grafana has no Instance this Workstation can reach.","serviceCreated":false,"aliasCreated":true}`, "Added the Alias to grafana on ws-b to the existing Service web.\n", "Warning: grafana has no Instance this Workstation can reach.\n", 201, 0},
		{"refused", "web", `{"type":"about:blank","title":"Bad Request","status":400,"field":"service","detail":"ws-b has not published grafana. Only a Remote Service with Yard Scope can be the target of an Alias."}`, "", "ws-b has not published grafana. Only a Remote Service with Yard Scope can be the target of an Alias.\n", 400, 1},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "POST" || r.URL.Path != "/api/workstation/services/"+tc.service+"/aliases" {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
				var body map[string]any
				if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
					t.Error(err)
				}
				if len(body) != 3 || body["workstation"] != "ws-b" || body["service"] != "grafana" || body["create"] != true {
					t.Errorf("unexpected body: %#v", body)
				}
				w.WriteHeader(tc.status)
				w.Write([]byte(tc.body))
			}))
			defer server.Close()
			run := commandtest.New(t).Run("--dashboard", server.URL, "alias", "add", tc.service, "--workstation", "ws-b", "--service", "grafana")
			if run.ExitCode != tc.exitCode || run.Stdout != tc.stdout || run.Stderr != tc.stderr {
				t.Fatalf("unexpected result: %+v; want exit %d, stdout %q, stderr %q", run, tc.exitCode, tc.stdout, tc.stderr)
			}
		})
	}
}

func TestAliasAddAndRemoveRequireTarget(t *testing.T) {
	for _, operation := range []string{"add", "remove"} {
		for _, tc := range []struct {
			flag, value, missing string
		}{
			{"--service", "grafana", "--workstation"},
			{"--workstation", "ws-b", "--service"},
		} {
			t.Run(operation+" "+tc.missing, func(t *testing.T) {
				run := commandtest.New(t).Run("alias", operation, "web", tc.flag, tc.value)
				if run.ExitCode != 1 || run.Stdout != "" || !strings.Contains(run.Stderr, strings.TrimPrefix(tc.missing, "--")) {
					t.Fatalf("unexpected result: %+v", run)
				}
			})
		}
	}
}

func TestAliasList(t *testing.T) {
	for _, tc := range []struct {
		name, body, stdout string
	}{
		{"in order", `[{"id":"ws-b/grafana","workstation":"ws-b","service":"grafana","state":"not-shared","reason":"ws-b publishes grafana with Local Scope only."},{"id":"ws-c/grafana","workstation":"ws-c","service":"grafana","state":"active"}]`, "grafana on ws-b: not-shared\n  ws-b publishes grafana with Local Scope only.\ngrafana on ws-c: active\n"},
		{"none", `[]`, "web has no Aliases.\n"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "GET" || r.URL.Path != "/api/workstation/services/web/aliases" {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
				w.Write([]byte(tc.body))
			}))
			defer server.Close()
			run := commandtest.New(t).Run("--dashboard", server.URL, "alias", "list", "web")
			if run.ExitCode != 0 || run.Stdout != tc.stdout || run.Stderr != "" {
				t.Fatalf("unexpected result: %+v; want stdout %q", run, tc.stdout)
			}
		})
	}
}

func TestAliasRemove(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "DELETE" || r.URL.Path != "/api/workstation/services/web/aliases/ws-b/grafana" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()
	run := commandtest.New(t).Run("--dashboard", server.URL, "alias", "remove", "web", "--workstation", "ws-b", "--service", "grafana")
	if run.ExitCode != 0 || run.Stdout != "Removed the Alias to grafana on ws-b from web.\n" || run.Stderr != "" {
		t.Fatalf("unexpected result: %+v", run)
	}
}

func TestAliasCommandsRequireOneService(t *testing.T) {
	for _, operation := range []string{"add", "list", "remove"} {
		for _, services := range [][]string{{}, {"web", "extra"}} {
			args := append([]string{"alias", operation}, services...)
			if operation != "list" {
				args = append(args, "--workstation", "ws-b", "--service", "grafana")
			}
			run := commandtest.New(t).Run(args...)
			if run.ExitCode != 1 || run.Stdout != "" || run.Stderr == "" {
				t.Fatalf("unexpected result for %v: %+v", args, run)
			}
		}
	}
}

func TestAliasListAndRemovePrintRefusal(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"detail":"This Workstation has no Service web."}`))
	}))
	defer server.Close()
	for _, operation := range []string{"list", "remove"} {
		args := []string{"--dashboard", server.URL, "alias", operation, "web"}
		if operation == "remove" {
			args = append(args, "--workstation", "ws-b", "--service", "grafana")
		}
		run := commandtest.New(t).Run(args...)
		if run.ExitCode != 1 || run.Stdout != "" || run.Stderr != "This Workstation has no Service web.\n" {
			t.Fatalf("unexpected result: %+v", run)
		}
	}
}
