//go:build windows

package templates

import _ "embed"

//go:embed compose.windows.yaml
var platformCompose string

//go:embed .env.windows.example
var platformEnv string

//go:embed consul-windows.sh
var consulScript string

//go:embed dashboard-windows.sh
var dashboardScript string

//go:embed traefik-windows.sh
var traefikScript string

var platformScripts = []Script{
	{"scripts/consul.sh", consulScript},
	{"scripts/dashboard.sh", dashboardScript},
	{"scripts/traefik.sh", traefikScript},
}
