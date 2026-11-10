package client_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/client/clienttest"
)

func TestReachabilityRequests(t *testing.T) {
	for _, remote := range []bool{false, true} {
		t.Run(map[bool]string{false: "own", true: "remote"}[remote], func(t *testing.T) {
			path := "/prefix/api/workstation/services/web%2Fapi%20%3F/reachability"
			if remote {
				path = "/prefix/api/services/ws%2Fb%20%23/web%2Fapi%20%3F/reachability"
			}
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.Method != "GET" || r.URL.EscapedPath() != path || r.Header.Get("Accept") != "application/json" {
					t.Errorf("unexpected request: %s %s %v", r.Method, r.URL, r.Header)
				}
				w.Write([]byte(`{"workstation":"ws-b","service":"web","passed":true,"instances":[{"id":"a","address":"127.0.0.1:80","outOfRange":true}],"routes":[]}`))
			}))
			defer server.Close()
			c := client.New(server.URL + "/prefix")
			var result client.Reachability
			var err error
			if remote {
				result, err = c.Test("ws/b #", "web/api ?")
			} else {
				result, err = c.TestOwn("web/api ?")
			}
			if err != nil {
				t.Fatal(err)
			}
			if !result.Passed || result.Service != "web" || len(result.Instances) != 1 || !result.Instances[0].OutOfRange {
				t.Fatalf("unexpected reachability: %+v", result)
			}
		})
	}
}

func TestReachabilityMatchesOpenAPI(t *testing.T) {
	clienttest.AssertOperations(t, func(c *client.Client) {
		_, _ = c.TestOwn("web")
		_, _ = c.Test("ws-b", "web")
	})
}
