package client_test

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"reflect"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestPublicationRequests(t *testing.T) {
	scope, log := "yard", false
	profiles := []string{"auth", "cors"}
	empty := []string{}
	for _, tc := range []struct {
		name    string
		request client.PublicationRequest
		body    string
	}{
		{"keep existing settings", client.PublicationRequest{}, ""},
		{"chosen settings", client.PublicationRequest{Scope: &scope, AccessLog: &log, Profiles: &profiles}, `{"scope":"yard","accessLog":false,"profiles":["auth","cors"]}`},
		{"clear profiles", client.PublicationRequest{Profiles: &empty}, `{"profiles":[]}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "PUT" || r.URL.EscapedPath() != "/prefix/api/workstation/services/web%2Fapi%20%3F/publication" || r.Header.Get("Accept") != "application/json" {
					t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
				}
				data, err := io.ReadAll(r.Body)
				if err != nil {
					t.Error(err)
				}
				if tc.body == "" {
					if len(data) != 0 || r.Header.Get("Content-Type") != "" {
						t.Errorf("unexpected body: %s", data)
					}
				} else {
					var got, want any
					if err := json.Unmarshal(data, &got); err != nil {
						t.Error(err)
					}
					if err := json.Unmarshal([]byte(tc.body), &want); err != nil {
						t.Fatal(err)
					}
					if !reflect.DeepEqual(got, want) || r.Header.Get("Content-Type") != "application/json" {
						t.Errorf("body: %s; want %s", data, tc.body)
					}
				}
				w.Write([]byte(`{"name":"web","hostname":"web.lvh.me","publication":{"scope":"yard","accessLog":false},"profiles":["auth","cors"]}`))
			}))
			defer server.Close()
			service, err := client.New(server.URL+"/prefix").Publish("web/api ?", tc.request)
			if err != nil {
				t.Fatal(err)
			}
			if service.Hostname != "web.lvh.me" || service.Publication == nil || service.Publication.Scope != "yard" || !reflect.DeepEqual(service.Profiles, profiles) {
				t.Fatalf("unexpected Service: %+v", service)
			}
		})
	}
}

func TestUnpublishRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "DELETE" || r.URL.EscapedPath() != "/prefix/api/workstation/services/web%2Fapi%20%3F/publication" || r.Header.Get("Accept") != "application/json" || r.ContentLength != 0 {
			t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()
	if err := client.New(server.URL + "/prefix").Unpublish("web/api ?"); err != nil {
		t.Fatal(err)
	}
}

func TestPublicationMatchesOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t,
		func(c *client.Client) { _, _ = c.Publish("web/api ?", client.PublicationRequest{}) },
		func(c *client.Client) { _ = c.Unpublish("web/api ?") },
	)
}
