package command_test

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestInitKeepsGossipKeyOutOfLocalSetupInstructions(t *testing.T) {
	const key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
	run := commandtest.New(t).Run("ws", "init", "--dir", t.TempDir(), "--workstation", "ws-a", "--gossip-key", key)
	if run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}
	if strings.Contains(run.Stdout, key) {
		t.Fatal("local setup instructions exposed the Gossip Key")
	}
}

func TestWSYardShareKeepsPrivateMaterialLocal(t *testing.T) {
	const key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
	for _, tlsEnabled := range []bool{false, true} {
		t.Run(map[bool]string{false: "plain", true: "TLS"}[tlsEnabled], func(t *testing.T) {
			folder := t.TempDir()
			yard := commandtest.New(t)
			if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--gossip-key", key); run.ExitCode != 0 {
				t.Fatal(run.Stderr)
			}
			files := map[string]string{
				"ca/ca.key":             "private-ca-material",
				"tls/consul/consul.key": "private-workstation-material", "tls/traefik/traefik.key": "private-workstation-material",
			}
			if tlsEnabled {
				files["consul.d/10-tls.hcl"] = "TLS enabled"
				files["tls/consul/ca.crt"] = "public-ca-certificate"
			}
			for name, contents := range files {
				path := filepath.Join(folder, name)
				if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
					t.Fatal(err)
				}
				if err := os.WriteFile(path, []byte(contents), 0600); err != nil {
					t.Fatal(err)
				}
			}
			before := readInitFile(t, folder, ".env")
			run := yard.Run("ws", "yard", "share", "--dir", folder, "--address", "192.168.1.10")
			if run.ExitCode != 0 {
				t.Fatal(run.Stderr)
			}
			if !strings.Contains(run.Stdout, key) {
				t.Fatal("explicit sharing omitted the joining credential")
			}
			for _, secret := range []string{"private-ca-material", "private-workstation-material", filepath.Join(folder, "tls/consul/consul.key"), filepath.Join(folder, "tls/traefik/traefik.key"), filepath.Join(folder, "ca/ca.key")} {
				if strings.Contains(run.Stdout, secret) {
					t.Fatal("sharing exposed this Workstation's private material")
				}
			}
			if tlsEnabled && !strings.Contains(run.Stdout, "ca issue consul") {
				t.Fatal("TLS sharing omitted how to get the Consul certificate")
			}
			if !strings.Contains(run.Stdout, "yard peers --sync") {
				t.Fatal("sharing does not remind to check and sync the Peers")
			}
			if strings.Contains(run.Stdout, "Check the joining Workstation's status") {
				t.Fatal("sharing lists steps that run on the joining Workstation")
			}
			if readInitFile(t, folder, ".env") != before {
				t.Fatal("sharing changed Workstation settings")
			}
			for name, contents := range files {
				if readInitFile(t, folder, name) != contents {
					t.Fatal("sharing changed certificate material")
				}
			}
		})
	}
}

func TestWSYardShareRefusesUnusablePeerAddressesWithoutPrintingCredentials(t *testing.T) {
	const key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
	folder := t.TempDir()
	yard := commandtest.New(t)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--gossip-key", key); run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}
	before := readInitFile(t, folder, ".env")
	for _, address := range []string{"", "0.0.0.0", "::1", "not-an-address"} {
		run := yard.Run("ws", "yard", "share", "--dir", folder, "--address", address)
		if run.ExitCode == 0 || strings.Contains(run.Stdout, key) || readInitFile(t, folder, ".env") != before {
			t.Fatalf("invalid sharing address %q changed settings or exposed credentials", address)
		}
	}
}

func TestWSYardShareRequiresTheActivePublicCAForTLSInstructions(t *testing.T) {
	const key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
	folder := t.TempDir()
	yard := commandtest.New(t)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--gossip-key", key); run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}
	if err := os.WriteFile(filepath.Join(folder, "consul.d", "10-tls.hcl"), []byte("TLS enabled"), 0600); err != nil {
		t.Fatal(err)
	}
	run := yard.Run("ws", "yard", "share", "--dir", folder)
	if run.ExitCode == 0 || strings.Contains(run.Stdout, key) {
		t.Fatal("incomplete TLS setup produced a usable-looking invitation")
	}
}

func TestWSYardRejectsUnknownCommands(t *testing.T) {
	for _, args := range [][]string{{"ws", "join"}, {"ws", "yard", "unknown"}} {
		run := commandtest.New(t).Run(args...)
		if run.ExitCode == 0 {
			t.Fatalf("unknown command succeeded: %v", args)
		}
	}
}

