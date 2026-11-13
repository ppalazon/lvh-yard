package settings_test

import (
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/settings"
)

func TestCheck(t *testing.T) {
	for _, tc := range []struct {
		name, value, want string
		bad               bool
	}{
		{"traefik", "on", "true", false},
		{"traefik", "off", "false", false},
		{"traefik", "yes", "", true},
		{"base-domain", "lvh.me", "lvh.me", false},
		{"base-domain", "dev.team.example.com", "dev.team.example.com", false},
		{"base-domain", "Lvh.me", "", true},
		{"base-domain", "-a.me", "", true},
		{"base-domain", "", "", true},
		{"peers", "10.0.0.2", "10.0.0.2", false},
		{"peers", " 10.0.0.2:8302,10.0.0.3 ", "10.0.0.2:8302 10.0.0.3", false},
		{"peers", "", "", false},
		{"peers", "10.0.0.2:0", "", true},
		{"peers", "10.0.0.2:65536", "", true},
		{"peers", "10.0.0.2:x", "", true},
		{"peers", "::1", "", true},
		{"peers", "example.com", "", true},
		{"bind-address", "", "", false},
		{"bind-address", "0.0.0.0", "0.0.0.0", false},
		{"bind-address", "::1", "", true},
		{"traefik-bind-address", "", "", true},
		{"dashboard-bind-address", "127.0.0.1", "127.0.0.1", false},
		{"dashboard-bind-address", "localhost", "", true},
		{"http-port", "80", "80", false},
		{"http-port", "0", "", true},
		{"http-port", "65535", "65535", false},
		{"http-port", "65536", "", true},
		{"http-port", "", "", true},
		{"https-port", "", "", false},
		{"https-port", "443", "443", false},
		{"consul-serf-wan-port", "8302", "8302", false},
		{"consul-serf-wan-port", "x", "", true},
		{"out-of-range", "", "", false},
		{"out-of-range", "10.0.0.0/8, 192.168.0.0/16", "10.0.0.0/8, 192.168.0.0/16", false},
		{"out-of-range", "10.0.0.0", "", true},
		{"stack-traces", "true", "true", false},
		{"stack-traces", "false", "false", false},
		{"stack-traces", "on", "", true},
	} {
		got, err := settings.Check(tc.name, tc.value)
		if tc.bad != (err != nil) || got != tc.want {
			t.Errorf("Check(%s, %q) = %q, %v; want %q, bad=%v", tc.name, tc.value, got, err, tc.want, tc.bad)
		}
	}
}

func TestCheckSaysWhatTheSettingAccepts(t *testing.T) {
	_, err := settings.Check("http-port", "x")
	if err == nil || err.Error() != "invalid http-port: use a port from 1 to 65535" {
		t.Fatalf("error = %v", err)
	}
	_, err = settings.Check("traefik", "x")
	if err == nil || err.Error() != "invalid traefik: use on or off" {
		t.Fatalf("error = %v", err)
	}
}

func TestEveryNameHasAKeyAndARule(t *testing.T) {
	names := []string{"base-domain", "peers", "bind-address", "traefik-bind-address", "dashboard-bind-address", "http-port", "https-port", "traefik-admin-port", "dashboard-port", "consul-http-port", "consul-dns-port", "consul-grpc-tls-port", "consul-server-port", "consul-serf-lan-port", "consul-serf-wan-port", "out-of-range", "stack-traces", "traefik"}
	for _, name := range names {
		key, ok := settings.EnvKey(name)
		if !ok || !strings.HasPrefix(key, "YARD_") {
			t.Errorf("EnvKey(%s) = %q, %v", name, key, ok)
		}
		if _, err := settings.Check(name, "\x00"); err == nil || strings.HasSuffix(err.Error(), "use ") {
			t.Errorf("%s has no rule: %v", name, err)
		}
	}
	if _, ok := settings.EnvKey("workstation"); ok {
		t.Error("workstation is a setting")
	}
}

func TestNames(t *testing.T) {
	for domain, want := range map[string]bool{"lvh.me": true, "a": true, "A": false, "a..b": false} {
		if got := settings.ValidBaseDomain(domain); got != want {
			t.Errorf("ValidBaseDomain(%q) = %v", domain, got)
		}
	}
	for name, want := range map[string]bool{"ws-a": true, "ws.a": false, "-a": false, strings.Repeat("a", 63): true, strings.Repeat("a", 64): false} {
		if got := settings.ValidWorkstationName(name); got != want {
			t.Errorf("ValidWorkstationName(%q) = %v", name, got)
		}
	}
}
