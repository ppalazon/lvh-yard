package command

import (
	"net"
	"strings"
)

func (systemProcess) LANAddress() (string, error) {
	candidates, err := lanCandidates()
	if err != nil {
		return "", err
	}
	return chooseLANAddress(candidates, defaultRouteSource())
}

// lanCandidates lists the IPv4 addresses of the active adapters. It skips the virtual adapters of WSL and Docker.
func lanCandidates() ([]net.IP, error) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return nil, err
	}
	var candidates []net.IP
	for _, iface := range interfaces {
		name := strings.ToLower(iface.Name)
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 || strings.Contains(name, "vethernet") || strings.Contains(name, "wsl") || strings.Contains(name, "docker") {
			continue
		}
		addresses, err := iface.Addrs()
		if err != nil {
			return nil, err
		}
		for _, address := range addresses {
			if network, ok := address.(*net.IPNet); ok && isGlobalUnicastIPv4(network.IP) {
				candidates = append(candidates, network.IP)
			}
		}
	}
	return candidates, nil
}
