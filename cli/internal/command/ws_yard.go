package command

import (
	"fmt"
	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
)

func newWSYard(options *Options, folder func() string) *cobra.Command {
	yard := &cobra.Command{
		Use: "yard", Short: "Manages this Workstation's Yard membership.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	yard.AddCommand(newWSYardShare(options, folder), newWSJoin(options, folder), newWSYardPeers(folder))
	return yard
}

func newWSYardShare(options *Options, folder func() string) *cobra.Command {
	var address string
	cmd := &cobra.Command{
		Use: "share", Short: "Prints the commands and files another Workstation needs to join.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			key := strings.Trim(env.Get("YARD_GOSSIP_KEY"), "\"'")
			if err := checkInitGossipKey(key); err != nil {
				return err
			}
			peer := "<this-workstation-lan-ip>"
			if cmd.Flags().Changed("address") {
				ip := net.ParseIP(address)
				if ip == nil || ip.To4() == nil || ip.IsUnspecified() || (!ip.IsGlobalUnicast() && !ip.IsLoopback()) {
					return fmt.Errorf("--address must be a specific IPv4 address, such as 192.168.1.10")
				}
				peer = ip.String()
			} else if ip := net.ParseIP(env.Get("YARD_BIND_ADDRESS")); ip != nil && ip.To4() != nil && !ip.IsUnspecified() && !ip.IsLoopback() {
				peer = ip.String()
			} else if detected, err := options.Process.LANAddress(); err == nil && detected != "" {
				peer = detected
			}
			tlsEnabled, err := consulTLSEnabled(folder())
			if err != nil {
				return err
			}
			if tlsEnabled {
				ca := filepath.Join(folder(), "tls", "consul", "ca.crt")
				info, err := os.Stat(ca)
				if err != nil {
					return fmt.Errorf("Consul TLS is enabled, but its public CA certificate cannot be read: %w", err)
				}
				if !info.Mode().IsRegular() {
					return fmt.Errorf("the public CA certificate must be a file: %s", ca)
				}
			}
			out := cmd.OutOrStdout()
			ws := wsCommand(folder(), options.WorkstationFolder())
			fmt.Fprintf(out, "Yard invitation from %s.\nKeep these instructions private: they contain the Yard's Gossip Key.\n\n", env.Get("YARD_WORKSTATION"))
			fmt.Fprintf(out, "1. Prepare this Workstation for incoming connections.\n   Peer address: %s:%s\n   Allow TCP port %s and TCP and UDP port %s from other Workstations.\n", peer, env.ValueOrDefault("YARD_CONSUL_SERF_WAN_PORT"), env.ValueOrDefault("YARD_CONSUL_SERVER_PORT"), env.ValueOrDefault("YARD_CONSUL_SERF_WAN_PORT"))
			if peer == "<this-workstation-lan-ip>" {
				fmt.Fprintln(out, "   No LAN IPv4 address could be detected. Set it explicitly:")
			} else {
				fmt.Fprintln(out, "   If this address is not reachable from other Workstations, override it:")
			}
			fmt.Fprintf(out, "      %s yard share --address '<lan-ip>'\n", ws)
			if ip := net.ParseIP(env.Get("YARD_BIND_ADDRESS")); ip != nil && ip.IsLoopback() {
				fmt.Fprintln(out, "   Consul listens only on this computer.")
				if ip := net.ParseIP(peer); ip == nil || !ip.IsLoopback() {
					fmt.Fprintf(out, "   Before another computer joins, change the binding:\n      %s set bind-address %s\n   Apply the commands it prints.\n", ws, quoteShell(peer))
				}
			}
			if ip := net.ParseIP(peer); ip != nil && ip.IsLoopback() {
				fmt.Fprintln(out, "   This Peer works only for Workstations on the same computer.")
			}
			peer += ":" + env.ValueOrDefault("YARD_CONSUL_SERF_WAN_PORT")
			fmt.Fprintln(out, "\n2. On the joining Workstation, not on this one, run ONE of these commands:")
			fmt.Fprintf(out, "\n   A. New Workstation (asks for its name and Base Domain):\n      lvh-yard ws init --gossip-key %s --peers %s\n", quoteShell(key), quoteShell(peer))
			fmt.Fprintf(out, "\n   B. Already initialized Workstation:\n      lvh-yard ws yard join --gossip-key %s --peers %s\n", quoteShell(key), quoteShell(peer))
			if tlsEnabled {
				fmt.Fprintf(out, "\n   Consul TLS is ON here, so the joining Workstation also needs a Consul certificate signed by this Yard's CA.\n   On the Workstation that holds the CA key, run:\n      lvh-yard ws tls ca issue consul '<new-workstation>'\n      lvh-yard ws tls ca install '<new-workstation>' --scp '<user@host>'\n   With a company CA, ask its issuer for the certificate instead.\n")
			} else {
				fmt.Fprintln(out, "\n   Consul TLS is OFF here. Keep it off on the joining Workstation too.")
			}
			fmt.Fprintln(out, "\nThen follow the next steps that the command prints on the joining Workstation.")
			fmt.Fprintf(out, "\nAfter it joins, check the Peers of this Workstation, and save the ones that answer in YARD_PEERS:\n  %[1]s yard peers\n  %[1]s yard peers --sync\n", ws)
			return nil
		},
	}
	cmd.Flags().StringVar(&address, "address", "", "Overrides the detected LAN IPv4 address in the joining commands.")
	return cmd
}

