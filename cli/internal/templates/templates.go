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

//go:embed lvh-yard.yaml
var dashboard string

func Compose(version string) (string, error) {
	const image = "ghcr.io/ppalazon/lvh-yard-dashboard:"
	if !strings.Contains(compose, image+"latest}") {
		return "", fmt.Errorf("compose.yaml no longer has the dashboard image that init edits")
	}
	if version == "" || version == "dev" {
		version = "latest"
	}
	return strings.ReplaceAll(compose, image+"latest}", image+version+"}"), nil
}

func Env() string {
	return env
}

func Dashboard() string {
	return dashboard
}
