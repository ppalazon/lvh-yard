//go:build windows

package templates

import _ "embed"

//go:embed compose.windows.yaml
var platformCompose string

//go:embed .env.windows.example
var platformEnv string
