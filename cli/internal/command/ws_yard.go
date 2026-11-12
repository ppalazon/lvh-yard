package command

import (
	"fmt"
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
			key := strings.Trim(env.get("YARD_GOSSIP_KEY"), "\"'")
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
			} else if ip := net.ParseIP(env.get("YARD_BIND_ADDRESS")); ip != nil && ip.To4() != nil && !ip.IsUnspecified() && !ip.IsLoopback() {
				peer = ip.String()
			} else if detected, err := options.Process.LANAddress(); err == nil && detected != "" {
				peer = detected
			}
			tlsEnabled, err := consulTLSEnabled(folder())
			if err != nil {
				return err
			}
			if tlsEnabled {
				ca := filepath.Join(folder(), "tls", "ca.crt")
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
			fmt.Fprintf(out, "Yard invitation from %s.\nKeep these instructions private: they contain the Yard's Gossip Key.\n\n", env.get("YARD_WORKSTATION"))
			fmt.Fprintf(out, "1. Prepare this Workstation for incoming connections.\n   Peer address: %s:%s\n   Allow TCP port %s and TCP and UDP port %s from other Workstations.\n", peer, env.valueOrDefault("YARD_CONSUL_SERF_WAN_PORT"), env.valueOrDefault("YARD_CONSUL_SERVER_PORT"), env.valueOrDefault("YARD_CONSUL_SERF_WAN_PORT"))
			if peer == "<this-workstation-lan-ip>" {
				fmt.Fprintln(out, "   No LAN IPv4 address could be detected. Set it explicitly:")
			} else {
				fmt.Fprintln(out, "   If this address is not reachable from other Workstations, override it:")
			}
			fmt.Fprintf(out, "      %s yard share --address '<lan-ip>'\n", ws)
			if ip := net.ParseIP(env.get("YARD_BIND_ADDRESS")); ip != nil && ip.IsLoopback() {
				fmt.Fprintln(out, "   Consul listens only on this computer.")
				if ip := net.ParseIP(peer); ip == nil || !ip.IsLoopback() {
					fmt.Fprintf(out, "   Before another computer joins, change the binding:\n      %s set bind-address %s\n   Apply the commands it prints.\n", ws, quoteShell(peer))
				}
			}
			if ip := net.ParseIP(peer); ip != nil && ip.IsLoopback() {
				fmt.Fprintln(out, "   This Peer works only for Workstations on the same computer.")
			}
			peer += ":" + env.valueOrDefault("YARD_CONSUL_SERF_WAN_PORT")
			fmt.Fprintln(out, "\n2. Set up the joining Workstation. Choose ONE:")
			fmt.Fprintf(out, "\n   A. New Workstation (asks for its name and Base Domain):\n      lvh-yard ws init --gossip-key %s --peers %s\n", quoteShell(key), quoteShell(peer))
			fmt.Fprintf(out, "\n   B. Already initialized Workstation:\n      lvh-yard ws yard join --gossip-key %s --peers %s\n", quoteShell(key), quoteShell(peer))
			next := 3
			if tlsEnabled {
				printYardTLSInvitation(out, folder(), env)
				next = 5
			} else {
				fmt.Fprintln(out, "\n   Consul TLS is OFF. Keep it off on the joining Workstation too.\n   No certificate bundle is needed for Yard membership.")
			}
			fmt.Fprintf(out, "\n%d. Start or apply settings on the joining Workstation. Choose ONE:\n\n   A. First start:\n      lvh-yard ws start\n\n   B. Services already running:\n      docker compose --project-directory '<joining-workstation-folder>' up -d --force-recreate\n      Also run any stop command printed during setup.\n", next)
			fmt.Fprintf(out, "\n%d. Check the joining Workstation's status:\n   lvh-yard status\n", next+1)
			fmt.Fprintf(out, "\n%d. Open its dashboard at an address printed during initialization.\n", next+2)
			if tlsEnabled || env.get("YARD_HTTPS_PORT") != "" {
				fmt.Fprintf(out, "   For browser HTTPS trust, import only the public CA certificate:\n      %s\n   Verify its source before trusting it. Browser trust does not enable Consul TLS.\n", filepath.Join(folder(), "tls", "ca.crt"))
			}
			if tlsEnabled {
				fmt.Fprintf(out, "\n%d. Optional: give a person access to protected Routes.\n   Client Certificates are not needed for Yard membership. Choose ONE issuer:\n\n   A. On the Workstation that holds the Yard CA key:\n      lvh-yard ws tls client create '<person>'\n\n   B. With a company CA:\n      Ask its issuer for a Client Certificate instead.\n\n   Share only that person's .p12 file and password through a private channel.\n", next+3)
			}
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

func printYardTLSInvitation(out io.Writer, folder string, env *envFile) {
	fmt.Fprintf(out, "\n3. Get a separate TLS bundle for the joining Workstation.\n   Consul TLS is ON. Every Workstation must use this Yard's CA.\n   Public CA certificate:\n      %s\n   It is safe to copy. Verify its source before trusting it.\n", filepath.Join(folder, "tls", "ca.crt"))
	fmt.Fprintln(out, "\n   Choose ONE issuer:")
	fmt.Fprintln(out, "\n   A. On the Workstation that holds that CA's key:")
	fmt.Fprintf(out, "      lvh-yard ws tls cert create --workstation '<new-workstation>' --base-domain '<base-domain>' --address '<new-workstation-lan-ip>' --address %s\n", quoteShell("*."+env.get("YARD_WORKSTATION")+".<base-domain>"))
	fmt.Fprintln(out, "\n   B. With a company CA:\n      Ask its issuer for the bundle instead.\n      Names: server.<new-workstation>.consul, localhost, 127.0.0.1, *.<base-domain>,\n      the LAN address and remote Route wildcards.\n      Usages: server and client authentication.")
	fmt.Fprintln(out, "\n   Send only these files from certificates/<new-workstation>/ through a private channel:\n      ca.crt\n      workstation.crt\n      workstation.key\n   Never send ca.key or this Workstation's certificate key.\n   Every Workstation needs wildcard SANs for the remote Routes it serves.\n   Existing Workstations may need new certificates for *.<new-workstation>.<their-base-domain>.")
	fmt.Fprintln(out, "\n4. Import the received bundle on the joining Workstation before starting it:")
	printYardTLSImport(out, "lvh-yard ws", "<received-bundle>")
}

func printYardTLSImport(out io.Writer, ws, bundle string) {
	fmt.Fprintf(out, "      %s tls import --ca %s --cert %s --key %s\n", ws, quoteShell(filepath.Join(bundle, "ca.crt")), quoteShell(filepath.Join(bundle, "workstation.crt")), quoteShell(filepath.Join(bundle, "workstation.key")))
}

func printYardJoinTLS(out io.Writer, ws, folder string, env *envFile, tlsEnabled bool) {
	fmt.Fprintf(out, "1. Match the destination Yard's Consul TLS mode. Choose ONE:\n\n   A. Destination uses TLS:\n      Get its public CA and a separate certificate for server.%s.consul.\n      Import the received bundle:\n", env.get("YARD_WORKSTATION"))
	printYardTLSImport(out, ws, "<received-bundle>")
	fmt.Fprintln(out, "      Never copy the CA key or reuse another Workstation's certificate key.\n\n   B. Destination does not use TLS:")
	if tlsEnabled {
		fmt.Fprintf(out, "      Move this file outside consul.d before applying the settings:\n         %s\n      HTTPS files are kept.\n", filepath.Join(folder, "consul.d", "10-tls.hcl"))
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
			if err := env.set("YARD_GOSSIP_KEY", key); err != nil {
				return err
			}
			if cmd.Flags().Changed("peers") {
				if err := env.set("YARD_PEERS", checked); err != nil {
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
