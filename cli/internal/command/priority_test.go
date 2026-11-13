package command_test

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

const priorityStaticSource = `{"id":"1a2b","kind":"static","instances":["1a2b"]}`
const priorityBoundSource = `{"id":"web-1:80","kind":"bound","instances":["web:web-1:80"]}`
const priorityService = `{"name":"web","instancePriority":%t,"sources":[%s],"routing":{"source":"1a2b"},"containerBindings":[{"id":"web-1:80","container":"web-1","port":80}],"instances":[{"id":"1a2b","host":"10.1.2.3","port":3000}]}`

func TestPriorityShowsAndSetsSourceOrder(t *testing.T) {
	for _, tc := range []struct {
		name, body, sources string
		args                []string
		on                  bool
		lines               []string
	}{
		{
			name: "show", on: true, sources: priorityStaticSource + "," + priorityBoundSource,
			lines: []string{"Instance Priority of web: on. The Route uses the first Instance Source with a passing Instance.", "  1. 1a2b  Static Instance 10.1.2.3:3000  (in use)", "  2. web-1:80  Container Binding to container web-1, port 80"},
		},
		{
			name: "set order", args: []string{"web-1:80", "1a2b"}, body: `{"order":["web-1:80","1a2b"]}`, on: true, sources: priorityBoundSource + "," + priorityStaticSource,
			lines: []string{"Instance Priority of web: on. The Route uses the first Instance Source with a passing Instance.", "  1. web-1:80  Container Binding to container web-1, port 80", "  2. 1a2b  Static Instance 10.1.2.3:3000  (in use)"},
		},
		{
			name: "off keeps order", args: []string{"--off"}, body: `{"on":false}`, sources: priorityStaticSource + "," + priorityBoundSource,
			lines: []string{"Instance Priority of web: off. Every passing Instance of every Instance Source shares the Route.", "  1. 1a2b  Static Instance 10.1.2.3:3000  (in use)", "  2. web-1:80  Container Binding to container web-1, port 80"},
		},
		{
			name: "on and order", args: []string{"web-1:80", "--on"}, body: `{"on":true,"order":["web-1:80"]}`, on: true, sources: priorityBoundSource + "," + priorityStaticSource,
			lines: []string{"Instance Priority of web: on. The Route uses the first Instance Source with a passing Instance.", "  1. web-1:80  Container Binding to container web-1, port 80", "  2. 1a2b  Static Instance 10.1.2.3:3000  (in use)"},
		},
		{
			name: "no sources", on: true,
			lines: []string{"Instance Priority of web: on. The Route uses the first Instance Source with a passing Instance.", "  No Instance Sources yet."},
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			updated, read := false, false
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				switch {
				case r.Method == "PUT" && r.URL.Path == "/api/workstation/services/web/instance-priority":
					updated = true
					var got, want any
					if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
						t.Error(err)
					}
					if tc.body == "" {
						t.Error("show priority changed it")
					} else if err := json.Unmarshal([]byte(tc.body), &want); err != nil {
						t.Fatal(err)
					}
					if !reflect.DeepEqual(got, want) {
						t.Errorf("body: %v; want %s", got, tc.body)
					}
					w.Write([]byte(`{"on":true,"sources":[]}`))
				case r.Method == "GET" && r.URL.Path == "/api/workstation/services/web":
					read = true
					if tc.body != "" && !updated {
						t.Error("read the Service before setting its priority")
					}
					fmt.Fprintf(w, priorityService, tc.on, tc.sources)
				default:
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
					w.WriteHeader(http.StatusNotFound)
				}
			}))
			defer server.Close()
			args := append([]string{"--dashboard", server.URL, "priority", "web"}, tc.args...)
			run := commandtest.New(t).Run(args...)
			if !read || updated != (tc.body != "") || run.ExitCode != 0 || run.Stdout != strings.Join(tc.lines, "\n")+"\n" || run.Stderr != "" {
				t.Fatalf("unexpected result: %+v; read: %v; updated: %v", run, read, updated)
			}
		})
	}
}

func TestPriorityDescribesEverySource(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(`{"name":"web","instancePriority":true,"sources":[{"id":"compose:80","kind":"bound"},{"id":"gone:80","kind":"bound"},{"id":"ws-b/api","kind":"alias"},{"id":"foreign-id","kind":"foreign","instances":["foreign-instance"]},{"id":"future-id","kind":"future"}],"containerBindings":[{"id":"compose:80","composeProject":"shop","composeService":"web","port":80}],"instances":[{"id":"foreign-instance","host":"10.1.2.4","port":8080}]}`))
	}))
	defer server.Close()
	run := commandtest.New(t).Run("--dashboard", server.URL, "priority", "web")
	want := "Instance Priority of web: on. The Route uses the first Instance Source with a passing Instance.\n" +
		"  1. compose:80  Container Binding to Compose service web of project shop, port 80\n" +
		"  2. gone:80  Container Binding\n" +
		"  3. ws-b/api  Alias to api on ws-b\n" +
		"  4. foreign-id  Foreign Instance 10.1.2.4:8080\n" +
		"  5. future-id  future\n"
	if run.ExitCode != 0 || run.Stdout != want || run.Stderr != "" {
		t.Fatalf("unexpected result: %+v", run)
	}
}

func TestPriorityRefusesInvalidUsage(t *testing.T) {
	for _, args := range [][]string{{"priority"}, {"priority", "web", "--on", "--off"}} {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			t.Errorf("invalid arguments sent a request: %s %s", r.Method, r.URL)
		}))
		run := commandtest.New(t).Run(append([]string{"--dashboard", server.URL}, args...)...)
		server.Close()
		if run.ExitCode != 1 || run.Stdout != "" || run.Stderr == "" {
			t.Fatalf("unexpected result: %+v", run)
		}
	}
}

func TestPriorityShowsDashboardRefusals(t *testing.T) {
	for _, tc := range []struct {
		name, detail string
		status       int
		args         []string
	}{
		{"unknown source", "web has no Instance Source nothing. Its Instance Sources are: 1a2b.", 400, []string{"nothing"}},
		{"duplicate source", "web names Instance Source 1a2b twice.", 400, []string{"1a2b", "1a2b"}},
		{"unknown service", "This Workstation has no Service web.", 404, nil},
		{"remote service", "Only its Home Workstation can change it.", 405, []string{"--off"}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if len(tc.args) != 0 && r.Method != "PUT" {
					t.Errorf("failed update read the Service: %s %s", r.Method, r.URL)
				}
				w.WriteHeader(tc.status)
				_ = json.NewEncoder(w).Encode(map[string]string{"detail": tc.detail})
			}))
			defer server.Close()
			run := commandtest.New(t).Run(append([]string{"--dashboard", server.URL, "priority", "web"}, tc.args...)...)
			if run.ExitCode != 1 || run.Stdout != "" || run.Stderr != tc.detail+"\n" {
				t.Fatalf("unexpected result: %+v", run)
			}
		})
	}
}
