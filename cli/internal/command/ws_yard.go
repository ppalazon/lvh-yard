package command

import (
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
)

func newWSYard(options *Options, folder func() string) *cobra.Command {
	yard := &cobra.Command{
		Use: "yard", Short: "Manages the Yard of this Workstation.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	yard.AddCommand(newWSYardInit(options, folder), newWSYardShare(options, folder), newWSJoin(options, folder), newWSYardStandalone(options, folder), newWSYardPeers(folder))
	return yard
}

func consulTLSEnabled(folder string) (bool, error) {
	root, err := os.OpenRoot(folder)
	if os.IsNotExist(err) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	defer func() { _ = root.Close() }()
	_, err = root.Stat(filepath.Join("consul.d", "10-tls.hcl"))
	if os.IsNotExist(err) {
		return false, nil
	}
	return err == nil, err
}

// wsCommand names the ws command to print. It adds --dir only for a folder that is not the default.
func wsCommand(folder, defaultFolder string) string {
	if filepath.Clean(folder) == filepath.Clean(defaultFolder) {
		return "lvh-yard ws"
	}
	return "lvh-yard ws --dir " + quoteShell(folder)
}
