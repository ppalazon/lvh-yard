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

func TestRegisterContainer(t *testing.T) {
	for _, tc := range []struct {
		name, container, service  string
		flags                     []string
		list, own, stdout, stderr string
		port                      int
	}{
		{"inferred distinct port", "grafana", "grafana", nil, `[{"name":"grafana","ports":[{"containerPort":3000,"hostPort":19080},{"containerPort":3000,"hostPort":19081}]}]`, `{"name":"grafana","hostname":"grafana.lvh.me","publication":{"scope":"local"},"instances":[{"id":"bound"}]}`, "Registered grafana as grafana, published at grafana.lvh.me.\n", "", 3000},
		{"explicit port and service", "storefront", "shop", []string{"--port", "9000", "--service", "shop"}, "", `{"name":"shop","instances":[{"id":"bound"}]}`, "Registered storefront as shop. shop is not published.\n", "", 9000},
		{"cleaned name", "My_Web", "my-web", []string{"--port", "80"}, "", `{"name":"my-web","instances":[]}`, "Registered My_Web as my-web. my-web is not published. No container of that name runs now: my-web gets its Bound Instance once one starts.\n", "", 80},
		{"not running", "catalog", "catalog", []string{"--port", "80"}, "", `{"name":"catalog","hostname":"catalog.lvh.me","publication":{"scope":"local"},"instances":[]}`, "Registered catalog as catalog, published at catalog.lvh.me. No container of that name runs now: catalog gets its Bound Instance once one starts.\n", "", 80},
		{"warnings", "web-1", "web", []string{"--port", "80", "--service", "web"}, "", `{"name":"web","instances":[{"id":"bound"}],"warnings":[{"severity":"risk","message":"The Bound Instance uses an unpublished port."},{"severity":"error","message":"The Route has no address."}]}`, "Registered web-1 as web. web is not published.\n", "risk: The Bound Instance uses an unpublished port.\nerror: The Route has no address.\n", 80},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				switch r.Method + " " + r.URL.Path {
				case "GET /api/workstation/containers":
					if tc.list == "" {
						t.Error("explicit port must not fetch containers")
					}
					w.Write([]byte(tc.list))
				case "POST /api/workstation/services/" + tc.service + "/container-bindings":
					assertBindingBody(t, r, map[string]any{"container": tc.container, "port": float64(tc.port)})
					w.WriteHeader(http.StatusCreated)
					fmt.Fprintf(w, `{"id":"binding","container":%q,"port":%d,"instances":[]}`, tc.container, tc.port)
				case "GET /api/workstation/services/" + tc.service:
					w.Write([]byte(tc.own))
				default:
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
					w.WriteHeader(500)
				}
			}))
			defer server.Close()
			args := append([]string{"--dashboard", server.URL, "register", "container", tc.container}, tc.flags...)
			run := commandtest.New(t).Run(args...)
			if run.ExitCode != 0 || run.Stdout != tc.stdout || run.Stderr != tc.stderr {
				t.Fatalf("unexpected registration: %+v", run)
			}
		})
	}
}

func TestRegisterContainerNeedsAnUnambiguousPort(t *testing.T) {
	for _, tc := range []struct{ name, body, reason string }{
		{"missing", `[]`, "No port is known for the container storefront: give --port."},
		{"no ports", `[{"name":"storefront","ports":[]}]`, "No port is known for the container storefront: give --port."},
		{"several", `[{"name":"storefront","ports":[{"containerPort":9000},{"containerPort":80},{"containerPort":80}]}]`, "The container storefront has more than one port: give --port with one of 80, 9000."},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "GET" || r.URL.Path != "/api/workstation/containers" {
					t.Errorf("port inference changed a Service: %s %s", r.Method, r.URL)
				}
				w.Write([]byte(tc.body))
			}))
			defer server.Close()
			run := commandtest.New(t).Run("--dashboard", server.URL, "register", "container", "storefront")
			if run.ExitCode != 1 || run.Stdout != "" || run.Stderr != tc.reason+"\n" {
				t.Fatalf("unexpected port refusal: %+v", run)
			}
		})
	}
}

func assertBindingBody(t *testing.T, r *http.Request, want map[string]any) {
	t.Helper()
	var body map[string]any
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		t.Error(err)
	}
	if !reflect.DeepEqual(body, want) {
		t.Errorf("got binding %+v, want %+v", body, want)
	}
}

func TestRegisterCompose(t *testing.T) {
	for _, tc := range []struct {
		name, target, service, composeService, instances, summary string
		flags                                                     []string
	}{
		{"replicas", "shop/my_web", "my-web", "my_web", `["one","two"]`, "2 of its containers run now, so my-web has 2 Bound Instances.", nil},
		{"one", "shop/web", "web", "web", `["one"]`, "1 of its containers runs now, so web has 1 Bound Instance.", nil},
		{"service option", "shop/web", "store", "web", `[]`, "None of its containers runs now: store gets a Bound Instance for each one that starts.", []string{"--service", "store"}},
		{"clean and shorten", "shop/" + strings.Repeat("A", 62) + "-Extra", strings.Repeat("a", 62), strings.Repeat("A", 62) + "-Extra", `[]`, "None of its containers runs now: " + strings.Repeat("a", 62) + " gets a Bound Instance for each one that starts.", nil},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "POST" || r.URL.Path != "/api/workstation/services/"+tc.service+"/container-bindings" {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
				assertBindingBody(t, r, map[string]any{"composeProject": "shop", "composeService": tc.composeService, "port": float64(80)})
				w.WriteHeader(http.StatusCreated)
				fmt.Fprintf(w, `{"id":"binding","composeProject":"shop","composeService":%q,"port":80,"instances":%s}`, tc.composeService, tc.instances)
			}))
			defer server.Close()
			args := append([]string{"--dashboard", server.URL, "register", "compose", tc.target, "--port", "80"}, tc.flags...)
			run := commandtest.New(t).Run(args...)
			want := fmt.Sprintf("Bound %s to Compose service %s of project shop, port 80. %s\n", tc.service, tc.composeService, tc.summary)
			if run.ExitCode != 0 || run.Stdout != want || run.Stderr != "" {
				t.Fatalf("got %+v; want stdout %q", run, want)
			}
		})
	}
}

