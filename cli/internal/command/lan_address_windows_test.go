package command_test

import (
	"errors"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"unsafe"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"golang.org/x/sys/windows"
)

func TestWindowsInitTakesTheDetectedAddressWithoutATerminal(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.LANIP = "192.168.1.20"
	folder := filepath.Join(t.TempDir(), "private")
	run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native")
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	if text, err := os.ReadFile(filepath.Join(folder, ".env")); err != nil || !strings.Contains(string(text), "YARD_BIND_ADDRESS=192.168.1.20") {
		t.Fatalf("bind address: %s, %v", text, err)
	}
}

// The rule that picks the default route adapter lives in LANAddress. When it refuses, init must ask for
// --address and write nothing, so a VirtualBox, VMware or VPN adapter is never taken silently.
func TestWindowsInitRequiresAddressWhenNoAdapterHoldsTheDefaultRoute(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.LANIP = ""
	yard.Process.LANError = errors.New("choose a LAN IPv4 address with --address: the default route is not on a suitable adapter; other adapter addresses: 192.168.56.1")
	folder := filepath.Join(t.TempDir(), "private")
	run := yard.Run("ws", "init", "--dir", folder, "--workstation", "native")
	if run.ExitCode == 0 || !strings.Contains(run.Stderr, "--address") || !strings.Contains(run.Stdout, "192.168.56.1") {
		t.Fatalf("selection error: %+v", run)
	}
	if _, err := os.Stat(filepath.Join(folder, ".env")); !os.IsNotExist(err) {
		t.Fatalf("init wrote .env: %v", err)
	}
	run = yard.Run("ws", "init", "--dir", folder, "--workstation", "native", "--address", "192.168.56.1")
	if run.ExitCode != 0 {
		t.Fatalf("explicit address: %+v", run)
	}
}

// This runs on the real adapters of the computer: an address that LANAddress returns belongs to an
// adapter that holds a default gateway, and a refusal names --address.
func TestWindowsLANAddressIsOnAnAdapterWithAGateway(t *testing.T) {
	gateway := addressesOfAdaptersWithAGateway(t)
	got, err := command.SystemProcess().LANAddress()
	t.Logf("LANAddress = %q, %v; addresses of adapters with a gateway: %v", got, err, gateway)
	if err != nil {
		if !strings.Contains(err.Error(), "--address") {
			t.Fatalf("a refusal must name --address: %v", err)
		}
		return
	}
	for _, address := range gateway {
		if address.String() == got {
			return
		}
	}
	t.Fatalf("%s is not on an adapter with a gateway: %v", got, gateway)
}

func addressesOfAdaptersWithAGateway(t *testing.T) []net.IP {
	t.Helper()
	size := uint32(15000)
	var buffer []byte
	for {
		buffer = make([]byte, size)
		first := (*windows.IpAdapterAddresses)(unsafe.Pointer(&buffer[0]))
		err := windows.GetAdaptersAddresses(windows.AF_INET, windows.GAA_FLAG_INCLUDE_GATEWAYS, 0, first, &size)
		if errors.Is(err, windows.ERROR_BUFFER_OVERFLOW) {
			continue
		}
		if err != nil {
			t.Fatalf("GetAdaptersAddresses: %v", err)
		}
		break
	}
	var addresses []net.IP
	for adapter := (*windows.IpAdapterAddresses)(unsafe.Pointer(&buffer[0])); adapter != nil; adapter = adapter.Next {
		if adapter.OperStatus != windows.IfOperStatusUp || adapter.FirstGatewayAddress == nil {
			continue
		}
		for unicast := adapter.FirstUnicastAddress; unicast != nil; unicast = unicast.Next {
			if ip := unicast.Address.IP(); ip.To4() != nil {
				addresses = append(addresses, ip)
			}
		}
	}
	return addresses
}
