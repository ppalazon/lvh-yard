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

func newWSYard(folder func() string) *cobra.Command {
	yard := &cobra.Command{
		Use: "yard", Short: "Manages this Workstation's Yard membership.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	yard.AddCommand(newWSYardShare(folder), newWSJoin(folder))
	return yard
}

func newWSYardShare(folder func() string) *cobra.Command {
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
			fmt.Fprintf(out, "Yard invitation from %s.\nKeep these instructions private: they contain the Yard's Gossip Key.\n\n", env.get("YARD_WORKSTATION"))
			fmt.Fprintf(out, "On this Workstation, allow TCP port %s and TCP and UDP port %s from the other Workstations.\n", env.valueOrDefault("YARD_CONSUL_SERVER_PORT"), env.valueOrDefault("YARD_CONSUL_SERF_WAN_PORT"))
			if peer == "<this-workstation-lan-ip>" {
				fmt.Fprintf(out, "Replace <this-workstation-lan-ip> with this computer's LAN address, or run lvh-yard ws --dir %s yard share --address <ip>.\n", quoteShell(folder()))
			}
			if ip := net.ParseIP(env.get("YARD_BIND_ADDRESS")); ip != nil && ip.IsLoopback() {
				fmt.Fprintf(out, "Consul is bound to loopback. Before another computer joins, use lvh-yard ws --dir %s set bind-address '<lan-ip>' and apply its printed commands.\n", quoteShell(folder()))
			}
			if ip := net.ParseIP(peer); ip != nil && ip.IsLoopback() {
				fmt.Fprintln(out, "This Peer address works only for Workstations on the same computer.")
			}
			peer += ":" + env.valueOrDefault("YARD_CONSUL_SERF_WAN_PORT")
			fmt.Fprintf(out, "\nOn a new Workstation:\n  lvh-yard ws init --workstation '<new-workstation>' --base-domain '<base-domain>' --gossip-key %s --peers %s\n", quoteShell(key), quoteShell(peer))
			fmt.Fprintf(out, "\nOn an already initialized Workstation instead:\n  lvh-yard ws yard join --gossip-key %s --peers %s\n", quoteShell(key), quoteShell(peer))
			if tlsEnabled {
				printYardTLSInvitation(out, folder(), env)
			} else {
				fmt.Fprintln(out, "\nConsul TLS is off here. No certificate bundle is needed for Yard membership. The joining Workstation must also have Consul TLS off.")
				if env.get("YARD_HTTPS_PORT") != "" {
					fmt.Fprintf(out, "For browser HTTPS trust only, share the public CA certificate at %s. This does not enable Consul TLS.\n", filepath.Join(folder(), "tls", "ca.crt"))
				}
			}
			fmt.Fprintln(out, "\nAfter the joining Workstation has the right settings and TLS files, run its printed recreation command, or lvh-yard ws start for a first start.\nThen run lvh-yard status and open its dashboard.")
			return nil
		},
	}
	cmd.Flags().StringVar(&address, "address", "", "Uses this Workstation's reachable IPv4 address in the joining commands.")
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
	fmt.Fprintf(out, "\nConsul TLS is on. Every Workstation must use this Yard's CA.\nThe public CA certificate is %s. It is safe to copy; verify its source before trusting it.\n", filepath.Join(folder, "tls", "ca.crt"))
	fmt.Fprintln(out, "\nAsk the owner of that CA to sign a separate Workstation Certificate.\nOn the Workstation that holds the CA key, run:")
	fmt.Fprintf(out, "  lvh-yard ws --dir '<ca-workstation-folder>' tls cert create --workstation '<new-workstation>' --base-domain '<base-domain>' --address '<new-workstation-lan-ip>' --address %s\n", quoteShell("*."+env.get("YARD_WORKSTATION")+".<base-domain>"))
	fmt.Fprintln(out, "With a company CA, ask its issuer for the bundle instead.\nInclude server.<new-workstation>.consul, localhost, 127.0.0.1, *.<base-domain>, the LAN address and remote Route wildcards.\nThe certificate must allow both server and client authentication.")
	fmt.Fprintln(out, "Copy only ca.crt, workstation.crt and workstation.key from certificates/<new-workstation>/ to that Workstation through a private channel.\nNever copy ca.key or this Workstation's certificate key.")
	fmt.Fprintln(out, "\nBefore starting the joining Workstation, import its received bundle:")
	printYardTLSImport(out, "", "<received-bundle>")
	fmt.Fprintln(out, "Each Workstation needs wildcard SANs for the remote Routes it serves. Existing Workstations may need new certificates for *.<new-workstation>.<their-base-domain>.")
	fmt.Fprintln(out, "\nFor browser trust, import only the public ca.crt into the browser's trusted certificate authorities.\nClient Certificates are for people who use protected Routes, not Yard membership. The CA owner can run lvh-yard ws tls client create <person> and share that person's .p12 file and password privately.")
}

func printYardTLSImport(out io.Writer, folder, bundle string) {
	dir := ""
	if folder != "" {
		dir = " --dir " + quoteShell(folder)
	}
	fmt.Fprintf(out, "  lvh-yard ws%s tls import --ca %s --cert %s --key %s\n", dir, quoteShell(filepath.Join(bundle, "ca.crt")), quoteShell(filepath.Join(bundle, "workstation.crt")), quoteShell(filepath.Join(bundle, "workstation.key")))
}

func printYardJoinTLS(out io.Writer, folder string, env *envFile, tlsEnabled bool) {
	fmt.Fprintf(out, "Before applying the Yard settings, check its Consul TLS policy.\nIf it uses TLS, get its public CA and a separate Workstation Certificate for server.%s.consul, then import the received bundle:\n", env.get("YARD_WORKSTATION"))
	printYardTLSImport(out, folder, "<received-bundle>")
	fmt.Fprintln(out, "Never copy the CA key or reuse another Workstation's certificate key. Every Workstation must use the same Yard CA and Consul TLS mode.")
	if tlsEnabled {
		fmt.Fprintf(out, "This Workstation currently uses Consul TLS. If the destination Yard does not, move %s outside consul.d before applying the settings. Its HTTPS files are kept.\n", quoteShell(filepath.Join(folder, "consul.d", "10-tls.hcl")))
	}
	fmt.Fprintln(out)
}

func newWSJoin(folder func() string) *cobra.Command {
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
			printYardJoinTLS(cmd.OutOrStdout(), folder(), env, tlsEnabled)
			return saveWSEnv(cmd, folder(), env)
		},
	}
	cmd.Flags().StringVar(&key, "gossip-key", "", "Uses the Yard's shared Gossip Key.")
	cmd.Flags().StringArrayVar(&peers, "peers", nil, "Sets Peer addresses separated by spaces or commas.")
	cmd.Flags().StringVar(&workstation, "workstation", "", "Refuses a change of the Workstation name.")
	_ = cmd.Flags().MarkHidden("workstation")
	return cmd
}
