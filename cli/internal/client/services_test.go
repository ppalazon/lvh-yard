package client_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestOwnServicesAndService(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" || r.Header.Get("Accept") != "application/json" {
			t.Errorf("unexpected request: %s %s", r.Method, r.URL)
		}
		if r.URL.EscapedPath() == "/api/workstation/services" {
			w.Write([]byte(`[{"name":"web","workstation":"desk","instances":[]}]`))
		} else if r.URL.EscapedPath() == "/api/workstation/services/web%2Fblue" {
			w.Write([]byte(`{"name":"web/blue","workstation":"desk","instances":[{"id":"one","host":"::1","port":80}]}`))
		} else {
			t.Errorf("unexpected path: %s", r.URL.EscapedPath())
		}
	}))
	defer server.Close()
	c := client.New(server.URL)
	services, err := c.OwnServices()
	if err != nil || len(services) != 1 || services[0].Name != "web" {
		t.Fatalf("services: %+v, %v", services, err)
	}
	service, err := c.OwnService("web/blue")
	if err != nil || service.Name != "web/blue" || len(service.Instances) != 1 || service.Instances[0].Host != "::1" {
		t.Fatalf("service: %+v, %v", service, err)
	}
}

func TestCreateServiceReportsWhetherItWasNew(t *testing.T) {
	for _, status := range []int{201, 200} {
		t.Run(http.StatusText(status), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				var body map[string]string
				if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
					t.Error(err)
				}
				if r.Method != "POST" || r.URL.Path != "/api/workstation/services" || r.Header.Get("Content-Type") != "application/json" || body["name"] != "web" {
					t.Errorf("unexpected create: %s %s %+v", r.Method, r.URL, body)
				}
				w.WriteHeader(status)
				w.Write([]byte(`{"name":"web","created":true}`))
			}))
			defer server.Close()
			created, err := client.New(server.URL).CreateService("web")
			if err != nil || created != (status == 201) {
				t.Fatalf("created=%v, error=%v", created, err)
			}
		})
	}
}

func TestDeleteServiceCanBeRepeatedButPreservesRefusals(t *testing.T) {
	for _, tc := range []struct {
		status int
		body   string
		want   string
	}{
		{204, "", ""},
		{404, `{"detail":"There is no such Service."}`, ""},
		{409, `{"detail":"The Services of desk can be changed only on desk."}`, "The Services of desk can be changed only on desk."},
		{500, `{"detail":"There is no such Service."}`, "There is no such Service."},
	} {
		t.Run(http.StatusText(tc.status), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "DELETE" || r.URL.EscapedPath() != "/api/workstation/services/web%2Fblue" {
					t.Errorf("unexpected delete: %s %s", r.Method, r.URL)
				}
				w.WriteHeader(tc.status)
				w.Write([]byte(tc.body))
			}))
			defer server.Close()
			err := client.New(server.URL).DeleteService("web/blue")
			if tc.want == "" && err != nil || tc.want != "" && (err == nil || err.Error() != tc.want) {
				t.Fatalf("error=%v, want %q", err, tc.want)
			}
		})
	}
}

func TestServiceOperationsMatchOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t,
		func(c *client.Client) { c.OwnServices() },
		func(c *client.Client) { c.OwnService("web/blue") },
		func(c *client.Client) { c.CreateService("web") },
		func(c *client.Client) { c.DeleteService("web/blue") },
	)
}
