package command_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

const catalogSnapshot = `{"home":{"name":"ws-a","services":[
{"name":"web","state":"degraded","hostname":"web.lvh.me","publication":{"scope":"local"},"instances":[{"id":"good","host":"10.1.2.3","port":8080,"health":"passing","outOfRange":true},{"id":"bad","host":"127.0.0.1","port":8081,"health":"critical"}],"routing":{"instances":["good"]},"warnings":[{"severity":"risk","message":"Hidden warning."},{"severity":"error","message":"Missing Route Profile."}]},
{"name":"worker","state":"unpublished","instances":[{"id":"worker-critical","host":"10.1.2.3","port":8083,"health":"critical"},{"id":"worker-warning","host":"10.1.2.3","port":8084,"health":"warning"}]},
{"name":"waiting","state":"unavailable","instances":[]}]},"remotes":[
{"name":"ws-b","services":[{"name":"database","state":"healthy","hostname":"db.lvh.me","publication":{"scope":"yard"},"instances":[{"id":"db-first","host":"10.1.2.4","port":5432,"health":"passing"},{"id":"db-second","host":"10.1.2.4","port":5433,"health":"passing"}],"routing":{"instances":["db-first","db-second"]}},{"name":"cache","state":"unavailable","empty":true,"instances":[]}]},
{"name":"ws-c","unreachable":true,"services":[]},{"name":"ws-d","services":[]}]}`

func TestCatalogViewsAndFilters(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.URL.RequestURI() != "/api/yard" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
		w.Write([]byte(catalogSnapshot))
	}))
	defer server.Close()
	for _, tc := range []struct {
		name string
		args []string
		want string
	}{
		{"summary", nil, "ws-a (this Workstation)\n  web       degraded     local  web.lvh.me  1/2\n  worker    unpublished                     0/2\n  waiting   unavailable                     No Instances\nws-b\n  database  healthy      yard   db.lvh.me   2/2\nws-c: Workstation unreachable\nws-d: no Services\nTo see why, run:\n  lvh-yard ws compose logs consul\n"},
		{"every scope", []string{"--scope", "every"}, "ws-a (this Workstation)\n  web       degraded     local  web.lvh.me  1/2\n  worker    unpublished                     0/2\n  waiting   unavailable                     No Instances\nws-b\n  database  healthy      yard   db.lvh.me   2/2\n  cache     unavailable                     Empty\nws-c: Workstation unreachable\nws-d: no Services\nTo see why, run:\n  lvh-yard ws compose logs consul\n"},
		{"local scope", []string{"--scope", "local"}, "ws-a (this Workstation)\n  web  degraded  local  web.lvh.me  1/2\nws-b: no Services\nws-c: Workstation unreachable\nws-d: no Services\nTo see why, run:\n  lvh-yard ws compose logs consul\n"},
		{"yard scope", []string{"--scope", "yard"}, "ws-a (this Workstation): no Services\nws-b\n  database  healthy  yard  db.lvh.me  2/2\nws-c: Workstation unreachable\nws-d: no Services\nTo see why, run:\n  lvh-yard ws compose logs consul\n"},
		{"filters", []string{"--workstation", "ws-a", "--state", "degraded"}, "ws-a (this Workstation)\n  web  degraded  local  web.lvh.me  1/2\n"},
		{"unreachable", []string{"--workstation", "ws-c"}, "ws-c: Workstation unreachable\nTo see why, run:\n  lvh-yard ws compose logs consul\n"},
		{"state", []string{"--state", "unpublished"}, "ws-a (this Workstation)\n  worker  unpublished        0/2\nws-b: no Services\nws-c: Workstation unreachable\nws-d: no Services\nTo see why, run:\n  lvh-yard ws compose logs consul\n"},
		{"details", []string{"--details", "--workstation", "ws-a", "--state", "degraded"}, "ws-a (this Workstation)\n  web: degraded at web.lvh.me\n    10.1.2.3:8080  passing   good  routed  out of range\n    127.0.0.1:8081  critical  bad\n    risk: Hidden warning.\n    error: Missing Route Profile.\n"},
		{"remote details", []string{"--details", "--workstation", "ws-b"}, "ws-b\n  database: healthy at db.lvh.me\n    10.1.2.4:5432  passing   db-first  routed\n    10.1.2.4:5433  passing   db-second  routed\n"},
		{"remote details of every scope", []string{"--details", "--workstation", "ws-b", "--scope", "every"}, "ws-b\n  database: healthy at db.lvh.me\n    10.1.2.4:5432  passing   db-first  routed\n    10.1.2.4:5433  passing   db-second  routed\n  cache: unavailable\n"},
		{"filtered details", []string{"--details", "--state", "unpublished"}, "ws-a (this Workstation)\n  worker: unpublished\n    10.1.2.3:8083  critical  worker-critical\n    10.1.2.3:8084  warning   worker-warning\nws-b: no Services\nws-c: Workstation unreachable\nws-d: no Services\nTo see why, run:\n  lvh-yard ws compose logs consul\n"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			h := commandtest.New(t)
			h.Process.Env["YARD_DASHBOARD_URL"] = server.URL
			result := h.Run(append([]string{"catalog"}, tc.args...)...)
			if result.ExitCode != 0 || result.Stderr != "" || result.Stdout != tc.want {
				t.Fatalf("got %+v; want output %q", result, tc.want)
			}
		})
	}
	for _, details := range []bool{false, true} {
		h := commandtest.New(t)
		h.Process.Env["YARD_DASHBOARD_URL"] = server.URL
		args := []string{"catalog", "--workstation", "ws-typo"}
		if details {
			args = append(args, "--details")
		}
		result := h.Run(args...)
		if result.ExitCode != 1 || result.Stdout != "" || !strings.Contains(result.Stderr, "The Yard has no Workstation ws-typo.") {
			t.Fatalf("unexpected missing Workstation result: %+v", result)
		}
	}
}

func TestCatalogRejectsUnknownStateBeforeCallingDashboard(t *testing.T) {
	result := commandtest.New(t).Run("catalog", "--state", "broken")
	if result.ExitCode != 1 || result.Stdout != "" {
		t.Fatalf("unexpected result: %+v", result)
	}
	for _, state := range []string{"broken", "unpublished", "unavailable", "unhealthy", "degraded", "healthy"} {
		if !strings.Contains(result.Stderr, state) {
			t.Errorf("missing %q in %q", state, result.Stderr)
		}
	}
}

func TestCatalogRejectsUnknownScopeBeforeCallingDashboard(t *testing.T) {
	result := commandtest.New(t).Run("catalog", "--scope", "world")
	if result.ExitCode != 1 || result.Stdout != "" {
		t.Fatalf("unexpected result: %+v", result)
	}
	for _, scope := range []string{"in-range", "every", "local", "yard"} {
		if !strings.Contains(result.Stderr, scope) {
			t.Errorf("missing %q in %q", scope, result.Stderr)
		}
	}
}
