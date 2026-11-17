package command

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/certzip"
	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/spf13/cobra"
)

const yardJoinHelp = `Joins the Yard of an Invitation. The Invitation comes from "ws yard share" on another Workstation.

Before:
  - Get the zip file and its password from the sender.
  - The zip file must name this Workstation.

Does:
  1. Asks for the password of the zip file.
  2. Installs the Consul certificate and key, and turns Consul TLS on.
  3. Sets the Gossip Key, the Peer and the LAN address of Consul.

Next:
  1. Run the "compose recreate" command that it prints.
  2. Open the ports that it names in the firewall.`

func newWSJoin(options *Options, folder func() string) *cobra.Command {
	var workstation, address, password string
	cmd := &cobra.Command{
		Use: "join <invitation>", Short: "Joins a Yard with an Invitation.", Long: yardJoinHelp,
		Args: func(cmd *cobra.Command, args []string) error {
			if cmd.Flags().Changed("workstation") {
				return fmt.Errorf("%s", renameWorkstationHint)
			}
			return cobra.ExactArgs(1)(cmd, args)
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			return joinWithInvitation(cmd, options, folder(), args[0], password, address)
		},
	}
	cmd.Flags().StringVar(&address, "address", "", "Sets the LAN IPv4 address of Consul (default: the one found).")
	cmd.Flags().StringVar(&password, "password", "", "Sets the zip file password (default: ask at a terminal).")
	cmd.Flags().StringVar(&workstation, "workstation", "", "Refuses a change of the Workstation name.")
	_ = cmd.Flags().MarkHidden("workstation")
	return cmd
}

func joinWithInvitation(cmd *cobra.Command, options *Options, folder, path, password, address string) error {
	env, err := readWSEnv(folder)
	if err != nil {
		return err
	}
	p := newPrompter(cmd, options)
	files, err := openInvitationFile(p, path, password)
	if err != nil {
		return err
	}
	invitation, err := readInvitation(files)
	if err != nil {
		return err
	}
	self := strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")
	if invitation.To != self {
		return fmt.Errorf("this Invitation is for %s, and this Workstation is %s. Ask the sender for an Invitation for %s", invitation.To, self, self)
	}
	trusted, err := consulTrustedCA(folder)
	if err != nil {
		return err
	}
	if trusted != nil && !sameCertificate(trusted, invitation.caPEM) {
		return fmt.Errorf("this Workstation is in another Yard. Run \"ws yard standalone\", then join")
	}
	bind, err := joinBindAddress(cmd, options, env, address)
	if err != nil {
		return err
	}
	if err := installRole(cmd, folder, env, roleConsul, invitation.caPEM, invitation.certPEM, invitation.keyPEM); err != nil {
		return err
	}
	for _, setting := range [][2]string{{"YARD_GOSSIP_KEY", invitation.GossipKey}, {"YARD_PEERS", invitation.Peer}, {"YARD_BIND_ADDRESS", bind}} {
		if err := env.Set(setting[0], setting[1]); err != nil {
			return err
		}
	}
	out := cmd.OutOrStdout()
	fmt.Fprintf(out, "Joined the Yard of %s.\n  Peer: %s\n  Consul TLS: on\n  Consul address: %s\n  Firewall: allow TCP %s and TCP+UDP %s from the Yard's computers.\n", invitation.From, invitation.Peer, bind, env.ValueOrDefault("YARD_CONSUL_SERVER_PORT"), env.ValueOrDefault("YARD_CONSUL_SERF_WAN_PORT"))
	return saveWSEnv(cmd, folder, env)
}

// openInvitationFile reads a zip file. At a terminal, it asks for the password of an encrypted one.
func openInvitationFile(p *prompter, path, password string) ([]certzip.File, error) {
	data, err := os.ReadFile(path) // #nosec G304 -- The user names the invitation.
	if err != nil {
		return nil, fmt.Errorf("Cannot read the Invitation: %w", err)
	}
	for attempt := 0; ; attempt++ {
		files, err := certzip.Open(data, password)
		switch {
		case err == nil:
			return files, nil
		case errors.Is(err, certzip.ErrWrongPassword) && p.terminal && attempt < 2:
			fmt.Fprintln(p.out, "The password is wrong.")
		case errors.Is(err, certzip.ErrPasswordRequired):
			if !p.terminal {
				return nil, fmt.Errorf("the Invitation has a password. Give it with --password")
			}
		default:
			return nil, err
		}
		if password, err = p.secret("Password of the zip file"); err != nil {
			return nil, err
		}
	}
}

// joinBindAddress is the LAN address that Consul listens on after the join: --address, the address
// that it listens on now, or the one that was found.
func joinBindAddress(cmd *cobra.Command, options *Options, env *envfile.File, address string) (string, error) {
	ip, err := yardSharePeer(cmd, options, env, address)
	if err != nil {
		return "", err
	}
	if ip == unknownLANAddress {
		return "", fmt.Errorf("no LAN address found for this Workstation. Set it with --address, for example --address 192.168.1.20")
	}
	return settings.Check("bind-address", ip)
}

// consulTrustedCA is the CA that Consul trusts on this Workstation, or nil while Consul TLS is off.
func consulTrustedCA(folder string) ([]byte, error) {
	enabled, err := consulTLSEnabled(folder)
	if err != nil || !enabled {
		return nil, err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return nil, err
	}
	defer func() { _ = root.Close() }()
	pem, err := root.ReadFile(filepath.Join("tls", "consul", "ca.crt"))
	if err != nil {
		return nil, fmt.Errorf("Consul TLS is on, but its CA certificate cannot be read: %w", err)
	}
	return pem, nil
}

func sameCertificate(a, b []byte) bool {
	return bytes.Equal(bytes.TrimSpace(a), bytes.TrimSpace(b))
}
