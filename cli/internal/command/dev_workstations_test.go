package command_test

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestDevWorkstationFolders(t *testing.T) {
	root := t.TempDir()
	log := filepath.Join(root, "calls")
	cli := filepath.Join(root, "lvh-yard")
	if err := os.WriteFile(cli, []byte("#!/bin/sh\nprintf '%s\\t' \"$@\" >> \"$CALLS\"\nprintf '\\n' >> \"$CALLS\"\n"), 0700); err != nil {
		t.Fatal(err)
	}
	script, err := filepath.Abs("../../../dev/workstations.sh")
	if err != nil {
		t.Fatal(err)
	}
	run := func(action string) []string {
		t.Helper()
		if err := os.WriteFile(log, nil, 0600); err != nil {
			t.Fatal(err)
		}
		cmd := exec.Command("bash", script, action, cli, root)
		cmd.Env = append(os.Environ(), "CALLS="+log)
		if out, err := cmd.CombinedOutput(); err != nil {
			t.Fatalf("%s: %v: %s", action, err, out)
		}
		calls, err := os.ReadFile(log)
		if err != nil {
			t.Fatal(err)
		}
		return strings.Split(strings.TrimSpace(string(calls)), "\n")
	}
	yard := commandtest.New(t)
	for _, call := range run("up") {
		args := strings.Split(strings.TrimRight(call, "\t"), "\t")
		result := yard.Run(args...)
		if result.ExitCode != 0 {
			t.Fatalf("%v: %+v", args, result)
		}
	}
	for _, ws := range []struct{ name, domain, http, consul, peer string }{
		{"ws-a", "lvh.me", "10080", "18500", "127.0.0.1:18312"},
		{"ws-b", "localhost", "10090", "18510", "127.0.0.1:18302"},
	} {
		env := readInitFile(t, filepath.Join(root, "dev", ws.name), ".env")
		for _, want := range []string{"YARD_BASE_DOMAIN=" + ws.domain, "YARD_HTTP_PORT=" + ws.http, "YARD_CONSUL_HTTP_PORT=" + ws.consul, "YARD_PEERS=" + ws.peer, "YARD_BIND_ADDRESS=127.0.0.1"} {
			if !strings.Contains(env, want+"\n") {
				t.Errorf("%s missing %s", ws.name, want)
			}
		}
	}
	a := readInitFile(t, filepath.Join(root, "dev/ws-a"), ".env")
	b := readInitFile(t, filepath.Join(root, "dev/ws-b"), ".env")
	key := func(env string) string {
		for _, line := range strings.Split(env, "\n") {
			if strings.HasPrefix(line, "YARD_GOSSIP_KEY=") {
				return line
			}
		}
		t.Fatal("missing Gossip Key")
		return ""
	}
	if key(a) != key(b) {
		t.Fatal("Workstations have different Gossip Keys")
	}
	for _, ws := range []struct {
		name  string
		ports []string
	}{
		{"ws-a", []string{"18080", "17780", "18600", "18503", "18300", "18301", "18302"}},
		{"ws-b", []string{"18090", "17790", "18610", "18513", "18310", "18311", "18312"}},
	} {
		env := readInitFile(t, filepath.Join(root, "dev", ws.name), ".env")
		for i, name := range []string{"TRAEFIK_ADMIN", "DASHBOARD", "CONSUL_DNS", "CONSUL_GRPC_TLS", "CONSUL_SERVER", "CONSUL_SERF_LAN", "CONSUL_SERF_WAN"} {
			if !strings.Contains(env, "YARD_"+name+"_PORT="+ws.ports[i]+"\n") {
				t.Errorf("%s: wrong %s port", ws.name, name)
			}
		}
		for _, name := range []string{"TRAEFIK_BIND_ADDRESS", "DASHBOARD_BIND_ADDRESS"} {
			if !strings.Contains(env, "YARD_"+name+"=127.0.0.1\n") {
				t.Errorf("%s: wrong %s", ws.name, name)
			}
		}
	}
	calls := run("up")
	if len(calls) != 2 || !strings.Contains(calls[0], "start") || !strings.Contains(calls[1], "start") {
		t.Fatalf("existing folders were reconfigured: %v", calls)
	}
	calls = run("down")
	if len(calls) != 2 || !strings.Contains(calls[0], "stop") || !strings.Contains(calls[1], "stop") {
		t.Fatalf("stop: %v", calls)
	}
}
