package command_test

import (
	"net"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

// statusDashboard answers the Status command and nothing else: any other call panics.
type statusDashboard struct {
	command.Dashboard
	status client.Status
}

func (d statusDashboard) Status() (client.Status, error) {
	return d.status, nil
}

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
			h := commandtest.New(t)
			h.Dashboard = statusDashboard{status: client.Status{
				Overall: tc.overall,
				Parts: []client.StatusPart{
					{Name: "Consul", State: tc.partState, Reason: "Peer reason", Details: []client.StatusDetail{
						{Name: "peer", Value: "10.0.0.2", State: tc.detailState},
						{Name: "version", Value: "28.1.1", State: "ok"},
					}},
					{Name: "Docker", State: "ok", Reason: "Docker answers"},
				},
			}}
			args := []string{"status"}
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
