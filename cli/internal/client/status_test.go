package client_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
)

func TestStatusReadsPartsAndDetails(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.URL.EscapedPath() != "/prefix/api/status" || r.Header.Get("Accept") != "application/json" {
			t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
		}
		w.Write([]byte(`{"overall":"warning","parts":[{"name":"Consul","state":"warning","reason":"Peer missing","details":[{"name":"peer","value":"missing","state":"warning"}]}]}`))
	}))
	defer server.Close()
	status, err := client.New(server.URL + "/prefix").Status()
	if err != nil {
		t.Fatal(err)
	}
	if status.Overall != "warning" || len(status.Parts) != 1 || status.Parts[0].Reason != "Peer missing" || status.Parts[0].Details[0].Value != "missing" {
		t.Fatalf("unexpected status: %+v", status)
	}
}
