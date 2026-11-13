package command

import (
	"crypto/x509"
	"fmt"
	"net"
	"path/filepath"
	"slices"
	"strings"
	"unicode"

	"github.com/spf13/cobra"
)

func newWSTLSIssue(options *Options, folder func() string) *cobra.Command {
	issue := &cobra.Command{Use: "issue", Short: "Signs certificates with the Yard CA.", Args: cobra.NoArgs}
	issue.AddCommand(
		newWSTLSIssueRoles(options, folder, "workstation [name]", "Signs the Consul and Traefik certificates of a Workstation without starting TLS.", issueWorkstationHelp, tlsRoles),
		newWSTLSIssueRoles(options, folder, "consul [name]", "Signs the Consul certificate of a Workstation without starting TLS.", issueConsulHelp, []string{roleConsul}),
		newWSTLSIssueRoles(options, folder, "traefik [name]", "Signs the Traefik certificate of a Workstation without starting TLS.", issueTraefikHelp, []string{roleTraefik}),
		newWSTLSIssueUser(options, folder),
	)
	return issue
}

func checkTLSAddress(address string) error {
	if net.ParseIP(address) != nil {
		return nil
	}
	dns := strings.TrimPrefix(address, "*.")
	if !baseDomainName.MatchString(dns) || net.ParseIP(dns) != nil {
		return fmt.Errorf("invalid --address %q: use an IP address, DNS name or leftmost-label wildcard such as *.ws-b.lvh.me", address)
	}
	return nil
}

func checkWorkstationName(name string) error {
	if !workstationName.MatchString(name) {
		return fmt.Errorf("invalid Workstation name: use lowercase letters, digits and -, at most 63, not starting or ending with -")
	}
	return nil
}

func splitAddresses(text string) []string {
	return strings.FieldsFunc(text, func(r rune) bool { return r == ',' || unicode.IsSpace(r) })
}

func newWSTLSIssueRoles(options *Options, folder func() string, use, short, long string, roles []string) *cobra.Command {
	var domain string
	var addresses []string
	var days int
	forTraefik := slices.Contains(roles, roleTraefik)
	cmd := &cobra.Command{Use: use, Short: short, Long: long, Args: cobra.MaximumNArgs(1), RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		ca, signer, _, err := readYardCA(folder())
		if err != nil {
			return err
		}
		p := newPrompter(cmd, options)
		self := strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")
		name := self
		if len(args) == 1 {
			name = args[0]
		} else if p.terminal {
			name, err = p.text("Workstation name, as it is in the Yard", self, checkWorkstationName)
			if err != nil {
				return err
			}
		}
		if err := checkWorkstationName(name); err != nil {
			return err
		}
		base := env.ValueOrDefault("YARD_BASE_DOMAIN")
		if forTraefik {
			if cmd.Flags().Changed("base-domain") {
				base = domain
			} else if p.terminal {
				base, err = p.text("Base Domain of that Workstation", base, func(text string) error {
					_, err := checkWSSetting("base-domain", text)
					return err
				})
				if err != nil {
					return err
				}
			}
			if _, err := checkWSSetting("base-domain", base); err != nil {
				return err
			}
			if !cmd.Flags().Changed("address") && p.terminal {
				fmt.Fprintf(p.out, "The Traefik certificate always names *.%s, localhost and 127.0.0.1.\nAdd more names only for a remote Workstation whose Routes this Workstation serves (*.<workstation>.%s), or an address that people reach it by.\n", base, base)
				text, err := p.text("More IP addresses, DNS names or wildcards such as *.ws-b.lvh.me, separated by spaces (none)", "", func(text string) error {
					for _, address := range splitAddresses(text) {
						if err := checkTLSAddress(address); err != nil {
							return err
						}
					}
					return nil
				})
				if err != nil {
					return err
				}
				addresses = splitAddresses(text)
			}
			for _, address := range addresses {
				if err := checkTLSAddress(address); err != nil {
					return err
				}
			}
		}
		days, err := askDays(cmd, p, days, 365, "certificate validity in days")
		if err != nil {
			return err
		}
		dir := filepath.Dir(issuedPath(folder(), name, roles[0], ".crt"))
		out := cmd.OutOrStdout()
		for _, role := range roles {
			leaf, key, err := newTLSCertificate(name, days)
			if err != nil {
				return err
			}
			fillRoleCertificate(leaf, role, name, base, addresses)
			if leaf.NotAfter.After(ca.NotAfter) || leaf.NotBefore.Before(ca.NotBefore) {
				return fmt.Errorf("certificate validity is outside the Yard CA validity: use a shorter --days value or make a new CA")
			}
			if err := writeTLSCertificate(dir, role, leaf, ca, key, signer); err != nil {
				return err
			}
			fmt.Fprintf(out, "%s certificate for %s saved in %s.\nIt names: %s\n", roleSettingName(role), name, filepath.Join(dir, role+".crt"), strings.Join(certificateNames(leaf), ", "))
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		ws := wsCommand(folder(), options.WorkstationFolder())
		install := ws + " tls ca install"
		if name != self {
			install += " " + quoteShell(name)
		}
		fmt.Fprintf(out, "TLS is not started. Never share the Yard CA key. To install, run:\n  %s\n", install)
		return nil
	}}
	if forTraefik {
		cmd.Flags().StringVar(&domain, "base-domain", "", "Uses this Base Domain for the certificate (default: this Workstation's).")
		cmd.Flags().StringArrayVar(&addresses, "address", nil, "Adds an IP address, DNS name or wildcard DNS name to the Traefik certificate.")
	}
	cmd.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
	return cmd
}

