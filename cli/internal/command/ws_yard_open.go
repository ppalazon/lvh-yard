package command

import (
	"errors"
	"fmt"
	"io/fs"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/spf13/cobra"
)

const yardInitHelp = `Opens this Workstation to a Yard. Run it once, before "ws yard share".

Before:
  - Make the Workstation CA with "lvh-yard ws tls ca create".

Does:
  1. Signs the Consul certificate with the Workstation CA.
  2. Installs it in tls/consul/ and turns Consul TLS on.
  3. Sets the LAN address that Consul listens on.

Next:
  1. Run the "compose recreate" command that it prints.
  2. Run "ws yard share".`

func newWSYardInit(options *Options, folder func() string) *cobra.Command {
	var address string
	var days int
	cmd := &cobra.Command{
		Use: "init", Short: "Opens this Workstation to a Yard.", Long: yardInitHelp,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runYardInit(cmd, options, folder(), address, days)
		},
	}
	cmd.Flags().StringVar(&address, "address", "", "Sets the LAN IPv4 address of Consul (default: the one found).")
	cmd.Flags().IntVar(&days, "days", 365, "Sets the Consul certificate validity in days.")
	addCAKeyPasswordFlags(cmd.Flags(), caKeyFlagUsage, caKeyFileFlagUsage)
	return cmd
}

func runYardInit(cmd *cobra.Command, options *Options, folder, address string, days int) error {
	env, err := readWSEnv(folder)
	if err != nil {
		return err
	}
	ws := wsCommand(folder, options.WorkstationFolder())
	caPEM, err := readCAPEM(folder)
	if errors.Is(err, fs.ErrNotExist) {
		return fmt.Errorf("this Workstation has no Workstation CA. Run:\n  %s tls ca create\nThen run:\n  %s yard init", ws, ws)
	}
	if err != nil {
		return err
	}
	if err := checkOwnYard(folder, caPEM); err != nil {
		return err
	}
	peer, err := yardSharePeer(cmd, options, env, address)
	if err != nil {
		return err
	}
	if peer == unknownLANAddress {
		return fmt.Errorf("no LAN address found for this Workstation. Set it with --address, for example --address 192.168.1.10")
	}
	bind, err := settings.Check("bind-address", peer)
	if err != nil {
		return err
	}
	enabled, err := consulTLSEnabled(folder)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if enabled && strings.Trim(env.Get("YARD_BIND_ADDRESS"), "\"'") == bind {
		fmt.Fprintf(out, "Already open: Consul TLS is on and Consul listens on %s.\nNext:\n  %s yard share <workstation>\n", bind, ws)
		return nil
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	if !enabled {
		p := newPrompter(cmd, options)
		ca, signer, err := readCA(cmd, p, folder)
		if err != nil {
			return err
		}
		if days, err = askDays(cmd, p, days, 365, "certificate validity in days"); err != nil {
			return err
		}
		self := strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")
		if err := signConsul(folder, ca, signer, self, days); err != nil {
			return err
		}
		certPEM, keyPEM, err := readSignedTLSRole(folder, self, roleConsul)
		if err != nil {
			return err
		}
		if err := installConsul(cmd, folder, env, caPEM, certPEM, keyPEM); err != nil {
			return err
		}
	}
	if err := env.Set("YARD_BIND_ADDRESS", bind); err != nil {
		return err
	}
	fmt.Fprintf(out, "Yard open.\n  Consul TLS: on\n  Consul address: %s\n  Firewall: allow TCP %s and TCP+UDP %s from the Yard's computers.\n", bind, env.ValueOrDefault("YARD_CONSUL_SERVER_PORT"), env.ValueOrDefault("YARD_CONSUL_SERF_WAN_PORT"))
	if err := saveWSEnv(cmd, folder, env); err != nil {
		return err
	}
	fmt.Fprintf(out, "Then, for each Workstation that joins:\n  %s yard share <workstation>\n", ws)
	return nil
}
