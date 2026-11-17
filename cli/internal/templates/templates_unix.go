//go:build !windows

package templates

import _ "embed"

//go:embed compose.yaml
var platformCompose string

//go:embed .env.example
var platformEnv string

//go:embed consul.sh
var consulScript string

//go:embed traefik.sh
var traefikScript string

var platformScripts = []Script{
	{"scripts/consul.sh", consulScript},
	{"scripts/traefik.sh", traefikScript},
}
