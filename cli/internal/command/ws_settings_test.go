package command_test

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestWSSetValues(t *testing.T) {
	for _, test := range []struct{ name, key, good, bad, want string }{
		{"base-domain", "YARD_BASE_DOMAIN", "example.test", "bad_domain", "example.test"},
		{"peers", "YARD_PEERS", "10.0.0.1, 10.0.0.2:8302", "bad-peer", "10.0.0.1 10.0.0.2:8302"},
		{"bind-address", "YARD_BIND_ADDRESS", "10.0.0.3", "bad", "10.0.0.3"},
		{"instance-host", "YARD_INSTANCE_HOST", "10.0.0.7", "host.docker.internal", "10.0.0.7"},
		{"traefik-bind-address", "YARD_TRAEFIK_BIND_ADDRESS", "0.0.0.0", "::1", "0.0.0.0"},
		{"dashboard-bind-address", "YARD_DASHBOARD_BIND_ADDRESS", "127.0.0.1", "bad", "127.0.0.1"},
		{"out-of-range", "YARD_OUT_OF_RANGE", "127.0.0.0/8,172.17.0.0/16", "10.0.0.0/99", "127.0.0.0/8,172.17.0.0/16"},
		{"stack-traces", "YARD_STACK_TRACES", "false", "yes", "false"},
	} {
		t.Run(test.name, func(t *testing.T) { checkWSSetting(t, test.name, test.key, test.good, test.bad, test.want) })
	}
	for _, name := range []string{"http-port", "https-port", "traefik-admin-port", "dashboard-port", "consul-http-port", "consul-dns-port", "consul-grpc-tls-port", "consul-server-port", "consul-serf-lan-port", "consul-serf-wan-port"} {
		t.Run(name, func(t *testing.T) {
			checkWSSetting(t, name, "YARD_"+strings.ToUpper(strings.ReplaceAll(name, "-", "_")), "1234", "65536", "1234")
		})
	}
}

func checkWSSetting(t *testing.T, name, key, good, bad, want string) {
	t.Helper()
	folder := t.TempDir()
	before := "# Keep my comment\n#" + key + "=old\nYARD_WORKSTATION=ws-a\n"
	writeEnv(t, folder, before)
	yard := commandtest.New(t)
	run := yard.Run("ws", "set", "--dir", folder, name, bad)
	if run.ExitCode != 1 || readInitFile(t, folder, ".env") != before || !strings.Contains(run.Stderr, "use ") {
		t.Fatalf("invalid value: %+v", run)
	}
	run = yard.Run("ws", "set", "--dir", folder, name, good)
	if run.ExitCode != 0 {
		t.Fatalf("set: %+v", run)
	}
	if got := readInitFile(t, folder, ".env"); got != "# Keep my comment\n"+key+"="+want+"\nYARD_WORKSTATION=ws-a\n" {
		t.Fatalf("changed other lines: %q", got)
	}
	info, err := os.Stat(filepath.Join(folder, ".env"))
	if err != nil || (commandtest.UnixModes && info.Mode().Perm() != 0600) {
		t.Fatalf("mode: %v %v", info, err)
	}
}

func TestWSShowHidesGossipKey(t *testing.T) {
	folder := t.TempDir()
	writeEnv(t, folder, "# Secret key: never print comments\nYARD_GOSSIP_KEY=secret\nYARD_BASE_DOMAIN=example.test\n")
	run := commandtest.New(t).Run("ws", "show", "--dir", folder)
	if run.ExitCode != 0 || strings.Contains(run.Stdout, "secret") || !strings.Contains(run.Stdout, "YARD_GOSSIP_KEY=[hidden]") || !strings.Contains(run.Stdout, "YARD_BASE_DOMAIN=example.test") {
		t.Fatalf("show: %+v", run)
	}
}

