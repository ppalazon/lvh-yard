package command_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

const catalogSourcesSnapshot = `{"home":{"name":"ws-a","services":[
{"name":"fixed","state":"healthy","hostname":"fixed.lvh.me","publication":{"scope":"local"},"instances":[{"id":"s1"}],"sources":[{"id":"s1","kind":"static","instances":["s1"]}],"routing":{"source":"s1","instances":["s1"]}},
{"name":"box","state":"healthy","hostname":"box.lvh.me","instances":[{"id":"b1"}],"containerBindings":[{"id":"web-1:80","container":"web-1","port":80}],"sources":[{"id":"web-1:80","kind":"bound","instances":["b1"]}],"routing":{"source":"web-1:80","instances":["b1"]}},
{"name":"stack","state":"healthy","hostname":"stack.lvh.me","instances":[{"id":"c1"}],"containerBindings":[{"id":"shop:web:80","composeProject":"shop","composeService":"web","port":80}],"sources":[{"id":"shop:web:80","kind":"bound","instances":["c1"]}],"routing":{"source":"shop:web:80","instances":["c1"]}},
{"name":"linked","state":"healthy","hostname":"linked.lvh.me","instances":[],"sources":[{"id":"ws-b/api","kind":"alias","instances":["a1"]}],"routing":{"source":"ws-b/api","instances":["a1"]}},
{"name":"other","state":"healthy","hostname":"other.lvh.me","instances":[{"id":"f1"}],"sources":[{"id":"f1","kind":"foreign","instances":["f1"]}],"routing":{"source":"f1","instances":["f1"]}},
{"name":"shared","state":"healthy","hostname":"shared.lvh.me","publication":{"scope":"yard"},"instances":[{"id":"s2"},{"id":"c2"}],"containerBindings":[{"id":"shop:db:5432","composeProject":"shop","composeService":"db","port":5432}],"sources":[{"id":"s2","kind":"static","instances":["s2"]},{"id":"shop:db:5432","kind":"bound","instances":["c2"]},{"id":"unrouted","kind":"static","instances":["gone"]}],"routing":{"off":true,"instances":["s2","c2"]}},
{"name":"quiet","state":"unpublished","instances":[{"id":"q1"}],"sources":[{"id":"q1","kind":"static","instances":["q1"]}]}]},"remotes":[]}`

func TestCatalogShowsTheSourcesTheRouteUses(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(catalogSourcesSnapshot))
	}))
	defer server.Close()
	want := "ws-a (this Workstation)\n" +
		"  fixed   healthy      local  fixed.lvh.me   0/1  [static]\n" +
		"  box     healthy             box.lvh.me     0/1  [docker]\n" +
		"  stack   healthy             stack.lvh.me   0/1  [compose]\n" +
		"  linked  healthy             linked.lvh.me  No Instances  [alias]\n" +
		"  other   healthy             other.lvh.me   0/1  [foreign]\n" +
		"  shared  healthy      yard   shared.lvh.me  0/2  [static] [compose]\n" +
		"  quiet   unpublished                        0/1\n"
	result := commandtest.New(t).Run("--dashboard", server.URL, "catalog")
	if result.ExitCode != 0 || result.Stderr != "" || result.Stdout != want {
		t.Fatalf("got %+v; want output %q", result, want)
	}
}
