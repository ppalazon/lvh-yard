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
	tests := []struct {
		name       string
		candidates []net.IP
		route      net.IP
		want       string
		refusal    string
	}{
		{name: "the default route adapter wins over another adapter", candidates: []net.IP{ip("10.0.2.15"), ip("192.168.1.20")}, route: ip("192.168.1.20"), want: "192.168.1.20"},
		{name: "the only candidate holds the default route", candidates: []net.IP{ip("192.168.1.20")}, route: ip("192.168.1.20"), want: "192.168.1.20"},
		{name: "the only candidate is not on the default route", candidates: []net.IP{ip("192.168.56.1")}, route: ip("172.19.144.1"), refusal: "the default route is not on a suitable adapter"},
		{name: "a lone candidate without a default route is not guessed", candidates: []net.IP{ip("192.168.56.1")}, refusal: "no default route was found"},
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
