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

func TestContainersRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.URL.EscapedPath() != "/prefix/api/workstation/containers" || r.Header.Get("Accept") != "application/json" || r.Header.Get("Content-Type") != "" {
			t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
		}
		w.Write([]byte(`[{"name":"shop-web-1","composeProject":"shop","composeService":"web","service":"web","running":true,"health":"healthy","followedBy":["web","shop"],"ports":[{"containerPort":80,"hostPort":19080,"hostIp":"127.0.0.1"},{"containerPort":443}]},{"name":"stopped","service":"stopped","running":false,"followedBy":[],"ports":[]}]`))
	}))
	defer server.Close()
	containers, err := client.New(server.URL + "/prefix").Containers()
	if err != nil {
		t.Fatal(err)
	}
	if len(containers) != 2 || containers[0].ComposeProject != "shop" || containers[0].ComposeService != "web" || containers[0].Health != "healthy" || !reflect.DeepEqual(containers[0].FollowedBy, []string{"web", "shop"}) || !containers[0].Running || containers[1].Running {
		t.Fatalf("unexpected containers: %+v", containers)
	}
	ports := containers[0].Ports
	if len(ports) != 2 || ports[0].ContainerPort != 80 || ports[0].HostIP != "127.0.0.1" || ports[0].HostPort == nil || *ports[0].HostPort != 19080 || ports[1].HostPort != nil {
		t.Fatalf("unexpected port mappings: %+v", ports)
	}
}

func TestContainerBindingRequests(t *testing.T) {
	for _, compose := range []bool{false, true} {
		t.Run(map[bool]string{false: "container", true: "compose"}[compose], func(t *testing.T) {
			want := map[string]any{"container": "My_Web", "port": float64(80)}
			response := `{"id":"My_Web:80","container":"My_Web","port":80,"instances":["one"]}`
			if compose {
				want = map[string]any{"composeProject": "shop", "composeService": "Front_End", "port": float64(80)}
				response = `{"id":"compose:shop:Front_End:80","composeProject":"shop","composeService":"Front_End","port":80,"instances":["one","two"]}`
			}
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				var body map[string]any
				if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
					t.Error(err)
				}
				if r.Method != "POST" || r.URL.EscapedPath() != "/prefix/api/workstation/services/web%2Fapi%20%3F/container-bindings" || r.Header.Get("Accept") != "application/json" || r.Header.Get("Content-Type") != "application/json" || !reflect.DeepEqual(body, want) {
					t.Errorf("unexpected binding: %s %s %v %+v", r.Method, r.URL, r.Header, body)
				}
				w.WriteHeader(http.StatusCreated)
				w.Write([]byte(response))
			}))
			defer server.Close()
			c := client.New(server.URL + "/prefix")
			var binding client.ContainerBinding
			var err error
			if compose {
				binding, err = c.BindCompose("web/api ?", "shop", "Front_End", 80)
			} else {
				binding, err = c.BindContainer("web/api ?", "My_Web", 80)
			}
			if err != nil {
				t.Fatal(err)
			}
			if binding.ID == "" || binding.Port != 80 || len(binding.Instances) == 0 || (!compose && binding.Container != "My_Web") || (compose && (binding.ComposeProject != "shop" || binding.ComposeService != "Front_End" || len(binding.Instances) != 2)) {
				t.Fatalf("unexpected binding: %+v", binding)
			}
		})
	}
}

func TestContainerOperationsMatchOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t,
		func(c *client.Client) { _, _ = c.Containers() },
		func(c *client.Client) { _, _ = c.BindContainer("web/api ?", "web-1", 80) },
		func(c *client.Client) { _, _ = c.BindCompose("web/api ?", "shop", "web", 80) },
	)
}

func TestContainerOperationErrors(t *testing.T) {
	for _, tc := range []struct {
		name, body, reason string
		status             int
	}{
		{"problem", `{"detail":"Docker does not answer."}`, "Docker does not answer.", 503},
		{"invalid JSON", `<html>not a dashboard</html>`, "not the API's JSON", 200},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.status)
				w.Write([]byte(tc.body))
			}))
			defer server.Close()
			c := client.New(server.URL)
			_, listErr := c.Containers()
			_, namedErr := c.BindContainer("web", "web-1", 80)
			_, composeErr := c.BindCompose("web", "shop", "web", 80)
			for _, err := range []error{listErr, namedErr, composeErr} {
				if err == nil || !strings.Contains(err.Error(), tc.reason) {
					t.Fatalf("got %v; want %q", err, tc.reason)
				}
			}
		})
	}
}
