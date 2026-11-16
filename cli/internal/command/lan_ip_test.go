package command

import (
	"net"
	"strings"
	"testing"
)

// The rule is a pure function, so these cases need no real adapters. The Windows tests call it through
// the real adapter list.
func TestChooseLANAddress(t *testing.T) {
	ip := func(text string) net.IP { return net.ParseIP(text) }
	plain := func(texts ...string) []lanCandidate {
		var candidates []lanCandidate
		for _, text := range texts {
			candidates = append(candidates, lanCandidate{IP: ip(text), Adapter: "Ethernet"})
		}
		return candidates
	}
	tests := []struct {
		name       string
		candidates []lanCandidate
		route      net.IP
		want       string
		refusal    string
	}{
		{name: "the default route adapter wins over another adapter", candidates: plain("10.0.2.15", "192.168.1.20"), route: ip("192.168.1.20"), want: "192.168.1.20"},
		{name: "the only candidate holds the default route", candidates: plain("192.168.1.20"), route: ip("192.168.1.20"), want: "192.168.1.20"},
		{name: "the only candidate is not on the default route", candidates: plain("192.168.56.1"), route: ip("172.19.144.1"), refusal: "the default route is not on a suitable adapter"},
		{name: "a lone candidate without a default route is not guessed", candidates: plain("192.168.56.1"), refusal: "no default route was found"},
		{name: "the default route adapter is a tunnel", candidates: append(plain("192.168.1.20"), lanCandidate{IP: ip("10.8.0.2"), Adapter: "OpenVPN", Tunnel: true}), route: ip("10.8.0.2"), refusal: `the default route is on "OpenVPN", which looks like a VPN or tunnel adapter; other adapter addresses: 192.168.1.20`},
		{name: "a tunnel that does not hold the default route is ignored", candidates: append(plain("192.168.1.20"), lanCandidate{IP: ip("10.8.0.2"), Adapter: "OpenVPN", Tunnel: true}), route: ip("192.168.1.20"), want: "192.168.1.20"},
		{name: "a lone tunnel with the default route lists no other address", candidates: []lanCandidate{{IP: ip("10.8.0.2"), Adapter: "WireGuard", Tunnel: true}}, route: ip("10.8.0.2"), refusal: "other adapter addresses: none"},
		{name: "no candidate", route: ip("192.168.1.20"), refusal: "other adapter addresses: none"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got, err := chooseLANAddress(tc.candidates, tc.route)
			if tc.refusal == "" {
				if err != nil || got != tc.want {
					t.Fatalf("got %q, %v; want %q", got, err, tc.want)
				}
				return
			}
			if err == nil || got != "" || !strings.Contains(err.Error(), "--address") || !strings.Contains(err.Error(), tc.refusal) {
				t.Fatalf("got %q, %v; want a refusal with --address and %q", got, err, tc.refusal)
			}
		})
	}
}

func TestIsTunnelAdapter(t *testing.T) {
	tests := []struct {
		name        string
		description string
		flags       net.Flags
		want        bool
	}{
		{name: "Ethernet", description: "Intel(R) Ethernet Connection I219-V", flags: net.FlagUp | net.FlagBroadcast},
		{name: "Wi-Fi", description: "Intel(R) Wi-Fi 6 AX201 160MHz", flags: net.FlagUp | net.FlagBroadcast},
		{name: "Ethernet", description: "Intel(R) Ethernet Connection I219-V", flags: net.FlagUp | net.FlagPointToPoint, want: true},
		{name: "Ethernet 2", description: "OpenVPN TAP-Windows6", flags: net.FlagUp | net.FlagBroadcast, want: true},
		{name: "Work VPN", flags: net.FlagUp | net.FlagBroadcast, want: true},
		{name: "Ethernet 3", description: "Wintun Userspace Tunnel", flags: net.FlagUp, want: true},
		{name: "wg0", description: "WireGuard Tunnel", flags: net.FlagUp, want: true},
		{name: "Tailscale", flags: net.FlagUp, want: true},
		{name: "Ethernet 4", description: "ZeroTier Virtual Port", flags: net.FlagUp, want: true},
		// A Windows guest on Hyper-V, VMware, VirtualBox or KVM has a real NIC with one of these names, and it holds
		// the default route. It is not a tunnel.
		{name: "Ethernet 3", description: "Microsoft Hyper-V Network Adapter", flags: net.FlagUp | net.FlagBroadcast},
		{name: "Ethernet0", description: "vmxnet3 Ethernet Adapter", flags: net.FlagUp | net.FlagBroadcast},
		{name: "Ethernet", description: "Intel(R) PRO/1000 MT Desktop Adapter", flags: net.FlagUp | net.FlagBroadcast},
		{name: "Ethernet", description: "Red Hat VirtIO Ethernet Adapter", flags: net.FlagUp | net.FlagBroadcast},
		{name: "Ethernet 7", description: "WAN Miniport (PPP)", flags: net.FlagUp, want: true},
		{name: "ETHERNET 8", description: "TUN adapter", flags: net.FlagUp, want: true},
	}
	for _, tc := range tests {
		if got := isTunnelAdapter(tc.name, tc.description, tc.flags); got != tc.want {
			t.Errorf("%q / %q / %v: got %v, want %v", tc.name, tc.description, tc.flags, got, tc.want)
		}
	}
}

func TestIsGlobalUnicastIPv4(t *testing.T) {
	for text, want := range map[string]bool{
		"192.168.1.20": true, "10.0.0.1": true,
		"127.0.0.1": false, "169.254.1.1": false, "224.0.0.1": false, "0.0.0.0": false, "255.255.255.255": false,
		"2001:db8::1": false, "::1": false,
	} {
		if got := isGlobalUnicastIPv4(net.ParseIP(text)); got != want {
			t.Errorf("%s: got %v, want %v", text, got, want)
		}
	}
	if isGlobalUnicastIPv4(nil) {
		t.Error("nil address was accepted")
	}
}
