package command

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
)

const yardStandaloneHelp = `Leaves the Yard. This Workstation works alone again.

Does:
  1. Makes a new Gossip Key and clears the Peers.
  2. Turns Consul TLS off and deletes the files in tls/consul/.
  3. Makes Consul listen on this computer only.

Next:
  1. Run the "compose recreate" command that it prints.

Note:
  - It keeps the Workstation CA, the Traefik certificate and the Consul data.
  - On native Windows, Consul keeps its LAN address.
  - The other Workstations list this one until their Consul servers drop it.`

func newWSYardStandalone(options *Options, folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "standalone", Short: "Leaves the Yard.", Long: yardStandaloneHelp, Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if err := protectWorkstationFolder(folder()); err != nil {
				return err
			}
			if err := removeConsulTLS(folder()); err != nil {
				return err
			}
			key, err := newGossipKey()
			if err != nil {
				return err
			}
			if err := env.Set("YARD_GOSSIP_KEY", key); err != nil {
				return err
			}
			if err := env.Set("YARD_PEERS", ""); err != nil {
				return err
			}
			if !lanAddressRequired {
				if err := env.Set("YARD_BIND_ADDRESS", loopbackAddress); err != nil {
					return err
				}
			}
			fmt.Fprintln(cmd.OutOrStdout(), "This Workstation is standalone.\n  Gossip Key: new\n  Peers: none\n  Consul TLS: off")
			return saveWSEnv(cmd, folder(), env)
		},
	}
}

// removeConsulTLS turns Consul TLS off and deletes the files of its certificate. The folder stays,
// because Compose mounts it.
func removeConsulTLS(folder string) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	if err := root.Remove(filepath.Join("consul.d", "10-tls.hcl")); err != nil && !os.IsNotExist(err) {
		return err
	}
	for _, name := range roleFiles(roleConsul) {
		if err := root.Remove(filepath.Join("tls", roleConsul, name)); err != nil && !os.IsNotExist(err) {
			return err
		}
	}
	return nil
}
