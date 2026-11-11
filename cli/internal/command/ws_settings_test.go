package command_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWSSetValues(t *testing.T) {
	for _, test := range []struct{ name, key, good, bad, want string }{
		{"base-domain", "YARD_BASE_DOMAIN", "example.test", "bad_domain", "example.test"},
		{"peers", "YARD_PEERS", "10.0.0.1, 10.0.0.2:8302", "bad-peer", "10.0.0.1 10.0.0.2:8302"},
		{"bind-address", "YARD_BIND_ADDRESS", "10.0.0.3", "bad", "10.0.0.3"},
		{"traefik-bind-address", "YARD_TRAEFIK_BIND_ADDRESS", "0.0.0.0", "::1", "0.0.0.0"},
		{"dashboard-bind-address", "YARD_DASHBOARD_BIND_ADDRESS", "127.0.0.1", "bad", "127.0.0.1"},
		{"out-of-range", "YARD_OUT_OF_RANGE", "127.0.0.0/8,172.17.0.0/16", "10.0.0.0/99", "127.0.0.0/8,172.17.0.0/16"},
		{"stack-traces", "YARD_STACK_TRACES", "false", "yes", "false"},
	} {
		t.Run(test.name, func(t *testing.T) { checkWSSetting(t, test.name, test.key, test.good, test.bad, test.want) })
	}
	for _, name := range []string{"http-port", "traefik-admin-port", "dashboard-port", "consul-http-port", "consul-dns-port", "consul-grpc-tls-port", "consul-server-port", "consul-serf-lan-port", "consul-serf-wan-port"} {
		t.Run(name, func(t *testing.T) {
			checkWSSetting(t, name, "YARD_"+strings.ToUpper(strings.ReplaceAll(name, "-", "_")), "1234", "65536", "1234")
		})
	}
}

func checkWSSetting(t *testing.T, name, key, good, bad, want string) {
	t.Helper()
	folder := t.TempDir()
	before := "# Keep my comment\n#"+key+"=old\nYARD_WORKSTATION=ws-a\n"
	writeEnv(t, folder, before)
	yard := commandtest.New(t)
	run := yard.Run("ws", "set", "--dir", folder, name, bad)
	if run.ExitCode != 1 || readInitFile(t, folder, ".env") != before || !strings.Contains(run.Stderr, "use ") { t.Fatalf("invalid value: %+v", run) }
	run = yard.Run("ws", "set", "--dir", folder, name, good)
	if run.ExitCode != 0 || !strings.Contains(run.Stdout, "ws restart") { t.Fatalf("set: %+v", run) }
	if got := readInitFile(t, folder, ".env"); got != "# Keep my comment\n"+key+"="+want+"\nYARD_WORKSTATION=ws-a\n" { t.Fatalf("changed other lines: %q", got) }
	info, err := os.Stat(filepath.Join(folder, ".env"))
	if err != nil || info.Mode().Perm() != 0600 { t.Fatalf("mode: %v %v", info, err) }
}

func TestWSShowHidesGossipKey(t *testing.T) {
	folder := t.TempDir()
	writeEnv(t, folder, "# Secret key: never print comments\nYARD_GOSSIP_KEY=secret\nYARD_BASE_DOMAIN=example.test\n")
	run := commandtest.New(t).Run("ws", "show", "--dir", folder)
	if run.ExitCode != 0 || strings.Contains(run.Stdout, "secret") || !strings.Contains(run.Stdout, "YARD_GOSSIP_KEY=[hidden]") || !strings.Contains(run.Stdout, "YARD_BASE_DOMAIN=example.test") { t.Fatalf("show: %+v", run) }
}

func TestWSJoin(t *testing.T) {
	const key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
	for _, peers := range []string{"", "10.0.0.2,10.0.0.3:8302"} {
		t.Run(peers, func(t *testing.T) {
			folder := t.TempDir()
			before := "# Kept\nYARD_WORKSTATION=ws-a\nYARD_GOSSIP_KEY=old\nYARD_PEERS=10.0.0.1\n"
			writeEnv(t, folder, before)
			yard := commandtest.New(t)
			bad := yard.Run("ws", "join", "--dir", folder, "--gossip-key", "bad")
			if bad.ExitCode != 1 || readInitFile(t, folder, ".env") != before { t.Fatalf("bad key: %+v", bad) }
			args := []string{"ws", "join", "--dir", folder, "--gossip-key", key}
			wantPeers := "10.0.0.1"
			if peers != "" { args = append(args, "--peers", peers); wantPeers = "10.0.0.2 10.0.0.3:8302" }
			run := yard.Run(args...)
			want := "# Kept\nYARD_WORKSTATION=ws-a\nYARD_GOSSIP_KEY="+key+"\nYARD_PEERS="+wantPeers+"\n"
			if run.ExitCode != 0 || readInitFile(t, folder, ".env") != want || strings.Contains(run.Stdout, key) { t.Fatalf("join: %+v", run) }
		})
	}
}

func TestWSRefusesRename(t *testing.T) {
	for _, args := range [][]string{{"set", "workstation", "new"}, {"join", "--workstation", "new"}} {
		run := commandtest.New(t).Run(append([]string{"ws"}, args...)...)
		if run.ExitCode != 1 || !strings.Contains(run.Stderr, "export a Backup") || !strings.Contains(run.Stderr, "ws init") || !strings.Contains(run.Stderr, "import") { t.Fatalf("rename: %+v", run) }
	}
}
