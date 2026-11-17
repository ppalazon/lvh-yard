package command_test

import (
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestInitKeepsGossipKeyOutOfLocalSetupInstructions(t *testing.T) {
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me")
	if run.ExitCode != 0 {
		t.Fatal(run.Stderr)
	}
	_, key, _ := strings.Cut(readInitFile(t, folder, ".env"), "\nYARD_GOSSIP_KEY=")
	key, _, _ = strings.Cut(key, "\n")
	if key == "" || strings.Contains(run.Stdout, key) {
		t.Fatal("local setup instructions exposed the Gossip Key")
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

// A folder with no Workstation says what to do first, and the command changes nothing.
func TestWSCommandsOnAFolderWithoutAWorkstationSayToRunInit(t *testing.T) {
	for _, args := range [][]string{
		{"yard", "join", "invitation.zip"}, {"yard", "share", "ws-b"}, {"yard", "standalone"}, {"yard", "peers"},
		{"show"}, {"set", "peers", "192.168.1.10"}, {"tls", "ca", "create"}, {"tls", "install"},
	} {
		folder := filepath.Join(t.TempDir(), "none")
		run := commandtest.New(t).Run(append(append([]string{"ws"}, args...), "--dir", folder)...)
		if run.ExitCode == 0 || !strings.Contains(run.Stderr, "lvh-yard ws init") {
			t.Fatalf("%v: %+v", args, run)
		}
		if exists(folder, "") {
			t.Fatalf("%v created the folder", args)
		}
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
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); run.ExitCode != 0 {
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
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); run.ExitCode != 0 {
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