// The Gossip Key and the Peers come only from an invitation. ws yard join has no flag for them.
func TestWSYardJoinNeedsAnInvitation(t *testing.T) {
	folder := t.TempDir()
	const before = "# Kept\nYARD_WORKSTATION=ws-a\nYARD_GOSSIP_KEY=old\nYARD_PEERS=10.0.0.1\n"
	writeEnv(t, folder, before)
	yard := commandtest.New(t)
	for _, args := range [][]string{{}, {"--gossip-key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}, {"--peers", "10.0.0.2"}} {
		run := yard.Run(append([]string{"ws", "yard", "join", "--dir", folder}, args...)...)
		if run.ExitCode != 1 || readInitFile(t, folder, ".env") != before {
			t.Fatalf("join %v: %+v", args, run)
		}
	}
}

func TestWSRefusesRename(t *testing.T) {
	for _, args := range [][]string{{"set", "workstation", "new"}, {"set", "workstation-name", "new"}, {"set", "YARD_WORKSTATION", "new"}, {"yard", "join", "--workstation", "new"}} {
		run := commandtest.New(t).Run(append([]string{"ws"}, args...)...)
		if run.ExitCode != 1 || !strings.Contains(run.Stderr, "export a Backup") || !strings.Contains(run.Stderr, "ws init") || !strings.Contains(run.Stderr, "import") {
			t.Fatalf("rename: %+v", run)
		}
	}
}

func TestWSSetEmptyAndBoundaryValues(t *testing.T) {
	for _, test := range []struct{ name, key, value string }{
		{"peers", "YARD_PEERS", ""},
		{"bind-address", "YARD_BIND_ADDRESS", ""},
		{"instance-host", "YARD_INSTANCE_HOST", ""},
		{"out-of-range", "YARD_OUT_OF_RANGE", ""},
		{"http-port", "YARD_HTTP_PORT", "1"},
		{"http-port", "YARD_HTTP_PORT", "65535"},
		{"stack-traces", "YARD_STACK_TRACES", "true"},
	} {
		t.Run(test.name+"/"+test.value, func(t *testing.T) {
			folder := t.TempDir()
			writeEnv(t, folder, test.key+"=old\n")
			yard := commandtest.New(t)
			run := yard.Run("ws", "set", "--dir", folder, test.name, test.value)
			if runtime.GOOS == "windows" && test.name == "bind-address" && test.value == "" {
				// Windows needs the LAN address, so it refuses to clear it.
				if run.ExitCode != 1 || !strings.Contains(run.Stderr, "bind-address") || readInitFile(t, folder, ".env") != test.key+"=old\n" {
					t.Fatalf("clear bind-address: %+v", run)
				}
				return
			}
			if run.ExitCode != 0 || readInitFile(t, folder, ".env") != test.key+"="+test.value+"\n" {
				t.Fatalf("set: %+v", run)
			}
			if len(yard.Process.Commands) != 0 {
				t.Fatalf("set ran a process: %v", yard.Process.Commands)
			}
		})
	}
}

func TestWSSetRefusesUnknownSettingAndBadPorts(t *testing.T) {
	for _, args := range [][]string{
		{"gossip-key", "secret"}, {"unknown", "value"},
		{"http-port", "0"}, {"http-port", "-1"}, {"http-port", "abc"},
		{"peers", "10.0.0.1:0"}, {"peers", "10.0.0.1:65536"},
	} {
		folder := t.TempDir()
		const before = "YARD_HTTP_PORT=80\nYARD_PEERS=\n"
		writeEnv(t, folder, before)
		run := commandtest.New(t).Run(append([]string{"ws", "set", "--dir", folder}, args...)...)
		if run.ExitCode != 1 || readInitFile(t, folder, ".env") != before {
			t.Fatalf("bad setting: %+v", run)
		}
	}
}

func TestWSSetTraefikKeepsProfilesAndChangesBothSettings(t *testing.T) {
	for _, profiles := range []string{"dashboard,traefik,metrics", "dashboard,metrics", "traefik", ""} {
		t.Run(profiles, func(t *testing.T) {
			folder := t.TempDir()
			writeEnv(t, folder, "# Keep my profiles\nCOMPOSE_PROFILES="+profiles+"\nYARD_TRAEFIK_ENABLED=true\nYARD_WORKSTATION=ws-a\n")
			yard := commandtest.New(t)
			before := readInitFile(t, folder, ".env")
			bad := yard.Run("ws", "set", "--dir", folder, "traefik", "false")
			if bad.ExitCode != 1 || !strings.Contains(bad.Stderr, "on or off") || readInitFile(t, folder, ".env") != before {
				t.Fatalf("invalid toggle changed settings: %+v", bad)
			}
			off := yard.Run("ws", "set", "--dir", folder, "traefik", "off")
			if off.ExitCode != 0 || !strings.Contains(off.Stdout, "lvh-yard remove traefik-admin") {
				t.Fatalf("off: %+v", off)
			}
			wantProfiles := "dashboard,metrics"
			if profiles == "traefik" || profiles == "" {
				wantProfiles = ""
			}
			want := "# Keep my profiles\nCOMPOSE_PROFILES=" + wantProfiles + "\nYARD_TRAEFIK_ENABLED=false\nYARD_WORKSTATION=ws-a\n"
			if got := readInitFile(t, folder, ".env"); got != want {
				t.Fatalf("off settings: %q, want %q", got, want)
			}
			for range 2 {
				on := yard.Run("ws", "set", "--dir", folder, "traefik", "on")
				if on.ExitCode != 0 {
					t.Fatalf("on: %+v", on)
				}
				wantOn := "traefik"
				if wantProfiles != "" {
					wantOn = wantProfiles + ",traefik"
				}
				want = "# Keep my profiles\nCOMPOSE_PROFILES=" + wantOn + "\nYARD_TRAEFIK_ENABLED=true\nYARD_WORKSTATION=ws-a\n"
				if got := readInitFile(t, folder, ".env"); got != want {
					t.Fatalf("on settings: %q, want %q", got, want)
				}
			}
		})
	}
}

func TestWSSetTraefikDoesNotWriteHalfAChange(t *testing.T) {
	folder := t.TempDir()
	const before = "YARD_TRAEFIK_ENABLED=true\nYARD_WORKSTATION=ws-a\n"
	writeEnv(t, folder, before)
	run := commandtest.New(t).Run("ws", "set", "--dir", folder, "traefik", "off")
	if run.ExitCode != 1 || readInitFile(t, folder, ".env") != before {
		t.Fatalf("partial Traefik toggle: %+v", run)
	}
}

func TestWSSetSaysWhatToRunNext(t *testing.T) {
	folder := t.TempDir()
	writeEnv(t, folder, "YARD_HTTP_PORT=80\nYARD_TRAEFIK_ENABLED=true\nCOMPOSE_PROFILES=traefik\n")
	yard := commandtest.New(t)
	port := yard.Run("ws", "set", "--dir", folder, "http-port", "8080")
	if port.ExitCode != 0 || !strings.Contains(port.Stdout, "Saved .env.") || !strings.Contains(port.Stdout, "compose recreate") {
		t.Fatalf("http-port: %+v", port)
	}
	if strings.Contains(port.Stdout, "stop traefik") || strings.Contains(port.Stdout, "traefik-admin") {
		t.Fatalf("a port change talks about Traefik: %s", port.Stdout)
	}
	off := yard.Run("ws", "set", "--dir", folder, "traefik", "off")
	if !strings.Contains(off.Stdout, "--profile traefik stop traefik") || !strings.Contains(off.Stdout, "compose recreate") {
		t.Fatalf("traefik off must say how to stop it: %+v", off)
	}
	on := yard.Run("ws", "set", "--dir", folder, "traefik", "on")
	if on.ExitCode != 0 || strings.Contains(on.Stdout, "stop traefik") || strings.Contains(on.Stdout, "traefik-admin") {
		t.Fatalf("traefik on: %+v", on)
	}
}

func TestWSSetWithoutAWorkstationSaysToRunInit(t *testing.T) {
	for name, folder := range map[string]string{"no folder": filepath.Join(t.TempDir(), "missing"), "no .env": t.TempDir()} {
		run := commandtest.New(t).Run("ws", "set", "--dir", folder, "http-port", "8080")
		if run.ExitCode != 1 || !strings.Contains(run.Stderr, "no Workstation in") || !strings.Contains(run.Stderr, "ws init") {
			t.Fatalf("%s: %+v", name, run)
		}
	}
}

func TestWSSetFalseOnAnotherSettingDoesNotTalkAboutTraefik(t *testing.T) {
	folder := t.TempDir()
	writeEnv(t, folder, "YARD_STACK_TRACES=true\n")
	run := commandtest.New(t).Run("ws", "set", "--dir", folder, "stack-traces", "false")
	if run.ExitCode != 0 || strings.Contains(run.Stdout, "traefik-admin") {
		t.Fatalf("stack-traces false: %+v", run)
	}
}
