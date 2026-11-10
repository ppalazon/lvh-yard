package main

import (
	"os"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
)

var version = "dev"

func main() {
	os.Exit(command.Execute(os.Args[1:], command.Options{Version: version}))
}
