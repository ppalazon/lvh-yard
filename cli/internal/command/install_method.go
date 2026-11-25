package command

import (
	"fmt"

	"github.com/spf13/cobra"
)

// NewInstallMethod is hidden, because dev/install.sh asks it and --help does not name it. It prints who installed
// this executable, such as "standalone" or the name of a package manager, and an empty line for a build from the
// sources. An executable from before this command fails on it, and the script reads that as "not owned by anyone".
func NewInstallMethod(options *Options) *cobra.Command {
	return &cobra.Command{
		Use:    "install-method",
		Short:  "Prints who installed this executable.",
		Hidden: true,
		Args:   cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			fmt.Fprintln(cmd.OutOrStdout(), options.InstallMethod)
			return nil
		},
	}
}
