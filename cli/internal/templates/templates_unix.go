//go:build !windows

package templates

import _ "embed"

//go:embed compose.yaml
var platformCompose string

//go:embed .env.example
var platformEnv string
