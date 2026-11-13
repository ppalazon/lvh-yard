package client_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestRegisterAndEditInstanceSendTheGivenSettings(t *testing.T) {
	for _, edit := range []bool{false, true} {
		t.Run(map[bool]string{false: "register", true: "edit"}[edit], func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				method, path, segments := "POST", "/api/workstation/services/web/blue/instances", 6
				if edit {
					method, path, segments = "PUT", path+"/one/two:80", 7
				}
				var body map[string]any
				if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
					t.Error(err)
				}
				want := map[string]any{"host": "::1", "port": float64(8080), "check": map[string]any{"path": "/health"}, "tags": []any{"blue"}, "meta": map[string]any{"owner": "team"}}
				if r.Method != method || r.URL.Path != path || len(strings.Split(r.URL.EscapedPath(), "/")) != segments || r.URL.RawQuery != "" || r.Header.Get("Content-Type") != "application/json" || r.Header.Get("Accept") != "application/json" || !reflect.DeepEqual(body, want) {
					t.Errorf("unexpected request: %s %s %+v", r.Method, r.URL.EscapedPath(), body)
				}
				w.Write([]byte(`{"id":"one/two:80","host":"::1","port":8080,"check":{"path":"/health"},"tags":["blue"],"meta":{"owner":"team"}}`))
			}))
			defer server.Close()
			host, tags, meta := "::1", []string{"blue"}, map[string]string{"owner": "team"}
			request := client.StaticInstanceRequest{Host: &host, Port: 8080, Check: &client.HealthCheck{Path: "/health"}, Tags: &tags, Meta: &meta}
			c := client.New(server.URL)
			var instance client.Instance
			var err error
			if edit {
				instance, err = c.EditInstance("web/blue", "one/two:80", request)
			} else {
				instance, err = c.Register("web/blue", request)
			}
			if err != nil || instance.ID != "one/two:80" || instance.Check == nil || instance.Check.Path != "/health" {
				t.Fatalf("instance=%+v, error=%v", instance, err)
			}
		})
	}
}

func TestRegisterMissingServiceSuggestsCreatingIt(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(404)
		w.Write([]byte(`{"detail":"This Workstation has no Service web.","service":"web"}`))
	}))
	defer server.Close()
	_, err := client.New(server.URL).Register("web", client.StaticInstanceRequest{Port: 80})
	want := "This Workstation has no Service web. Create it first: lvh-yard service create web"
	if err == nil || err.Error() != want {
		t.Fatalf("error=%v, want %q", err, want)
	}
}

func TestRemoveOneInstanceOrBinding(t *testing.T) {
	for _, binding := range []bool{false, true} {
		t.Run(map[bool]string{false: "instance", true: "binding"}[binding], func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				kind := "instances"
				if binding {
					kind = "container-bindings"
				}
				if r.Method != "DELETE" || r.URL.Path != "/api/workstation/services/web/blue/"+kind+"/one/two:80" || len(strings.Split(r.URL.EscapedPath(), "/")) != 7 || r.URL.RawQuery != "" {
					t.Errorf("unexpected remove: %s %s", r.Method, r.URL.EscapedPath())
				}
				w.WriteHeader(204)
			}))
			defer server.Close()
			c := client.New(server.URL)
			var err error
			if binding {
				err = c.UnbindContainer("web/blue", "one/two:80")
			} else {
				err = c.Remove("web/blue", "one/two:80")
			}
			if err != nil {
				t.Fatal(err)
			}
		})
	}
}

func TestInstanceOperationsMatchOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t,
		func(c *client.Client) { _, _ = c.Register("web/blue", client.StaticInstanceRequest{Port: 80}) },
		func(c *client.Client) {
			_, _ = c.EditInstance("web/blue", "one/two:80", client.StaticInstanceRequest{Port: 80})
		},
		func(c *client.Client) { _ = c.Remove("web/blue", "one/two:80") },
		func(c *client.Client) { _ = c.UnbindContainer("web/blue", "one/two:80") },
	)
}
