package command

import (
	"fmt"
	"net"
	"strings"
)

func (systemProcess) LANAddress() (string, error) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return "", err
	}
	var candidates []string
	for _, iface := range interfaces {
		name := strings.ToLower(iface.Name)
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 || strings.Contains(name, "vethernet") || strings.Contains(name, "wsl") || strings.Contains(name, "docker") {
			continue
		}
		addresses, err := iface.Addrs()
		if err != nil {
			return "", err
		}
		for _, address := range addresses {
			network, ok := address.(*net.IPNet)
			if ok && network.IP.To4() != nil && network.IP.IsGlobalUnicast() && !network.IP.IsLinkLocalUnicast() {
				candidates = append(candidates, network.IP.String())
			}
		}
	}
	if len(candidates) != 1 {
		return "", fmt.Errorf("choose a LAN IPv4 address with --address; suitable adapter addresses: %s", strings.Join(candidates, ", "))
	}
	return candidates[0], nil
}
