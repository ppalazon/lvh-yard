package command_test

import (
	"io"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
)

// Every command of the Java CLI with its public and hidden options, by name.
var javaCommands = map[string][]string{
	"":                   {"dashboard"},
	"init":               {"dir", "workstation", "peers", "gossip-key", "base-domain", "force", "upgrade", "reinit", "docker-socket", "hostname", "terminal"},
	"catalog":            {"workstation", "state", "details"},
	"service":            nil,
	"service create":     nil,
	"service delete":     nil,
	"register":           nil,
	"register static":    {"port", "host", "check", "path", "interval", "timeout", "tag", "meta"},
	"register container": {"port", "service"},
	"register compose":   {"port", "service"},
	"containers":         {"details"},
	"remove":             {"id", "port", "host", "container", "compose-project", "compose-service"},
	"publish":            {"scope", "access-log", "profile", "no-profiles"},
	"priority":           {"on", "off"},
	"unpublish":          nil,
	"alias":              nil,
	"alias add":          {"workstation", "service"},
	"alias list":         nil,
	"alias remove":       {"workstation", "service"},
	"profiles":           nil,
	"profiles show":      nil,
	"profiles set":       nil,
	"profiles edit":      nil,
	"profiles remove":    nil,
	"export":             {"output"},
	"import":             {"update", "dry-run"},
	"test":               {"workstation"},
	"status":             {"strict"},
	"completions":        nil,
}

func findCommand(root *cobra.Command, path string) *cobra.Command {
	cmd := root
	if path == "" {
		return cmd
	}
	for _, name := range strings.Fields(path) {
		var next *cobra.Command
		for _, child := range cmd.Commands() {
			if child.Name() == name {
				next = child
			}
		}
		if next == nil {
			return nil
		}
		cmd = next
	}
	return cmd
}

func TestEveryJavaCommandAndOptionExists(t *testing.T) {
	root := command.NewRoot(command.Options{Process: &commandtest.Process{}, Out: io.Discard, Err: io.Discard})
	for path, options := range javaCommands {
		cmd := findCommand(root, path)
		if cmd == nil {
			t.Errorf("command %q is missing", path)
			continue
		}
		// Cobra adds -h/--help when a command runs, so add it before the lookup.
		cmd.InitDefaultHelpFlag()
		wanted := append([]string{"help", "version"}, options...)
		for _, name := range wanted {
			var flag *pflag.Flag
			if flag = cmd.Flags().Lookup(name); flag == nil {
				flag = cmd.InheritedFlags().Lookup(name)
			}
			if flag == nil {
				t.Errorf("command %q lacks --%s", path, name)
			}
		}
		for short, long := range map[string]string{"h": "help", "V": "version"} {
			if flag := cmd.Flags().ShorthandLookup(short); flag == nil || flag.Name != long {
				t.Errorf("command %q lacks -%s for --%s", path, short, long)
			}
		}
		if cmd.Flags().ShorthandLookup("v") != nil {
			t.Errorf("command %q has -v, which Java does not", path)
		}
	}
	if flag := findCommand(root, "export").Flags().ShorthandLookup("o"); flag == nil || flag.Name != "output" {
		t.Error("export lacks -o for --output")
	}
}

func TestEveryCommandPrintsTheVersionWithoutADashboard(t *testing.T) {
	for path := range javaCommands {
		yard := commandtest.New(t)
		yard.Version = "1.2.3"
		args := append(strings.Fields(path), "-V")
		run := yard.Run(args...)
		if run.ExitCode != 0 || run.Stdout != "lvh-yard 1.2.3\n" || run.Stderr != "" {
			t.Errorf("%v: %+v", args, run)
		}
		args = append(strings.Fields(path), "--version", "--dashboard", "http://127.0.0.1:1")
		if run := yard.Run(args...); run.ExitCode != 0 || run.Stdout != "lvh-yard 1.2.3\n" {
			t.Errorf("%v: %+v", args, run)
		}
	}
	if run := commandtest.New(t).Run("-v"); run.ExitCode == 0 {
		t.Errorf("-v must not work: %+v", run)
	}
}
