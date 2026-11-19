package command_test

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestPublish(t *testing.T) {
	for _, tc := range []struct {
		name, body, answer string
		args               []string
		stdout, stderr     string
	}{
		{
			name: "new local publication", args: []string{"web"},
			answer: `{"workstation":"ws-a","name":"web","state":"unhealthy","hostname":"web.lvh.me","publication":{"scope":"local","accessLog":false}}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Local\n  State: unhealthy\n",
		},
		{
			name: "omitted scope keeps yard", args: []string{"web"},
			answer: `{"workstation":"ws-a","name":"web","state":"healthy","hostname":"web.lvh.me","publication":{"scope":"yard","accessLog":true}}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Yard\n  Other Workstations: web.ws-a.<base-domain>\n  State: healthy\n  Access log: on\n",
		},
		{
			name: "yard scope and warnings", args: []string{"web", "--scope", "yard"}, body: `{"scope":"yard"}`,
			answer: `{"workstation":"ws-a","name":"web","state":"healthy","hostname":"web.lvh.me","publication":{"scope":"yard","accessLog":false},"warnings":[{"severity":"error","topic":"Unknown profile","message":"The Route Profile team-login does not exist."},{"severity":"risk","topic":"Loopback","message":"The Instance at 127.0.0.1:8080 is on a loopback address."}]}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Yard\n  Other Workstations: web.ws-a.<base-domain>\n  State: healthy\n",
			stderr: "error: [Unknown profile] The Route Profile team-login does not exist.\nrisk: [Loopback] The Instance at 127.0.0.1:8080 is on a loopback address.\n",
		},
		{
			name: "profiles keep order", args: []string{"web", "--profile", "basic-auth", "--profile", "cors"}, body: `{"profiles":["basic-auth","cors"]}`,
			answer: `{"workstation":"ws-a","name":"web","state":"healthy","hostname":"web.lvh.me","publication":{"scope":"local","accessLog":false},"profiles":["basic-auth","cors"],"warnings":[{"severity":"risk","topic":"Authentication","message":"The Route Profile basic-auth asks who the user is only on the hostname."}]}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Local\n  State: healthy\n  Route Profiles: basic-auth, cors\n",
			stderr: "risk: [Authentication] The Route Profile basic-auth asks who the user is only on the hostname.\n",
		},
		{
			name: "remove profiles", args: []string{"web", "--no-profiles"}, body: `{"profiles":[]}`,
			answer: `{"name":"web","state":"healthy","hostname":"web.lvh.me","publication":{"scope":"local","accessLog":false}}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Local\n  State: healthy\n",
		},
		{
			name: "access log on", args: []string{"web", "--access-log", "on"}, body: `{"accessLog":true}`,
			answer: `{"name":"web","state":"healthy","hostname":"web.lvh.me","publication":{"scope":"local","accessLog":true}}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Local\n  State: healthy\n  Access log: on\n",
		},
		{
			name: "access log off", args: []string{"web", "--scope", "local", "--access-log", "off"}, body: `{"scope":"local","accessLog":false}`,
			answer: `{"name":"web","state":"healthy","hostname":"web.lvh.me","publication":{"scope":"local","accessLog":false}}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Local\n  State: healthy\n",
		},
		{
			name: "mutual tls on", args: []string{"web", "--mutual-tls", "on"}, body: `{"mutualTls":true}`,
			answer: `{"name":"web","state":"healthy","hostname":"web.lvh.me","publication":{"scope":"local","accessLog":false,"mutualTls":true}}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Local\n  State: healthy\n  Mutual TLS: on\n",
		},
		{
			name: "mutual tls off", args: []string{"web", "--mutual-tls", "off"}, body: `{"mutualTls":false}`,
			answer: `{"name":"web","state":"healthy","hostname":"web.lvh.me","publication":{"scope":"local","accessLog":false,"mutualTls":false}}`,
			stdout: "Published web at web.lvh.me.\n  Scope: Local\n  State: healthy\n",
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			called := false
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				called = true
				if r.Method != "PUT" || r.URL.Path != "/api/workstation/services/web/publication" {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
				data, err := io.ReadAll(r.Body)
				if err != nil {
					t.Error(err)
				}
				if tc.body == "" {
					if len(data) != 0 {
						t.Errorf("omitted settings sent a body: %s", data)
					}
				} else {
					var got, want any
					if err := json.Unmarshal(data, &got); err != nil {
						t.Error(err)
					}
					if err := json.Unmarshal([]byte(tc.body), &want); err != nil {
						t.Fatal(err)
					}
					if !reflect.DeepEqual(got, want) {
						t.Errorf("body: %s; want %s", data, tc.body)
					}
				}
				w.Write([]byte(tc.answer))
			}))
			defer server.Close()
			args := append([]string{"--dashboard", server.URL, "publish"}, tc.args...)
			run := commandtest.New(t).Run(args...)
			if !called || run.ExitCode != 0 || run.Stdout != tc.stdout || run.Stderr != tc.stderr {
				t.Fatalf("unexpected result: %+v; called: %v", run, called)
			}
		})
	}
}

func TestPublicationRejectsUsageWithoutRequests(t *testing.T) {
	for _, args := range [][]string{
		{"publish", "web", "--profile", "cors", "--no-profiles"},
		{"publish", "web", "--scope", "world"},
		{"publish", "web", "--access-log", "maybe"},
		{"publish", "web", "--mutual-tls", "maybe"},
		{"publish", "web", "--priority", "bound,static"},
		{"publish"},
		{"publish", "web", "extra"},
		{"unpublish"},
		{"unpublish", "web", "extra"},
	} {
		t.Run(strings.Join(args, " "), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				t.Errorf("invalid arguments sent a request: %s %s", r.Method, r.URL)
			}))
			defer server.Close()
			run := commandtest.New(t).Run(append([]string{"--dashboard", server.URL}, args...)...)
			if run.ExitCode != 1 || run.Stdout != "" || run.Stderr == "" {
				t.Fatalf("unexpected result: %+v", run)
			}
		})
	}
}

func TestUnpublishRemovesOnlyPublication(t *testing.T) {
	called := false
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		if r.Method != "DELETE" || r.URL.Path != "/api/workstation/services/web/publication" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()
	run := commandtest.New(t).Run("--dashboard", server.URL, "unpublish", "web")
	if !called || run.ExitCode != 0 || run.Stdout != "Unpublished web. Its Instances stay registered.\n" || run.Stderr != "" {
		t.Fatalf("unexpected result: %+v; called: %v", run, called)
	}
}

func TestPublicationUnknownService(t *testing.T) {
	for _, name := range []string{"publish", "unpublish"} {
		t.Run(name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(http.StatusNotFound)
				w.Write([]byte(`{"detail":"This Workstation has no Service wbe."}`))
			}))
			defer server.Close()
			run := commandtest.New(t).Run("--dashboard", server.URL, name, "wbe")
			if run.ExitCode != 1 || run.Stdout != "" || run.Stderr != "This Workstation has no Service wbe.\n" {
				t.Fatalf("unexpected result: %+v", run)
			}
		})
	}
}
