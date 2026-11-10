package command

import (
	"fmt"
	"slices"
	"strings"

	"github.com/spf13/cobra"
)

var completionShells = []string{"bash", "zsh"}

// NewCompletions is hidden, because the user guide names it and --help does not.
func NewCompletions(options *Options) *cobra.Command {
	return &cobra.Command{
		Use:       "completions <shell>",
		Short:     "Prints a shell completion script.",
		Hidden:    true,
		Args:      cobra.MatchAll(cobra.ExactArgs(1), cobra.OnlyValidArgs),
		ValidArgs: completionShells,
		RunE: func(cmd *cobra.Command, args []string) error {
			root := cmd.Root()
			if args[0] == "bash" {
				return root.GenBashCompletionV2(cmd.OutOrStdout(), true)
			}
			return root.GenZshCompletion(cmd.OutOrStdout())
		},
		ValidArgsFunction: func(*cobra.Command, []string, string) ([]string, cobra.ShellCompDirective) {
			return completionShells, cobra.ShellCompDirectiveNoFileComp
		},
	}
}

// finishCommands adds the -V/--version flag every command has, and the fixed values the shell completes.
func finishCommands(root *cobra.Command) {
	addVersionFlags(root)
	completeValues(root, []string{"catalog"}, "state", []string{"unpublished", "unavailable", "unhealthy", "degraded", "healthy"})
	completeValues(root, []string{"publish"}, "scope", []string{"local", "yard"})
	completeValues(root, []string{"publish"}, "access-log", []string{"on", "off"})
	completeValues(root, []string{"register", "static"}, "check", []string{"tcp", "http"})
}

// Cobra gives --version only to the root, and the short flag -v. Every command here has -V.
func addVersionFlags(cmd *cobra.Command) {
	cmd.Version = cmd.Root().Version
	if cmd.Flags().Lookup("version") == nil {
		cmd.Flags().BoolP("version", "V", false, "Prints the version.")
	}
	for _, child := range cmd.Commands() {
		addVersionFlags(child)
	}
}

func completeValues(root *cobra.Command, path []string, flag string, values []string) {
	cmd, rest, err := root.Find(path)
	if err != nil || len(rest) > 0 || cmd.Flags().Lookup(flag) == nil {
		panic(fmt.Sprintf("completion: no flag --%s on %s", flag, strings.Join(path, " ")))
	}
	complete := func(*cobra.Command, []string, string) ([]string, cobra.ShellCompDirective) {
		return slices.Clone(values), cobra.ShellCompDirectiveNoFileComp
	}
	if err := cmd.RegisterFlagCompletionFunc(flag, complete); err != nil {
		panic(err)
	}
}
