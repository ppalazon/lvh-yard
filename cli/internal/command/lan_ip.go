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

// chooseLANAddress returns the candidate that holds the default route. It never guesses: when no
// candidate holds that route, the user gives the address.
func chooseLANAddress(candidates []net.IP, route net.IP) (string, error) {
	var listed []string
	for _, candidate := range candidates {
		if route != nil && candidate.Equal(route) {
			return candidate.String(), nil
		}
		listed = append(listed, candidate.String())
	}
	reason := "no default route was found"
	if route != nil {
		reason = "the default route is not on a suitable adapter"
	}
	if len(listed) == 0 {
		listed = []string{"none"}
	}
	return "", fmt.Errorf("choose a LAN IPv4 address with --address: %s; other adapter addresses: %s", reason, strings.Join(listed, ", "))
}
