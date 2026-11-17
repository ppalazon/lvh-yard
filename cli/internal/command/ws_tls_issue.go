package command

import (
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"unicode"

	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

// The Consul certificates are not signed here: ws yard share signs them, for the Workstations that
// join a Yard.
func newWSTLSIssue(options *Options, folder func() string) *cobra.Command {
	issue := &cobra.Command{Use: "issue", Short: "Signs certificates with the Workstation CA.", Args: cobra.NoArgs}
	addCAKeyPasswordFlags(issue.PersistentFlags(), caKeyFlagUsage, caKeyFileFlagUsage)
	issue.AddCommand(newWSTLSIssueTraefik(options, folder), newWSTLSIssueUser(options, folder))
	return issue
}

func checkTLSAddress(address string) error {
	if net.ParseIP(address) != nil {
		return nil
	}
	dns := strings.TrimPrefix(address, "*.")
	if !settings.ValidBaseDomain(dns) || net.ParseIP(dns) != nil {
		return fmt.Errorf("invalid --address %q. Use an IP address, a DNS name or a wildcard such as *.ws-b.lvh.me", address)
	}
	return nil
}

func checkWorkstationName(name string) error {
	if !settings.ValidWorkstationName(name) {
		return fmt.Errorf("invalid Workstation name. Use 1 to 63 lowercase letters, digits or -, and no - at the start or end")
	}
	return nil
}

func splitAddresses(text string) []string {
	return strings.FieldsFunc(text, func(r rune) bool { return r == ',' || unicode.IsSpace(r) })
}

// newWSTLSIssueTraefik signs the Traefik certificate of this Workstation. It is for this
// Workstation only, so its key never leaves it.
func newWSTLSIssueTraefik(options *Options, folder func() string) *cobra.Command {
	var addresses []string
	var days int
	cmd := &cobra.Command{Use: "traefik", Short: "Signs the Traefik certificate of this Workstation.", Long: issueTraefikHelp, Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		p := newPrompter(cmd, options)
		ca, signer, err := readCA(cmd, p, folder())
		if err != nil {
			return err
		}
		self := strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")
		base := env.ValueOrDefault("YARD_BASE_DOMAIN")
		if addresses, err = askTraefikAddresses(cmd, p, base, addresses); err != nil {
			return err
		}
		days, err := askDays(cmd, p, days, 365, "certificate validity in days")
		if err != nil {
			return err
		}
		leaf, key, err := tlsca.NewCertificate(self, days)
		if err != nil {
			return err
		}
		tlsca.FillTraefik(leaf, base, addresses)
		if err := tlsca.CheckWithin(leaf, ca); err != nil {
			return err
		}
		root, err := os.OpenRoot(folder())
		if err != nil {
			return err
		}
		defer func() { _ = root.Close() }()
		if err := writeTLSCertificate(root, filepath.Dir(issuedPath("", self, roleTraefik, ".crt")), roleTraefik, leaf, ca, key, signer, ""); err != nil {
			return err
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		out := cmd.OutOrStdout()
		fmt.Fprintf(out, "Traefik certificate for %s saved in %s.\n  Names: %s\n  HTTPS is not started.\n", self, issuedPath(folder(), self, roleTraefik, ".crt"), strings.Join(tlsca.Names(leaf), ", "))
		fmt.Fprintf(out, "Next:\n  %s tls install\n", wsCommand(folder(), options.WorkstationFolder()))
		return nil
	}}
	cmd.Flags().StringArrayVar(&addresses, "address", nil, "Adds an IP address, DNS name or *.<name> wildcard to the certificate.")
	cmd.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
	return cmd
}

// askTraefikAddresses gives the names to add to the Traefik certificate: the flag, or the answer to
// a question.
func askTraefikAddresses(cmd *cobra.Command, p *prompter, base string, addresses []string) ([]string, error) {
	if !cmd.Flags().Changed("address") && p.terminal {
		fmt.Fprintf(p.out, "The Traefik certificate always names *.%s, localhost and 127.0.0.1.\nAdd more names for a remote Workstation (*.<workstation>.%s) or an address that people use.\n", base, base)
		text, err := p.text("More names: IP addresses, DNS names or wildcards, separated by spaces (none)", "", func(text string) error {
			for _, address := range splitAddresses(text) {
				if err := checkTLSAddress(address); err != nil {
					return err
				}
			}
			return nil
		})
		if err != nil {
			return addresses, err
		}
		addresses = splitAddresses(text)
	}
	for _, address := range addresses {
		if err := checkTLSAddress(address); err != nil {
			return addresses, err
		}
	}
	return addresses, nil
}

const issueTraefikHelp = `Signs the Traefik certificate of this Workstation with the Workstation CA.

Before:
  - Make the Workstation CA with "ws tls ca create".

Does:
  1. Writes ca/certs/<this Workstation>/traefik.crt and traefik.key.
  2. Names *.<Base Domain>, localhost, 127.0.0.1 and each --address.
  3. Allows server use only.

Next:
  1. Install it:
       ws tls install

Note:
  - It does not copy the files to tls/ and does not start HTTPS.
  - A wildcard covers one label: *.lvh.me does not cover app.ws-b.lvh.me.
  - For a company or public certificate, use "ws tls install --ca --cert --key".`