func TestWSYardShareSelectsReachablePeer(t *testing.T) {
	for _, tc := range []struct {
		name     string
		bind     string
		override string
		detects  string
		err      error
		want     string
	}{
		{name: "wildcard binding", bind: "0.0.0.0", detects: "192.168.50.20", want: "192.168.50.20:18302"},
		{name: "configured binding wins", bind: "192.168.50.10", detects: "192.168.50.20", want: "192.168.50.10:18302"},
		{name: "explicit address wins", bind: "192.168.50.10", override: "10.20.30.40", detects: "192.168.50.20", want: "10.20.30.40:18302"},
		{name: "detection unavailable", bind: "0.0.0.0", err: errors.New("no route"), want: "<this-workstation-lan-ip>:18302"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			folder := t.TempDir()
			yard := commandtest.New(t)
			yard.Process.LANIP, yard.Process.LANError = tc.detects, tc.err
			for _, args := range [][]string{
				{"ws", "init", "--dir", folder, "--workstation", "ws-a"},
				{"ws", "--dir", folder, "set", "bind-address", tc.bind},
				{"ws", "--dir", folder, "set", "consul-serf-wan-port", "18302"},
			} {
				if run := yard.Run(args...); run.ExitCode != 0 {
					t.Fatal(run.Stderr)
				}
			}
			args := []string{"ws", "--dir", folder, "yard", "share"}
			if tc.override != "" {
				args = append(args, "--address", tc.override)
			}
			run := yard.Run(args...)
			if run.ExitCode != 0 {
				t.Fatal(run.Stderr)
			}
			if !strings.Contains(run.Stdout, "--peers '"+tc.want+"'") {
				t.Fatalf("invitation has the wrong Peer:\n%s", run.Stdout)
			}
		})
	}
}

func TestWSYardPeersSyncKeepsOnlyAnsweringWorkstations(t *testing.T) {
	consul := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/agent/members" || r.URL.Query().Get("wan") != "1" {
			http.NotFound(w, r)
			return
		}
		_, _ = w.Write([]byte(`[
			{"Name":"ws-a.ws-a","Addr":"192.168.1.10","Port":8302,"Status":1,"Tags":{"dc":"ws-a"}},
			{"Name":"ws-c.ws-c","Addr":"192.168.1.12","Port":8302,"Status":1,"Tags":{"dc":"ws-c"}},
			{"Name":"ws-b.ws-b","Addr":"192.168.1.11","Port":18302,"Status":1,"Tags":{"dc":"ws-b"}},
			{"Name":"ws-d.ws-d","Addr":"192.168.1.13","Port":8302,"Status":4,"Tags":{"dc":"ws-d"}}]`))
	}))
	defer consul.Close()
	folder := t.TempDir()
	yard := commandtest.New(t)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}
	if run := yard.Run("ws", "--dir", folder, "set", "consul-http-port", consul.URL[strings.LastIndex(consul.URL, ":")+1:]); run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}
	if run := yard.Run("ws", "--dir", folder, "set", "peers", "192.168.1.13"); run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}

	listed := yard.Run("ws", "--dir", folder, "yard", "peers")
	if listed.ExitCode != 0 {
		t.Fatal(listed.Stderr)
	}
	if strings.Contains(listed.Stdout, "ws-a ") || strings.Contains(listed.Stdout, "192.168.1.13") {
		t.Fatalf("the list holds this Workstation or one that does not answer:\n%s", listed.Stdout)
	}
	if got := strings.Fields(listed.Stdout); !slices.Contains(got, "192.168.1.11:18302") || !slices.Contains(got, "192.168.1.12") {
		t.Fatalf("the list lacks an answering Workstation:\n%s", listed.Stdout)
	}
	if !strings.Contains(readInitFile(t, folder, ".env"), "YARD_PEERS=192.168.1.13\n") {
		t.Fatal("listing changed .env")
	}

	synced := yard.Run("ws", "--dir", folder, "yard", "peers", "--sync")
	if synced.ExitCode != 0 {
		t.Fatal(synced.Stderr)
	}
	if !strings.Contains(readInitFile(t, folder, ".env"), "YARD_PEERS=192.168.1.11:18302 192.168.1.12\n") {
		t.Fatalf(".env lacks the answering Peers:\n%s", readInitFile(t, folder, ".env"))
	}
	if again := yard.Run("ws", "--dir", folder, "yard", "peers", "--sync"); !strings.Contains(again.Stdout, "already up to date") {
		t.Fatalf("a second sync changed something:\n%s", again.Stdout)
	}
}

func TestWSYardPeersNamesAConsulThatIsOff(t *testing.T) {
	folder := t.TempDir()
	yard := commandtest.New(t)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}
	if run := yard.Run("ws", "--dir", folder, "set", "consul-http-port", "1"); run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}
	run := yard.Run("ws", "--dir", folder, "yard", "peers")
	if run.ExitCode == 0 || !strings.Contains(run.Stderr, "cannot reach Consul") {
		t.Fatalf("exit %d, stderr %q", run.ExitCode, run.Stderr)
	}
}
