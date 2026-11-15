package command

import (
	"errors"
	"net"
	"strings"
	"unsafe"

	"golang.org/x/sys/windows"
)

func (systemProcess) LANAddress() (string, error) {
	candidates, err := lanCandidates()
	if err != nil {
		return "", err
	}
	return chooseLANAddress(candidates, defaultRouteSource())
}

// lanCandidates lists the IPv4 addresses of the active adapters. It skips the virtual adapters of WSL and Docker.
func lanCandidates() ([]lanCandidate, error) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return nil, err
	}
	descriptions := adapterDescriptions()
	var candidates []lanCandidate
	for _, iface := range interfaces {
		name := strings.ToLower(iface.Name)
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 || strings.Contains(name, "vethernet") || strings.Contains(name, "wsl") || strings.Contains(name, "docker") {
			continue
		}
		addresses, err := iface.Addrs()
		if err != nil {
			return nil, err
		}
		tunnel := isTunnelAdapter(iface.Name, descriptions[iface.Index], iface.Flags)
		for _, address := range addresses {
			if network, ok := address.(*net.IPNet); ok && isGlobalUnicastIPv4(network.IP) {
				candidates = append(candidates, lanCandidate{IP: network.IP, Adapter: iface.Name, Tunnel: tunnel})
			}
		}
	}
	return candidates, nil
}

// adapterDescriptions maps an interface index to the driver description, such as "TAP-Windows Adapter V9".
// net.Interface has only the friendly name. When Windows gives no list, the name alone decides.
func adapterDescriptions() map[int]string {
	size := uint32(15000)
	var buffer []byte
	for {
		buffer = make([]byte, size)
		first := (*windows.IpAdapterAddresses)(unsafe.Pointer(&buffer[0]))
		err := windows.GetAdaptersAddresses(windows.AF_INET, 0, 0, first, &size)
		if errors.Is(err, windows.ERROR_BUFFER_OVERFLOW) {
			continue
		}
		if err != nil {
			return nil
		}
		break
	}
	descriptions := map[int]string{}
	for adapter := (*windows.IpAdapterAddresses)(unsafe.Pointer(&buffer[0])); adapter != nil; adapter = adapter.Next {
		descriptions[int(adapter.IfIndex)] = windows.UTF16PtrToString(adapter.Description)
	}
	return descriptions
}
