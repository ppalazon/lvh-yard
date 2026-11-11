package client_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestAddAliasRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" || r.URL.EscapedPath() != "/prefix/api/workstation/services/web%2Fapi%20%3F/aliases" || r.Header.Get("Accept") != "application/json" || r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
		}
		var body map[string]any
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Error(err)
		}
		if len(body) != 3 || body["workstation"] != "ws-b" || body["service"] != "grafana" || body["create"] != true {
			t.Errorf("unexpected body: %#v", body)
		}
		w.WriteHeader(http.StatusCreated)
		w.Write([]byte(`{"id":"ws-b/grafana","workstation":"ws-b","service":"grafana","state":"unavailable","reason":"No Instance is in range.","serviceCreated":true,"aliasCreated":true}`))
	}))
	defer server.Close()
	create := true
	added, err := client.New(server.URL+"/prefix").AddAlias("web/api ?", client.AliasRequest{Workstation: "ws-b", Service: "grafana", Create: &create})
	if err != nil {
		t.Fatal(err)
	}
	if added.ID != "ws-b/grafana" || added.Workstation != "ws-b" || added.Service != "grafana" || added.State != "unavailable" || added.Reason != "No Instance is in range." || !added.ServiceCreated || !added.AliasCreated {
		t.Fatalf("unexpected Alias: %+v", added)
	}
}

func TestAliasRequestsMatchOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t, func(c *client.Client) {
		_, _ = c.AddAlias("web/api ?", client.AliasRequest{Workstation: "ws-b", Service: "grafana"})
		_, _ = c.Aliases("web/api ?")
		_ = c.RemoveAlias("web/api ?", "ws/b #", "grafana/db ?")
	})
}

func TestListAliasesRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.URL.EscapedPath() != "/prefix/api/workstation/services/web%2Fapi%20%3F/aliases" || r.Header.Get("Accept") != "application/json" {
			t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
		}
		w.Write([]byte(`[{"id":"ws-b/grafana","workstation":"ws-b","service":"grafana","state":"not-shared","reason":"Only Local Scope."},{"id":"ws-c/web","workstation":"ws-c","service":"web","state":"active"}]`))
	}))
	defer server.Close()
	aliases, err := client.New(server.URL + "/prefix").Aliases("web/api ?")
	if err != nil {
		t.Fatal(err)
	}
	if len(aliases) != 2 || aliases[0].ID != "ws-b/grafana" || aliases[0].Workstation != "ws-b" || aliases[0].Service != "grafana" || aliases[0].State != "not-shared" || aliases[0].Reason != "Only Local Scope." || aliases[1].State != "active" {
		t.Fatalf("unexpected Aliases: %+v", aliases)
	}
}

func TestRemoveAliasRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "DELETE" || r.URL.EscapedPath() != "/prefix/api/workstation/services/web%2Fapi%20%3F/aliases/ws%2Fb%20%23/grafana%2Fdb%20%3F" || r.Header.Get("Accept") != "application/json" {
			t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()
	if err := client.New(server.URL+"/prefix").RemoveAlias("web/api ?", "ws/b #", "grafana/db ?"); err != nil {
		t.Fatal(err)
	}
}
