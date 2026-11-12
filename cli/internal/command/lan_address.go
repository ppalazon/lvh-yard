package command

import (
	"errors"
	"net"
)

func (systemProcess) LANAddress() (string, error) {
	// Prefer the default route to avoid guessing among host and bridge interfaces.
	// UDP connect selects a source address without sending a packet.
	conn, err := net.DialUDP("udp4", nil, &net.UDPAddr{IP: net.IPv4(192, 0, 2, 1), Port: 9})
	if err == nil {
		ip := conn.LocalAddr().(*net.UDPAddr).IP
		_ = conn.Close()
		if ip.To4() != nil && ip.IsGlobalUnicast() {
			return ip.String(), nil
		}
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
			if !ok || network.IP.To4() == nil || !network.IP.IsGlobalUnicast() {
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
