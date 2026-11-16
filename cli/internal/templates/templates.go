package templates

import (
	_ "embed"
	"fmt"
	"strings"
)

//go:embed traefik-windows.sh
var windowsTraefik string

func WindowsTraefik() string {
	return windowsTraefik
}

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

// Compose is the compose.yaml of this platform, with the dashboard image pinned to version.
func Compose(version string) (string, error) {
	const image = "ghcr.io/ppalazon/lvh-yard-dashboard:"
	if !strings.Contains(platformCompose, image+"latest}") {
		return "", fmt.Errorf("compose.yaml no longer has the dashboard image that init edits")
	}
	if version == "" || version == "dev" {
		version = "latest"
	}
	return strings.ReplaceAll(platformCompose, image+"latest}", image+version+"}"), nil
}

// Env is the .env template of this platform.
func Env() string {
	return platformEnv
}

func Dashboard() string {
	return dashboard
}
