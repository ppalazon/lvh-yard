package command

import (
	"fmt"
	"io"
	"os"

	"github.com/spf13/cobra"
)

type Options struct {
	Version string
	Process Process
	Out     io.Writer
	Err     io.Writer

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
	if options.Out == nil {
		options.Out = os.Stdout
	}
	if options.Err == nil {
		options.Err = os.Stderr
	}
	root := &cobra.Command{
		Use:           "lvh-yard",
		Short:         "Manages Services on this Workstation.",
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
	for _, group := range groups {
		root.AddCommand(group(&options))
	}
	return root
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
		if err != errStatusFailed && err != errReachabilityFailed {
			fmt.Fprintln(root.ErrOrStderr(), err)
		}
		return 1
	}
	return 0
}