func consulTLSEnabled(folder string) (bool, error) {
	_, err := os.Stat(filepath.Join(folder, "consul.d", "10-tls.hcl"))
	if os.IsNotExist(err) {
		return false, nil
	}
	return err == nil, err
}

func printYardTLSImport(out io.Writer, ws, bundle string) {
	fmt.Fprintf(out, "      %s tls import consul --ca %s --cert %s --key %s\n", ws, quoteShell(filepath.Join(bundle, "ca.crt")), quoteShell(filepath.Join(bundle, "consul.crt")), quoteShell(filepath.Join(bundle, "consul.key")))
}

func printYardJoinTLS(out io.Writer, ws, folder string, env *envfile.File, tlsEnabled bool) {
	fmt.Fprintf(out, "1. Match the destination Yard's Consul TLS mode. Choose ONE:\n\n   A. Destination uses TLS:\n      Get its public CA and a separate Consul certificate for server.%s.consul.\n      Import the received files:\n", env.Get("YARD_WORKSTATION"))
	printYardTLSImport(out, ws, "<received-folder>")
	fmt.Fprintln(out, "      Never copy the CA key or reuse another Workstation's certificate keys.\n\n   B. Destination does not use TLS:")
	if tlsEnabled {
		fmt.Fprintf(out, "      Turn Consul TLS off before applying the settings:\n         %s tls disable consul\n      HTTPS files are kept.\n", ws)
	} else {
		fmt.Fprintln(out, "      No TLS file changes are needed.")
	}
	fmt.Fprintln(out, "\n   Every Workstation must use the same Yard CA and Consul TLS mode.")
	fmt.Fprint(out, "\n2. ")
}

func newWSJoin(options *Options, folder func() string) *cobra.Command {
	var key, workstation string
	var peers []string
	cmd := &cobra.Command{
		Use: "join", Short: "Changes the Yard's Gossip Key and Peers without restarting services.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if cmd.Flags().Changed("workstation") {
				return fmt.Errorf("%s", renameWorkstationHint)
			}
			if err := checkInitGossipKey(key); err != nil {
				return err
			}
			var checked string
			var err error
			if cmd.Flags().Changed("peers") {
				checked, err = checkWSSetting("peers", strings.Join(peers, " "))
				if err != nil {
					return err
				}
			}
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			tlsEnabled, err := consulTLSEnabled(folder())
			if err != nil {
				return err
			}
			if err := env.Set("YARD_GOSSIP_KEY", key); err != nil {
				return err
			}
			if cmd.Flags().Changed("peers") {
				if err := env.Set("YARD_PEERS", checked); err != nil {
					return err
				}
			}
			printYardJoinTLS(cmd.OutOrStdout(), wsCommand(folder(), options.WorkstationFolder()), folder(), env, tlsEnabled)
			return saveWSEnv(cmd, folder(), env)
		},
	}
	cmd.Flags().StringVar(&key, "gossip-key", "", "Uses the Yard's shared Gossip Key.")
	cmd.Flags().StringArrayVar(&peers, "peers", nil, "Sets Peer addresses separated by spaces or commas.")
	cmd.Flags().StringVar(&workstation, "workstation", "", "Refuses a change of the Workstation name.")
	_ = cmd.Flags().MarkHidden("workstation")
	return cmd
}

// wsCommand names the ws command to print. It adds --dir only for a folder that is not the default.
func wsCommand(folder, defaultFolder string) string {
	if filepath.Clean(folder) == filepath.Clean(defaultFolder) {
		return "lvh-yard ws"
	}
	return "lvh-yard ws --dir " + quoteShell(folder)
}
