package command

import (
	"crypto/ecdsa"
	"crypto/x509"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/certzip"
	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

const (
	// unknownLANAddress stands for an address that could not be found.
	unknownLANAddress = "<this-workstation-lan-ip>"
	// loopbackAddress is where Consul listens until the Workstation joins or opens a Yard.
	loopbackAddress = "127.0.0.1"
)

const yardShareHelp = `Invites one Workstation to this Yard. Writes one zip file with a password.

Before:
  - Run "ws yard init" on this Workstation, once.

Does:
  1. Signs a Consul certificate for the name that you give.
  2. Packs it in the Invitation with the Main CA certificate.
  3. Adds the Gossip Key and the address of this Workstation.

Next:
  1. Send the zip file and its password by two different ways.
  2. The other Workstation runs:
       ws yard join <zip file>

Note:
  - Only the Workstation that holds the CA key can share.`

func newWSYardShare(options *Options, folder func() string) *cobra.Command {
	var output, password string
	var days int
	cmd := &cobra.Command{
		Use: "share [workstation]", Short: "Invites a Workstation to this Yard.", Long: yardShareHelp,
		Args: cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runYardShare(cmd, options, folder(), args, output, password, days)
		},
	}
	cmd.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
	cmd.Flags().StringVarP(&output, "output", "o", "", "Writes the zip file here (default: certificates/ in the Workstation Folder).")
	cmd.Flags().StringVar(&password, "password", "", "Sets the zip file password (default: ask, or make a random one).")
	addCAKeyPasswordFlags(cmd.Flags(), caKeyFlagUsage, caKeyFileFlagUsage)
	return cmd
}

