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
		mark                                  string
	}{
		{"ok", "ok", "ok", "", false, 0, "ok    "},
		{"warning", "warning", "warning", "warning", false, 0, "WARN  "},
		{"strict warning", "warning", "warning", "warning", true, 1, "WARN  "},
		{"failing", "failing", "failing", "failing", false, 1, "FAIL  "},
		{"unknown", "unknown", "unknown", "unknown", false, 1, "FAIL  "},
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
			suffix := ""
			if tc.detailState != "" && tc.detailState != "ok" {
				suffix = " (" + tc.detailState + ")"
			}
			want := "LVH Yard on this Workstation: " + tc.overall + "\n  " + tc.mark + "Consul: Peer reason\n          peer: 10.0.0.2" + suffix + "\n          version: 28.1.1\n  ok    Docker: Docker answers\n"
			if result.ExitCode != tc.exit || result.Stdout != want || result.Stderr != "" {
				t.Fatalf("got %+v; want code %d and output %q", result, tc.exit, want)
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
	listener.Close()
	result := commandtest.New(t).Run("--dashboard", address, "status")
	if result.ExitCode != 1 || result.Stdout != "" || !strings.Contains(result.Stderr, address) || strings.Count(result.Stderr, "\n") != 1 {
		t.Fatalf("unexpected stopped dashboard result: %+v", result)
	}
}
