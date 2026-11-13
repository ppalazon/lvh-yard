package command_test

import (
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestStatus(t *testing.T) {
	for _, tc := range []struct {
		name, overall, partState, detailState string
		strict                                bool
		exit                                  int
		want                                  string
	}{
		{"ok", "ok", "ok", "", false, 0, "LVH Yard on this Workstation: ok\n  ok    Consul: Peer reason\n          peer: 10.0.0.2\n          version: 28.1.1\n  ok    Docker: Docker answers\n"},
		{"warning", "warning", "warning", "warning", false, 0, "LVH Yard on this Workstation: warning\n  WARN  Consul: Peer reason\n          peer: 10.0.0.2 (warning)\n          version: 28.1.1\n  ok    Docker: Docker answers\n"},
		{"strict warning", "warning", "warning", "warning", true, 1, "LVH Yard on this Workstation: warning\n  WARN  Consul: Peer reason\n          peer: 10.0.0.2 (warning)\n          version: 28.1.1\n  ok    Docker: Docker answers\n"},
		{"failing", "failing", "failing", "failing", false, 1, "LVH Yard on this Workstation: failing\n  FAIL  Consul: Peer reason\n          peer: 10.0.0.2 (failing)\n          version: 28.1.1\n  ok    Docker: Docker answers\n"},
		{"unknown", "unknown", "unknown", "unknown", false, 1, "LVH Yard on this Workstation: unknown\n  FAIL  Consul: Peer reason\n          peer: 10.0.0.2 (unknown)\n          version: 28.1.1\n  ok    Docker: Docker answers\n"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.Write([]byte(`{"overall":"` + tc.overall + `","parts":[{"name":"Consul","state":"` + tc.partState + `","reason":"Peer reason","details":[{"name":"peer","value":"10.0.0.2","state":"` + tc.detailState + `"},{"name":"version","value":"28.1.1","state":"ok"}]},{"name":"Docker","state":"ok","reason":"Docker answers","details":[]}]}`))
			}))
			defer server.Close()
			h := commandtest.New(t)
			args := []string{"--dashboard", server.URL, "status"}
			if tc.strict {
				args = append(args, "--strict")
			}
			result := h.Run(args...)
			if result.ExitCode != tc.exit || result.Stdout != tc.want || result.Stderr != "" {
				t.Fatalf("got %+v; want code %d and output %q", result, tc.exit, tc.want)
			}
		})
	}
}

func TestStatusStoppedDashboard(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	address := "http://" + listener.Addr().String()
	_ = listener.Close()
	result := commandtest.New(t).Run("--dashboard", address, "status")
	if result.ExitCode != 1 || result.Stdout != "" || !strings.Contains(result.Stderr, address) || strings.Count(result.Stderr, "\n") != 1 {
		t.Fatalf("unexpected stopped dashboard result: %+v", result)
	}
}
