package templates

import (
	_ "embed"
	"fmt"
	"strings"
)

//go:embed compose.yaml
var compose string

//go:embed .env.example
var env string

func Compose(version string) (string, error) {
	const image = "ghcr.io/ppalazon/lvh-yard-dashboard:"
	const build = "    build: .\n"
	if !strings.Contains(compose, image+"latest}") || !strings.Contains(compose, build) {
		return "", fmt.Errorf("compose.yaml no longer has the dashboard image or build line that init edits")
	}
	if version == "" || version == "dev" {
		version = "latest"
	}
	return strings.ReplaceAll(strings.ReplaceAll(compose, image+"latest}", image+version+"}"), build, ""), nil
}

func Env() string {
	return env
}
