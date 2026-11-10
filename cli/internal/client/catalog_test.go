package client_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestCatalogOperations(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.Header.Get("Accept") != "application/json" {
			t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
		}
		switch r.URL.Path {
		case "/prefix/api/yard":
			w.Write([]byte(`{"home":{"name":"home","services":[]},"remotes":[{"name":"away","unreachable":true,"services":[]}]}`))
		case "/prefix/api/services":
			if r.URL.Query().Get("workstation") != "ws /+?" || r.URL.Query().Get("state") != "healthy" {
				t.Errorf("unexpected filters: %s", r.URL.RawQuery)
			}
			w.Write([]byte(`[{"name":"api","state":"healthy"}]`))
		case "/prefix/api/services/ws /+?/api/#":
			if len(strings.Split(r.URL.EscapedPath(), "/")) != 6 || r.URL.RawQuery != "" {
				t.Errorf("names escaped into another path or query: %s", r.URL)
			}
			w.Write([]byte(`{"name":"api/#","sources":[{"id":"second","kind":"alias","instances":["b"]},{"id":"first","kind":"static","instances":["a"]}],"instances":[{"id":"a","host":"127.0.0.1","port":80,"health":"passing"}],"routing":{"source":"first","instances":["a"]},"publication":{"scope":"yard","accessLog":true},"warnings":[{"severity":"risk","message":"Check this."}]}`))
		default:
			t.Errorf("unexpected path: %s", r.URL.EscapedPath())
			http.NotFound(w, r)
		}
	}))
	defer server.Close()
	c := client.New(server.URL + "/prefix")
	yard, err := c.Yard()
	if err != nil || yard.Home.Name != "home" || len(yard.Remotes) != 1 || !yard.Remotes[0].Unreachable {
		t.Fatalf("yard: %+v, %v", yard, err)
	}
	services, err := c.YardServices("ws /+?", "healthy")
	if err != nil || len(services) != 1 || services[0].Name != "api" {
		t.Fatalf("services: %+v, %v", services, err)
	}
	service, err := c.YardService("ws /+?", "api/#")
	if err != nil {
		t.Fatal(err)
	}
	if service.Name != "api/#" || len(service.Sources) != 2 || service.Sources[0].ID != "second" || service.Sources[1].ID != "first" || service.Instances[0].Health != "passing" || service.Routing.Source != "first" || service.Publication.Scope != "yard" || service.Warnings[0].Message != "Check this." {
		t.Fatalf("service: %+v", service)
	}
}

func TestCatalogMatchesOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t, func(c *client.Client) {
		_, _ = c.Yard()
		_, _ = c.YardServices("", "")
		_, _ = c.YardServices("ws /+?", "healthy")
		_, _ = c.YardService("ws /+?", "api/#")
	})
}
