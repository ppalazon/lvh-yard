package command_test

import (
	"os"
	"path/filepath"
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
				"ca/ca.key":           "private-ca-material",
				"tls/workstation.key": "private-workstation-material",
			}
			if tlsEnabled {
				files["consul.d/10-tls.hcl"] = "TLS enabled"
				files["tls/ca.crt"] = "public-ca-certificate"
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
			for _, secret := range []string{"private-ca-material", "private-workstation-material", filepath.Join(folder, "tls/workstation.key"), filepath.Join(folder, "ca/ca.key")} {
				if strings.Contains(run.Stdout, secret) {
					t.Fatal("sharing exposed this Workstation's private material")
				}
			}
			if tlsEnabled && !strings.Contains(run.Stdout, filepath.Join(folder, "tls/ca.crt")) {
				t.Fatal("TLS sharing omitted the active public CA certificate")
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
