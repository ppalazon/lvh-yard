package command_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestReachability(t *testing.T) {
	for _, tc := range []struct {
		name, body string
		remote     bool
		code       int
		lines      []string
	}{
		{"passing", `{"passed":true,"instances":[{"address":"10.1.2.3:8080","reached":true}],"routes":[{"hostname":"web.lvh.me","reached":true,"status":200}]}`, false, 0, []string{"  ok    Instance 10.1.2.3:8080", "  ok    Route web.lvh.me: answered 200", "Every check passed."}},
		{"failing", `{"passed":false,"instances":[{"address":"10.1.2.3:8080","reached":true},{"address":"10.1.2.3:8081","error":"Connection refused"}],"routes":[{"hostname":"web.lvh.me","error":"Traefik answered 502: no Instance answered."}]}`, false, 1, []string{"  ok    Instance 10.1.2.3:8080", "  FAIL  Instance 10.1.2.3:8081: Connection refused", "  FAIL  Route web.lvh.me: Traefik answered 502: no Instance answered.", "2 of 3 checks failed."}},
		{"no route", `{"passed":true,"instances":[{"address":"192.168.1.20:3000","reached":true}],"routes":[],"noRoute":"Published with Local Scope: only ws-b routes it, so only its Instances are tested from here."}`, true, 0, []string{"  ok    Instance 192.168.1.20:3000", "Published with Local Scope: only ws-b routes it, so only its Instances are tested from here.", "Every check passed."}},
		{"out of range", `{"passed":true,"instances":[{"address":"192.168.1.20:3000","reached":true},{"address":"127.0.0.1:3000","outOfRange":true}],"routes":[{"hostname":"web.ws-b.lvh.me","reached":true,"status":200}]}`, true, 0, []string{"  ok    Instance 192.168.1.20:3000", "  skip  Instance 127.0.0.1:3000: out of range", "  ok    Route web.ws-b.lvh.me: answered 200", "Every check passed. 1 skipped."}},
		{"priority", `{"passed":true,"instances":[{"address":"10.1.2.3:8080","reached":true,"routed":true},{"address":"172.18.0.5:80","reached":true}],"routes":[],"routing":{"kind":"static","source":"7b1c"}}`, false, 0, []string{"  ok    Instance 10.1.2.3:8080 (in the Route)", "  ok    Instance 172.18.0.5:80 (not in the Route now)", "The Route uses the static Instance Source 7b1c now.", "Every check passed."}},
		{"priority off", `{"passed":true,"instances":[{"address":"10.1.2.3:8080","reached":true,"routed":true}],"routes":[],"routing":{"off":true}}`, false, 0, []string{"  ok    Instance 10.1.2.3:8080 (in the Route)", "The Instance Priority is off, so the Route uses every passing Instance, or every Instance in range if none passes.", "Every check passed."}},
		{"alias", `{"passed":false,"instances":[{"address":"10.1.2.3:8080","error":"Connection refused"}],"routes":[{"hostname":"web.lvh.me","reached":true,"status":200}],"routing":{"kind":"alias"},"alias":{"service":"web","workstation":"ws-b"}}`, false, 1, []string{"  FAIL  Instance 10.1.2.3:8080 (not in the Route now): Connection refused", "  ok    Route web.lvh.me: answered 200", "The Route sends requests to the Alias's Remote Service: web on ws-b.", "1 of 2 checks failed."}},
		{"none passing", `{"passed":false,"instances":[{"address":"10.1.2.3:8080"}],"routes":[],"routing":{}}`, false, 1, []string{"  FAIL  Instance 10.1.2.3:8080 (not in the Route now)", "No Instance passes, so the Route uses every Instance in range.", "1 of 1 check failed."}},
		{"alias without details", `{"passed":true,"instances":[],"routes":[],"routing":{"kind":"alias"}}`, false, 0, []string{"The Route sends requests to the Alias's Remote Service.", "Every check passed."}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				path := "/api/workstation/services/web/reachability"
				if tc.remote {
					path = "/api/services/ws-b/web/reachability"
				}
				if r.Method != "GET" || r.URL.Path != path {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
				w.Header().Set("Content-Type", "application/json")
				w.Write([]byte(`{"workstation":"ws-b","service":"web",` + tc.body[1:]))
			}))
			defer server.Close()
			h := commandtest.New(t)
			args := []string{"--dashboard", server.URL, "test", "web"}
			if tc.remote {
				args = append(args, "--workstation", "ws-b")
			}
			run := h.Run(args...)
			want := "Reachability of web on ws-b, from this Workstation:\n" + strings.Join(tc.lines, "\n") + "\n"
			if run.ExitCode != tc.code || run.Stdout != want || run.Stderr != "" {
				t.Fatalf("got %+v; want exit %d, stdout %q", run, tc.code, want)
			}
		})
	}
}

func TestReachabilityUnknownService(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"detail":"This Workstation has no Service wbe."}`))
	}))
	defer server.Close()
	run := commandtest.New(t).Run("--dashboard", server.URL, "test", "wbe")
	if run.ExitCode != 1 || run.Stdout != "" || run.Stderr != "This Workstation has no Service wbe.\n" {
		t.Fatalf("unexpected result: %+v", run)
	}
}

func TestReachabilityRequiresOneService(t *testing.T) {
	for _, args := range [][]string{{"test"}, {"test", "web", "extra"}} {
		run := commandtest.New(t).Run(args...)
		if run.ExitCode != 1 || run.Stderr == "" {
			t.Fatalf("unexpected result: %+v", run)
		}
	}
}
