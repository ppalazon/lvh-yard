//go:build !windows

package command

import (
	"errors"
	"net"
)

func (systemProcess) LANAddress() (string, error) {
	// Prefer the default route to avoid guessing among host and bridge interfaces.
	if ip := defaultRouteSource(); isGlobalUnicastIPv4(ip) {
		return ip.String(), nil
	}

	interfaces, err := net.Interfaces()
	if err != nil {
		return "", err
	}
	var candidate net.IP
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addresses, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, address := range addresses {
			network, ok := address.(*net.IPNet)
			if !ok || !isGlobalUnicastIPv4(network.IP) {
				continue
			}
			if candidate != nil && !candidate.Equal(network.IP) {
				return "", errors.New("more than one active IPv4 address and no default route")
			}
			candidate = network.IP
		}
	}
	if candidate == nil {
		return "", errors.New("no usable LAN IPv4 address")
	}
	return candidate.String(), nil
}
