package main

import (
	"os"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
)

var version = "dev"

// installMethod says who installed this executable. dev/package-cli.sh sets it to "standalone" for a release archive.
// A package sets its own value, and a build from the sources has none. Only "standalone" updates itself.
var installMethod = ""

func main() {
	os.Exit(command.Execute(os.Args[1:], command.Options{Version: version, InstallMethod: installMethod}))
}
