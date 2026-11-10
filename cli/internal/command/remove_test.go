package command_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

const removalService = `{"name":"web","instances":[{"id":"one","host":"10.1.2.3","port":8080},{"id":"two","host":"10.1.2.4","port":8080},{"id":"three","host":"::1","port":8081}],"containerBindings":[{"id":"web-1:80","container":"web-1","port":80},{"id":"web-1:443","container":"web-1","port":443},{"id":"web-2:80","container":"web-2","port":80},{"id":"compose:shop:web:80","composeProject":"shop","composeService":"web","port":80},{"id":"compose:shop:web:443","composeProject":"shop","composeService":"web","port":443},{"id":"compose:other:web:80","composeProject":"other","composeService":"web","port":80}]}`

func TestRemoveSelectsExactlyOneSource(t *testing.T) {
	for _, tc := range []struct {
		name             string
		args             []string
		kind, id, output string
		lookup           bool
	}{
		{"id", []string{"--id", "one/two:80"}, "instances", "one/two:80", "Removed Instance one/two:80 of web.", false},
		{"port", []string{"--port", "8081"}, "instances", "three", "Removed web at [::1]:8081.", true},
		{"host", []string{"--port", "8080", "--host", "10.1.2.4"}, "instances", "two", "Removed web at 10.1.2.4:8080.", true},
		{"container", []string{"--container", "web-2"}, "container-bindings", "web-2:80", "to container web-2, port 80.", true},
		{"container port", []string{"--container", "web-1", "--port", "443"}, "container-bindings", "web-1:443", "to container web-1, port 443.", true},
		{"compose", []string{"--compose-project", "other", "--compose-service", "web"}, "container-bindings", "compose:other:web:80", "to Compose service web of project other, port 80.", true},
		{"compose port", []string{"--compose-project", "shop", "--compose-service", "web", "--port", "443"}, "container-bindings", "compose:shop:web:443", "to Compose service web of project shop, port 443.", true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			gets, deletes := 0, 0
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method == "GET" && r.URL.Path == "/api/workstation/services/web" {
					gets++
					w.Write([]byte(removalService))
				} else if r.Method == "DELETE" && r.URL.Path == "/api/workstation/services/web/"+tc.kind+"/"+tc.id && len(strings.Split(r.URL.EscapedPath(), "/")) == 7 && r.URL.RawQuery == "" {
					deletes++
					w.WriteHeader(204)
				} else {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL.EscapedPath())
					w.WriteHeader(500)
				}
			}))
			defer server.Close()
			result := commandtest.New(t).Run(append([]string{"--dashboard", server.URL, "remove", "web"}, tc.args...)...)
			wantGets := 0
			if tc.lookup {
				wantGets = 1
			}
			if result.ExitCode != 0 || result.Stderr != "" || !strings.Contains(result.Stdout, tc.output) || deletes != 1 || gets != wantGets {
				t.Fatalf("result=%+v, gets=%d, deletes=%d", result, gets, deletes)
			}
		})
	}
}

func TestRemoveRefusesAmbiguousOrMissingSources(t *testing.T) {
	for _, tc := range []struct {
		args    []string
		reasons []string
	}{
		{[]string{"--port", "8080"}, []string{"10.1.2.3", "10.1.2.4", "--host"}},
		{[]string{"--port", "9090"}, []string{"has no Instance at port 9090"}},
		{[]string{"--port", "8080", "--host", "10.0.0.9"}, []string{"has no Instance at 10.0.0.9:8080"}},
		{[]string{"--container", "web-1"}, []string{"80, 443", "--port"}},
		{[]string{"--container", "missing"}, []string{"has no Container Binding to container missing"}},
		{[]string{"--container", "web-1", "--port", "90"}, []string{"has no Container Binding", "port 90"}},
		{[]string{"--compose-project", "shop", "--compose-service", "web"}, []string{"80, 443", "--port"}},
		{[]string{"--compose-project", "missing", "--compose-service", "web"}, []string{"has no Container Binding to Compose service web of project missing"}},
	} {
		t.Run(strings.Join(tc.args, " "), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "GET" {
					t.Errorf("refused selection mutated a source: %s %s", r.Method, r.URL)
				}
				w.Write([]byte(removalService))
			}))
			defer server.Close()
			result := commandtest.New(t).Run(append([]string{"--dashboard", server.URL, "remove", "web"}, tc.args...)...)
			if result.ExitCode != 1 || result.Stdout != "" {
				t.Fatalf("unexpected result: %+v", result)
			}
			for _, reason := range tc.reasons {
				if !strings.Contains(result.Stderr, reason) {
					t.Errorf("error %q lacks %q", result.Stderr, reason)
				}
			}
		})
	}
}

func TestRemoveRejectsInvalidSelectorsBeforeCallingDashboard(t *testing.T) {
	for _, args := range [][]string{
		{}, {"--host", "10.1.2.3"}, {"--id", "one", "--port", "80"},
		{"--id", "one", "--container", "web"}, {"--id", "one", "--host", "host"},
		{"--id", "one", "--compose-project", "shop", "--compose-service", "web"},
		{"--container", "web", "--host", "host"},
		{"--container", "web", "--compose-project", "shop"},
		{"--container", "web", "--compose-service", "web"},
		{"--compose-project", "shop"}, {"--compose-service", "web"},
	} {
		t.Run(strings.Join(args, " "), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				t.Errorf("invalid selector called dashboard: %s %s", r.Method, r.URL)
				w.WriteHeader(500)
			}))
			defer server.Close()
			result := commandtest.New(t).Run(append([]string{"--dashboard", server.URL, "remove", "web"}, args...)...)
			if result.ExitCode != 1 || result.Stdout != "" || result.Stderr == "" {
				t.Fatalf("invalid selector accepted: %+v", result)
			}
		})
	}
}