func runYardShare(cmd *cobra.Command, options *Options, folder string, args []string, output, password string, days int) error {
	env, err := readWSEnv(folder)
	if err != nil {
		return err
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	ws := wsCommand(folder, options.WorkstationFolder())
	peer, err := yardOpenAddress(folder, ws, env)
	if err != nil {
		return err
	}
	p := newPrompter(cmd, options)
	self := strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")
	name, err := askJoiningName(p, args, self)
	if err != nil {
		return err
	}
	key := strings.Trim(env.Get("YARD_GOSSIP_KEY"), "\"'")
	if err := checkInitGossipKey(key); err != nil {
		return err
	}
	ca, signer, err := readCA(cmd, p, folder)
	if err != nil {
		return err
	}
	caPEM, err := readCAPEM(folder)
	if err != nil {
		return err
	}
	if err := checkOwnYard(folder, caPEM); err != nil {
		return err
	}
	if days, err = askDays(cmd, p, days, 365, "certificate validity in days"); err != nil {
		return err
	}
	if err := signConsul(folder, ca, signer, name, days); err != nil {
		return err
	}
	certPEM, keyPEM, err := readSignedTLSRole(folder, name, roleConsul)
	if err != nil {
		return err
	}
	invitation := yardInvitation{Version: yardInvitationVersion, From: self, To: name, Peer: peer + ":" + env.ValueOrDefault("YARD_CONSUL_SERF_WAN_PORT"), GossipKey: key}
	files, err := invitationFiles(invitation, caPEM, certPEM, keyPEM, "lvh-yard ws yard join <this-file>")
	if err != nil {
		return err
	}
	secret, given, err := newZipPassword(cmd, p, password)
	if err != nil {
		return err
	}
	data, err := certzip.Build(files, secret)
	if err != nil {
		return err
	}
	path, err := writeExport(cmd, folder, output, name+"-yard.zip", data)
	if err != nil {
		return err
	}
	printYardShare(cmd, ws, path, name, secret, given)
	return nil
}

// yardOpenAddress is the address that Consul listens on after ws yard init. A Workstation that
// ws yard init did not open has Consul TLS off, or no usable address. A loopback address is allowed
// for a Yard on one computer, such as the test Workstations.
func yardOpenAddress(folder, ws string, env *envfile.File) (string, error) {
	enabled, err := consulTLSEnabled(folder)
	if err != nil {
		return "", err
	}
	ip := net.ParseIP(strings.Trim(env.Get("YARD_BIND_ADDRESS"), "\"'"))
	if !enabled || ip == nil || ip.To4() == nil || ip.IsUnspecified() {
		return "", fmt.Errorf("this Workstation is not open to a Yard. Run:\n  %s yard init", ws)
	}
	return ip.String(), nil
}

// printYardShare says what the share made, and what to do next.
func printYardShare(cmd *cobra.Command, ws, path, name, secret string, given bool) {
	out := cmd.OutOrStdout()
	fmt.Fprintf(out, "Invitation for %s: %s\n", name, path)
	if !given {
		fmt.Fprintf(out, "Zip password: %s\n  Save it now. It is not shown again.\n", secret)
	}
	fmt.Fprintf(out, "Next:\n  1. Send the zip file and its password by two different ways. Never send ca.key.\n  2. On %s, run:\n       lvh-yard ws yard join %s\n  3. After %s joins, run on this Workstation:\n       %s yard peers --sync\n", name, quoteShell(filepath.Base(path)), name, ws)
}

func askJoiningName(p *prompter, args []string, self string) (string, error) {
	var name string
	switch {
	case len(args) == 1:
		name = args[0]
	case p.terminal:
		var err error
		name, err = p.text("Name of the Workstation that joins", "", func(text string) error {
			if text == "" {
				return fmt.Errorf("Give the name of the Workstation that joins.")
			}
			return checkWorkstationName(text)
		})
		if err != nil {
			return "", err
		}
	default:
		return "", fmt.Errorf("give the name of the Workstation that joins, as an argument")
	}
	if err := checkWorkstationName(name); err != nil {
		return "", err
	}
	if name == self {
		return "", fmt.Errorf("%s is this Workstation. Give the name of the Workstation that joins", name)
	}
	return name, nil
}

// checkOwnYard refuses a Workstation that already trusts the CA of another Yard for Consul. Sharing
// would give its members a Consul certificate from a CA that this Workstation does not trust.
func checkOwnYard(folder string, caPEM []byte) error {
	trusted, err := consulTrustedCA(folder)
	if err != nil || trusted == nil || sameCertificate(trusted, caPEM) {
		return err
	}
	return fmt.Errorf("this Workstation trusts the Main CA of another Yard. Share from the Workstation that made that Main CA, or run \"ws yard standalone\" first")
}

// signConsul signs the Consul certificate of a Workstation and keeps it in ca/certs/<name>/.
func signConsul(folder string, ca *x509.Certificate, signer *ecdsa.PrivateKey, name string, days int) error {
	leaf, key, err := tlsca.NewCertificate(name, days)
	if err != nil {
		return err
	}
	tlsca.FillConsul(leaf, name)
	if err := tlsca.CheckWithin(leaf, ca); err != nil {
		return err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	return writeTLSCertificate(root, filepath.Dir(issuedPath("", name, roleConsul, ".crt")), roleConsul, leaf, ca, key, signer, "")
}

// newZipPassword gives the password of a zip file: the given one, or a random one. given is false
// for a random one, which the command prints.
func newZipPassword(cmd *cobra.Command, p *prompter, password string) (secret string, given bool, err error) {
	if secret, given, err = askTLSClientPassword(cmd, p, password, "Password of the zip file (empty for a random one)"); err != nil || given {
		return secret, given, err
	}
	secret, err = tlsca.RandomPassword()
	return secret, false, err
}

// yardSharePeer is the LAN address of this Workstation for the Yard: --address, the address that
// Consul listens on, or the one that was found. It gives unknownLANAddress when there is none.
func yardSharePeer(cmd *cobra.Command, options *Options, env *envfile.File, address string) (string, error) {
	if cmd.Flags().Changed("address") {
		ip := net.ParseIP(address)
		if ip == nil || ip.To4() == nil || ip.IsUnspecified() || (!ip.IsGlobalUnicast() && !ip.IsLoopback()) {
			return "", fmt.Errorf("--address must be one IPv4 address, for example 192.168.1.10")
		}
		return ip.String(), nil
	}
	if ip := net.ParseIP(env.Get("YARD_BIND_ADDRESS")); ip != nil && ip.To4() != nil && !ip.IsUnspecified() && !ip.IsLoopback() {
		return ip.String(), nil
	}
	if detected, err := options.Process.LANAddress(); err == nil && detected != "" {
		return detected, nil
	}
	return unknownLANAddress, nil
}