// fillRoleCertificate sets the names and usages of a role. Consul checks server.<name>.consul and
// needs both usages. Traefik answers browsers, which need the Route names and a server usage.
func fillRoleCertificate(leaf *x509.Certificate, role, name, base string, addresses []string) {
	leaf.KeyUsage = x509.KeyUsageDigitalSignature
	leaf.IPAddresses = []net.IP{net.ParseIP("127.0.0.1")}
	if role == roleConsul {
		leaf.DNSNames = []string{"server." + name + ".consul", "localhost"}
		leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth}
		return
	}
	leaf.DNSNames = []string{"*." + base, "localhost"}
	leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}
	for _, address := range addresses {
		if ip := net.ParseIP(address); ip != nil {
			leaf.IPAddresses = append(leaf.IPAddresses, ip)
			continue
		}
		leaf.DNSNames = append(leaf.DNSNames, address)
	}
}

const issueWorkstationHelp = `Signs the Consul and the Traefik certificate of a Workstation with the Yard CA. It does not copy
them to tls/ or start TLS. To sign only one, use ws tls ca issue consul or ws tls ca issue traefik.

The files are ca/certs/<name>/consul.crt|key and ca/certs/<name>/traefik.crt|key. The
certificates have separate keys, so Traefik never holds the key that identifies this Workstation
to the other Consul servers.

The Consul certificate names server.<name>.consul, localhost and 127.0.0.1.
The Traefik certificate names *.<Base Domain>, localhost and 127.0.0.1.
A wildcard covers one label, so *.lvh.me does not cover app.ws-b.lvh.me.
Add more names to the Traefik certificate with --address, once for each:
  *.<workstation>.<Base Domain>   a remote Workstation whose Routes this Workstation serves
  an IP address or DNS name       an address that people or tools reach this Workstation by`

const issueConsulHelp = `Signs the Consul certificate of a Workstation with the Yard CA, in ca/certs/<name>/consul.crt|key.
It does not copy it to tls/ or start TLS.

It names server.<name>.consul, which Consul checks between Workstations, localhost and 127.0.0.1.
It allows server and client use, because Consul checks both ways.`

const issueTraefikHelp = `Signs the Traefik certificate of a Workstation with the Yard CA, in ca/certs/<name>/traefik.crt|key.
It does not copy it to tls/ or start TLS. For a public or company certificate, use
ws tls import traefik instead.

It names *.<Base Domain>, localhost and 127.0.0.1, and it allows server use only.
A wildcard covers one label, so *.lvh.me does not cover app.ws-b.lvh.me.
Add more names with --address, once for each:
  *.<workstation>.<Base Domain>   a remote Workstation whose Routes this Workstation serves
  an IP address or DNS name       an address that people or tools reach this Workstation by`

func certificateNames(cert *x509.Certificate) []string {
	names := make([]string, 0, len(cert.DNSNames)+len(cert.IPAddresses))
	for _, name := range cert.DNSNames {
		names = append(names, "DNS:"+name)
	}
	for _, ip := range cert.IPAddresses {
		names = append(names, "IP:"+ip.String())
	}
	return names
}
