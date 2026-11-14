package templates

import (
	_ "embed"
	"fmt"
	"runtime"
	"strings"
)

//go:embed compose.yaml
var compose string

//go:embed compose.windows.yaml
var windowsCompose string

//go:embed traefik-windows.sh
var windowsTraefik string

func WindowsTraefik() string {
	return windowsTraefik
}

//go:embed .env.example
var env string

//go:embed 00-lvh-yard.hcl
var consul string

func Consul() string {
	return consul
}

//go:embed 10-tls.hcl
var consulTLS string

func ConsulTLS() string {
	return consulTLS
}

//go:embed 10-tls.yaml
var traefikTLS string

func TraefikTLS() string { return traefikTLS }

//go:embed lvh-yard.yaml
var dashboard string

func Compose(version string) (string, error) {
	source := compose
	if runtime.GOOS == "windows" {
		source = windowsCompose
	}
	const image = "ghcr.io/ppalazon/lvh-yard-dashboard:"
	if !strings.Contains(source, image+"latest}") {
		return "", fmt.Errorf("compose.yaml no longer has the dashboard image that init edits")
	}
	if version == "" || version == "dev" {
		version = "latest"
	}
	return strings.ReplaceAll(source, image+"latest}", image+version+"}"), nil
}

func Env() string {
	if runtime.GOOS == "windows" {
		return windowsEnv
	}
	return env
}

var windowsEnv = func() string {
	if runtime.GOOS != "windows" {
		return ""
	}
	return strings.NewReplacer(
		"# The LAN address that Consul listens on. If it is empty, Consul uses the address of the interface with the default\n# route.",
		"# The native Windows LAN IPv4 address that Consul advertises and new Instances use.\n# Set it before startup. Consul detects its container bind address separately.",
		"# listens on every address, loopback too. Any other address listens only there.",
		"# listens on every address, loopback too. Windows supports only 127.0.0.1 and 0.0.0.0.",
		"# Only for the dashboard image (COMPOSE_PROFILES=dashboard). The dashboard runs as this user and group, not as root. It\n# also joins the group that owns the Docker socket, so that it can read the socket. YARD_DOCKER_GID has no default,\n# because it differs on each computer. To find the values, run id -u, id -g and stat -c %g /var/run/docker.sock.\n#YARD_UID=1000\n#YARD_GID=1000\n#YARD_DOCKER_GID=",
		"# Windows Compose runs Consul and the dashboard as 1000:1000.\n# The dashboard also joins group 0 to read the Docker Desktop socket.\nYARD_UID=1000\nYARD_GID=1000\nYARD_DOCKER_GID=0",
	).Replace(env)
}()

func Dashboard() string {
	return dashboard
}
