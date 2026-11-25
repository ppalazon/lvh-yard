package command

import (
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/ppalazon/lvh-yard/cli/internal/update"
	"github.com/spf13/cobra"
)

type Options struct {
	Version string
	// InstallMethod says who installed this executable. update replaces it only when it is InstallMethodStandalone.
	InstallMethod string
	Process       Process
	Out           io.Writer
	Err           io.Writer
	// Dashboard builds the dashboard of an address. The default is the HTTP client.
	Dashboard func(address string) Dashboard
	// Releases is the address of the GitHub releases that update reads. Tests give their own server.
	Releases string
	// Executable is the path of this executable, which update replaces. The default is the running one.
	Executable func() (string, error)
	// HTTP is the client that update downloads with.
	HTTP *http.Client
	// ConsulHTTP is the client that ws yard peers asks Consul with. The default has a 5 s timeout.
	ConsulHTTP *http.Client

	dashboard string
}

// Group constructors share the root's flags and process.
func NewRoot(options Options, groups ...func(*Options) *cobra.Command) *cobra.Command {
	if options.Version == "" {
		options.Version = "dev"
	}
	if options.Process == nil {
		options.Process = SystemProcess()
	}
	if options.Dashboard == nil {
		options.Dashboard = newDashboard
	}
	if options.Out == nil {
		options.Out = os.Stdout
	}
	if options.Err == nil {
		options.Err = os.Stderr
	}
	if options.Releases == "" {
		options.Releases = update.Releases
	}
	if options.Executable == nil {
		options.Executable = runningExecutable
	}
	if options.HTTP == nil {
		options.HTTP = update.Client
	}
	if options.ConsulHTTP == nil {
		options.ConsulHTTP = &http.Client{Timeout: 5 * time.Second}
	}
	root := &cobra.Command{
		Use:           "lvh-yard",
		Short:         "Manages the Services of this Workstation.",
		Version:       options.Version,
		Args:          cobra.NoArgs,
		SilenceErrors: true,
		SilenceUsage:  true,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
		CompletionOptions: cobra.CompletionOptions{DisableDefaultCmd: true},
	}
	root.SetIn(options.Process.Input())
	root.SetOut(options.Out)
	root.SetErr(options.Err)
	root.SetVersionTemplate("lvh-yard {{.Version}}\n")
	root.PersistentFlags().StringVar(&options.dashboard, "dashboard", "", "Uses the dashboard at this URL.")
	root.AddCommand(NewStatus(&options))
	root.AddCommand(NewCatalog(&options))
	root.AddCommand(NewReachability(&options))
	root.AddCommand(NewService(&options), NewRegister(&options), NewRemove(&options))
	root.AddCommand(NewProfiles(&options))
	root.AddCommand(NewExport(&options), NewImport(&options))
	root.AddCommand(NewWS(&options), NewUpdate(&options))
	root.AddCommand(NewContainers(&options))
	root.AddCommand(NewPublish(&options), NewUnpublish(&options), NewPriority(&options))
	root.AddCommand(NewAlias(&options))
	root.AddCommand(NewCompletions(&options), NewInstallMethod(&options))
	for _, group := range groups {
		root.AddCommand(group(&options))
	}
	finishCommands(root)
	return root
}

// runningExecutable is the path of this executable with symbolic links followed, so that update replaces the file
// and not a link to it.
func runningExecutable() (string, error) {
	path, err := os.Executable()
	if err != nil {
		return "", err
	}
	return filepath.EvalSymlinks(path)
}

func Execute(args []string, options Options) int {
	return Run(NewRoot(options), args)
}

func Run(root *cobra.Command, args []string) int {
	if args == nil {
		args = []string{}
	}
	root.SetArgs(args)
	if err := root.Execute(); err != nil {
		if exit, ok := errors.AsType[*processExit](err); ok {
			return exit.code
		}
		if !errors.Is(err, errStatusFailed) && !errors.Is(err, errReachabilityFailed) {
			fmt.Fprintln(root.ErrOrStderr(), err)
		}
		return 1
	}
	return 0
}
