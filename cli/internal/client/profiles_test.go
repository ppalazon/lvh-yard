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

func TestProfileOperationsMatchOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t,
		func(c *client.Client) { _, _ = c.RouteProfiles() },
		func(c *client.Client) { _, _ = c.RouteProfile("team/a ?#") },
		func(c *client.Client) {
			_, _ = c.PutRouteProfiles(map[string]any{"team": map[string]any{"compress": map[string]any{}}})
		},
		func(c *client.Client) {
			_, _ = c.PutRouteProfile("team/a ?#", map[string]any{"compress": map[string]any{}})
		},
		func(c *client.Client) {
			_, _ = c.PutRouteProfile("certificate", map[string]any{"clientCertificate": map[string]any{}})
		},
		func(c *client.Client) { _ = c.RemoveRouteProfile("team/a ?#") },
	)
}

func TestProfileClientOperations(t *testing.T) {
	for _, tc := range []struct {
		name, method, path, body, response string
		call                               func(*client.Client) error
	}{
		{"list", "GET", "/api/workstation/route-profiles", "", `[{"name":"team","type":"headers","services":["web"]}]`, func(c *client.Client) error {
			profiles, err := c.RouteProfiles()
			if err == nil && (len(profiles) != 1 || profiles[0].Name != "team" || profiles[0].Type != "headers" || !reflect.DeepEqual(profiles[0].Services, []string{"web"})) {
				t.Errorf("unexpected profiles: %+v", profiles)
			}
			return err
		}},
		{"get", "GET", "/api/workstation/route-profiles/team%2Fa%20%3F%23", "", `{"name":"team","definition":{"headers":{"customRequestHeaders":{"X-Team":"100"}}}}`, func(c *client.Client) error {
			profile, err := c.RouteProfile("team/a ?#")
			if err == nil && (profile.Name != "team" || profile.Definition["headers"] == nil) {
				t.Errorf("unexpected profile: %+v", profile)
			}
			return err
		}},
		{"put batch", "PUT", "/api/workstation/route-profiles", `{"team":{"compress":{}}}`, `[{"name":"team","services":[]}]`, func(c *client.Client) error {
			profiles, err := c.PutRouteProfiles(map[string]any{"team": map[string]any{"compress": map[string]any{}}})
			if err == nil && (len(profiles) != 1 || profiles[0].Name != "team") {
				t.Errorf("unexpected saved profiles: %+v", profiles)
			}
			return err
		}},
		{"put one", "PUT", "/api/workstation/route-profiles/team%2Fa%20%3F%23", `{"compress":{}}`, `{"name":"team","definition":{"compress":{}}}`, func(c *client.Client) error {
			profile, err := c.PutRouteProfile("team/a ?#", map[string]any{"compress": map[string]any{}})
			if err == nil && (profile.Name != "team" || profile.Definition["compress"] == nil) {
				t.Errorf("unexpected saved profile: %+v", profile)
			}
			return err
		}},
		{"delete", "DELETE", "/api/workstation/route-profiles/team%2Fa%20%3F%23", "", "", func(c *client.Client) error { return c.RemoveRouteProfile("team/a ?#") }},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != tc.method || r.URL.EscapedPath() != "/prefix"+tc.path || r.Header.Get("Accept") != "application/json" {
					t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
				}
				body, _ := io.ReadAll(r.Body)
				if tc.body == "" {
					if len(body) != 0 {
						t.Errorf("unexpected body: %s", body)
					}
				} else {
					var got, want any
					if json.Unmarshal(body, &got) != nil || json.Unmarshal([]byte(tc.body), &want) != nil || !reflect.DeepEqual(got, want) || r.Header.Get("Content-Type") != "application/json" {
						t.Errorf("unexpected JSON request: %s %v", body, r.Header)
					}
				}
				if tc.response == "" {
					w.WriteHeader(http.StatusNoContent)
				} else {
					w.Write([]byte(tc.response))
				}
			}))
			defer server.Close()
			if err := tc.call(client.New(server.URL + "/prefix")); err != nil {
				t.Fatal(err)
			}
		})
	}
}

func TestProfileClientPrintsEachProblemReason(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(`{"detail":"Combined reasons.","problems":[{"profile":"file","reason":"file reads usersFile."},{"profile":"typo","reason":"typo has an unknown type."}]}`))
	}))
	defer server.Close()
	for _, call := range []func(*client.Client) error{
		func(c *client.Client) error { _, err := c.PutRouteProfiles(map[string]any{}); return err },
		func(c *client.Client) error { _, err := c.PutRouteProfile("team", map[string]any{}); return err },
	} {
		if err := call(client.New(server.URL)); err == nil || err.Error() != "file reads usersFile.\ntypo has an unknown type." {
			t.Fatalf("unexpected refusal: %v", err)
		}
	}
}