func TestContainerBindingsPermitSeveralServicesForTheSameTarget(t *testing.T) {
	for _, target := range []string{"container", "compose"} {
		t.Run(target, func(t *testing.T) {
			var bound []string
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				service := strings.Split(r.URL.Path, "/")[4]
				switch r.Method {
				case "POST":
					if target == "container" {
						assertBindingBody(t, r, map[string]any{"container": "web-1", "port": float64(80)})
					} else {
						assertBindingBody(t, r, map[string]any{"composeProject": "shop", "composeService": "web", "port": float64(80)})
					}
					bound = append(bound, service)
					w.WriteHeader(http.StatusCreated)
					w.Write([]byte(`{"id":"binding","composeProject":"shop","composeService":"web","port":80,"instances":["one","two"]}`))
				case "GET":
					fmt.Fprintf(w, `{"name":%q,"instances":[{"id":"one"}]}`, service)
				default:
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
			}))
			defer server.Close()
			for _, service := range []string{"web", "store"} {
				name := "web-1"
				if target == "compose" {
					name = "shop/web"
				}
				run := commandtest.New(t).Run("--dashboard", server.URL, "register", target, name, "--port", "80", "--service", service)
				if run.ExitCode != 0 {
					t.Fatalf("second Service refused: %+v", run)
				}
			}
			if !reflect.DeepEqual(bound, []string{"web", "store"}) {
				t.Fatalf("bindings went to the wrong Services: %v", bound)
			}
		})
	}
}

func TestContainerBindingDashboardRefusals(t *testing.T) {
	for _, tc := range []struct {
		name, command, target, service, port, body, reason string
		status                                             int
	}{
		{"named missing default", "container", "grafana", "grafana", "3000", `{"detail":"This Workstation has no Service grafana.","service":"grafana"}`, "lvh-yard service create grafana", 404},
		{"named missing override", "container", "grafana", "web", "3000", `{"detail":"This Workstation has no Service web.","service":"web"}`, "lvh-yard service create web", 404},
		{"compose missing", "compose", "shop/web", "web", "80", `{"detail":"This Workstation has no Service web.","service":"web"}`, "lvh-yard service create web", 404},
		{"invalid port reaches dashboard", "compose", "shop/web", "web", "0", `{"detail":"The container port must be between 1 and 65535."}`, "The container port must be between 1 and 65535.", 400},
		{"duplicate", "container", "web-1", "web", "80", `{"detail":"This Service already follows that container."}`, "This Service already follows that container.", 409},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "POST" || r.URL.Path != "/api/workstation/services/"+tc.service+"/container-bindings" {
					t.Errorf("unexpected request: %s %s", r.Method, r.URL)
				}
				w.WriteHeader(tc.status)
				w.Write([]byte(tc.body))
			}))
			defer server.Close()
			args := []string{"--dashboard", server.URL, "register", tc.command, tc.target, "--port", tc.port}
			if tc.name != "named missing default" {
				args = append(args, "--service", tc.service)
			}
			run := commandtest.New(t).Run(args...)
			if run.ExitCode != 1 || run.Stdout != "" || !strings.Contains(run.Stderr, tc.reason) || strings.Count(run.Stderr, "\n") != 1 {
				t.Fatalf("unexpected refusal: %+v", run)
			}
		})
	}
}

func TestContainerBindingUsage(t *testing.T) {
	h := commandtest.New(t)
	for _, args := range [][]string{
		{"register", "container"},
		{"register", "container", "web", "extra"},
		{"register", "compose", "--port", "80"},
		{"register", "compose", "shop/web"},
		{"register", "container", "web", "--port", "80", "--no-publish"},
	} {
		run := h.Run(args...)
		if run.ExitCode != 1 || run.Stderr == "" || run.Stdout != "" {
			t.Fatalf("invalid arguments accepted: %v: %+v", args, run)
		}
	}
	for _, target := range []string{"", "web", "/web", "shop/", "shop/web/extra", " /web", "shop/ "} {
		run := h.Run("register", "compose", target, "--port", "80")
		if run.ExitCode != 1 || !strings.Contains(run.Stderr, "<project>/<service>") || run.Stdout != "" {
			t.Fatalf("invalid Compose target accepted: %q: %+v", target, run)
		}
	}
	run := h.Run("register")
	for _, child := range []string{"static", "container", "compose"} {
		if run.ExitCode != 0 || !strings.Contains(run.Stdout, child) {
			t.Fatalf("missing register child %s: %+v", child, run)
		}
	}
}
