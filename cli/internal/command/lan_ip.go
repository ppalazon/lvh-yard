package command

import (
	"fmt"
	"net"
	"strings"
)

// isGlobalUnicastIPv4 is true for an IPv4 address that other computers can reach. It is false for
// loopback, link-local, multicast and unspecified addresses.
func isGlobalUnicastIPv4(ip net.IP) bool {
	return ip.To4() != nil && ip.IsGlobalUnicast()
}

// defaultRouteSource is the address that the system uses to reach the Internet, or nil when there is
// no default route. A UDP connect selects a source address without sending a packet.
func defaultRouteSource() net.IP {
	conn, err := net.DialUDP("udp4", nil, &net.UDPAddr{IP: net.IPv4(192, 0, 2, 1), Port: 9})
	if err != nil {
		return nil
	}
	defer func() { _ = conn.Close() }()
	return conn.LocalAddr().(*net.UDPAddr).IP
}

// lanCandidate is one IPv4 address of an active adapter. Tunnel is true when the adapter looks like a VPN or
// another tunnel.
type lanCandidate struct {
	IP      net.IP
	Adapter string
	Tunnel  bool
}

// tunnelWords are the parts of an adapter name or description that mark a VPN or another tunnel. A virtual machine's
// own network card is not one: a Windows guest on Hyper-V, VMware or VirtualBox has a real card with such a name.
var tunnelWords = []string{"vpn", "tap", "tun", "wireguard", "wintun", "ppp", "tailscale", "zerotier"}

// isTunnelAdapter is true for a point-to-point adapter, or one that names itself as a VPN or another tunnel.
func isTunnelAdapter(name, description string, flags net.Flags) bool {
	if flags&net.FlagPointToPoint != 0 {
		return true
	}
	text := strings.ToLower(name + " " + description)
	for _, word := range tunnelWords {
		if strings.Contains(text, word) {
			return true
		}
	}
	return false
}

// chooseLANAddress returns the candidate that holds the default route. It never guesses: when no
// candidate holds that route, or the one that does looks like a VPN or another tunnel, the user gives
// the address. A full-tunnel VPN takes the default route, and its address is not a LAN address.
func chooseLANAddress(candidates []lanCandidate, route net.IP) (string, error) {
	var listed []string
	var tunnel *lanCandidate
	for i, candidate := range candidates {
		if route != nil && candidate.IP.Equal(route) {
			if !candidate.Tunnel {
				return candidate.IP.String(), nil
			}
			tunnel = &candidates[i]
			continue
		}
		listed = append(listed, candidate.IP.String())
	}
	reason := "no default route was found"
	if route != nil {
		reason = "the default route is not on a suitable adapter"
	}
	if tunnel != nil {
		reason = fmt.Sprintf("the default route is on %q, which looks like a VPN or tunnel adapter", tunnel.Adapter)
	}
	if len(listed) == 0 {
		listed = []string{"none"}
	}
	return "", fmt.Errorf("choose a LAN IPv4 address with --address: %s; other adapter addresses: %s", reason, strings.Join(listed, ", "))
}
