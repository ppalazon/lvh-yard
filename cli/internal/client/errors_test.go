package client_test

import (
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
)

func TestStatusFailures(t *testing.T) {
	for _, tc := range []struct {
		name       string
		code       int
		body, want string
	}{
		{"reasons", 400, `{"detail":"fallback","problems":[{"place":"profiles.auth","reason":"unknown field"},{"reason":"missing profile"}]}`, "profiles.auth: unknown field\nmissing profile"},
		{"detail", 405, `{"detail":"Only its Home Workstation can change it.","title":"refused"}`, "Only its Home Workstation can change it."},
		{"title", 503, `{"title":"Consul does not answer"}`, "Consul does not answer"},
		{"empty reason fallback", 400, `{"problems":[{"reason":""}],"detail":"bad profile"}`, "bad profile"},
		{"not problem JSON", 502, "gateway broken", "502"},
		{"empty problem", 500, `{}`, "500"},
		{"not JSON", 200, "<html>not a dashboard</html>", "is not the LVH Yard dashboard"},
		{"wrong JSON shape", 200, `[]`, "is not the LVH Yard dashboard"},
		{"truncated JSON", 200, `{"overall":`, "is not the LVH Yard dashboard"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(tc.code); w.Write([]byte(tc.body)) }))
			defer server.Close()
			_, err := client.New(server.URL).Status()
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("got %v; want %q", err, tc.want)
			}
		})
	}
}

func TestStatusRefusedConnectionNamesAddress(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	address := "http://" + listener.Addr().String()
	_ = listener.Close()
	_, err = client.New(address).Status()
	if err == nil || !strings.Contains(err.Error(), "does not answer at "+address) {
		t.Fatalf("got %v", err)
	}
}

func TestStatusPreservesEscapedBasePath(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.EscapedPath() != "/yard%2Fspace%20name/api/status" {
			t.Errorf("path: %s", r.URL.EscapedPath())
		}
		w.Write([]byte(`{"overall":"ok","parts":[]}`))
	}))
	defer server.Close()
	if _, err := client.New(server.URL + "/yard%2Fspace%20name/").Status(); err != nil {
		t.Fatal(err)
	}
}
