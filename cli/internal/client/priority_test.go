package client_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"reflect"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestInstancePriorityRequests(t *testing.T) {
	on := false
	order := []string{"ws-b/web", "static-id"}
	empty := []string{}
	for _, tc := range []struct {
		name    string
		request *client.InstancePriorityRequest
		body    string
	}{
		{"show", nil, ""},
		{"off", &client.InstancePriorityRequest{On: &on}, `{"on":false}`},
		{"order", &client.InstancePriorityRequest{Order: &order}, `{"order":["ws-b/web","static-id"]}`},
		{"empty order", &client.InstancePriorityRequest{Order: &empty}, `{"order":[]}`},
		{"both", &client.InstancePriorityRequest{On: &on, Order: &order}, `{"on":false,"order":["ws-b/web","static-id"]}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				method := "GET"
				if tc.request != nil {
					method = "PUT"
				}
				if r.Method != method || r.URL.EscapedPath() != "/prefix/api/workstation/services/web%2Fapi%20%3F/instance-priority" || r.Header.Get("Accept") != "application/json" {
					t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
				}
				if tc.request != nil {
					var got, want any
					if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
						t.Error(err)
					}
					if err := json.Unmarshal([]byte(tc.body), &want); err != nil {
						t.Fatal(err)
					}
					if !reflect.DeepEqual(got, want) || r.Header.Get("Content-Type") != "application/json" {
						t.Errorf("body: %v; want %s", got, tc.body)
					}
				} else if r.ContentLength != 0 {
					t.Error("GET has a body")
				}
				w.Write([]byte(`{"on":false,"sources":[{"id":"ws-b/web","kind":"alias","instances":[]}],"routing":{"source":"ws-b/web","kind":"alias"}}`))
			}))
			defer server.Close()
			c := client.New(server.URL + "/prefix")
			var priority client.InstancePriority
			var err error
			if tc.request == nil {
				priority, err = c.InstancePriority("web/api ?")
			} else {
				priority, err = c.SetInstancePriority("web/api ?", *tc.request)
			}
			if err != nil {
				t.Fatal(err)
			}
			if priority.On || len(priority.Sources) != 1 || priority.Sources[0].ID != "ws-b/web" || priority.Routing == nil || priority.Routing.Source != "ws-b/web" {
				t.Fatalf("unexpected priority: %+v", priority)
			}
		})
	}
}

func TestInstancePriorityMatchesOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t,
		func(c *client.Client) { _, _ = c.InstancePriority("web/api ?") },
		func(c *client.Client) { _, _ = c.SetInstancePriority("web/api ?", client.InstancePriorityRequest{}) },
	)
}
