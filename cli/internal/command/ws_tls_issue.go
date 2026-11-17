package command

import (
	"crypto/ecdsa"
	"crypto/x509"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"unicode"

	"github.com/ppalazon/lvh-yard/cli/internal/certzip"
	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

// ws tls ca issue signs what a person or a Service holds. The Consul certificates are not signed
// here: ws yard share signs them. The parts of Traefik sign their own certificates with
// issueFromCA: ws tls https create and ws tls mtls create.
func newWSTLSIssue(options *Options, folder func() string) *cobra.Command {
	issue := &cobra.Command{Use: "issue", Short: "Signs certificates with the Workstation CA.", Args: cobra.NoArgs}
	addCAKeyPasswordFlags(issue.PersistentFlags(), caKeyFlagUsage, caKeyFileFlagUsage)
	issue.AddCommand(newWSTLSIssueService(options, folder), newWSTLSIssueUser(options, folder))
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

// issueFromCA signs a certificate with the Workstation CA, and keeps it in ca/yard/<owner>/<kind>.crt
// and .key, or in ca/service/<owner>/ for a Service. owner is a Workstation or a Service. fill sets the names and the usages. It gives the
// PEM of the certificate and of its key.
func issueFromCA(folder string, ca *x509.Certificate, signer *ecdsa.PrivateKey, owner, kind, commonName string, days int, fill func(*x509.Certificate)) (certPEM, keyPEM []byte, leaf *x509.Certificate, err error) {
	leaf, key, err := tlsca.NewCertificate(commonName, days)
	if err != nil {
		return nil, nil, nil, err
	}
	fill(leaf)
	if err := tlsca.CheckWithin(leaf, ca); err != nil {
		return nil, nil, nil, err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return nil, nil, nil, err
	}
	defer func() { _ = root.Close() }()
	if err := writeTLSCertificate(root, filepath.Dir(issuedPath("", owner, kind, ".crt")), kind, leaf, ca, key, signer, ""); err != nil {
		return nil, nil, nil, err
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return nil, nil, nil, err
	}
	if certPEM, keyPEM, err = readSignedTLSRole(folder, owner, kind); err != nil {
		return nil, nil, nil, err
	}
	return certPEM, keyPEM, leaf, nil
}

// newWSTLSIssueService signs a certificate for a Service. The Service serves HTTPS with it, and
// Traefik checks the Service with the CA, when the Service has mutual TLS.
func newWSTLSIssueService(options *Options, folder func() string) *cobra.Command {
	var addresses []string
	var days int
	var password string
	cmd := &cobra.Command{Use: "service [name]", Short: "Signs a certificate for a Service.", Long: issueServiceHelp, Args: cobra.MaximumNArgs(1), RunE: func(cmd *cobra.Command, args []string) error {
		if _, err := readWSEnv(folder()); err != nil {
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
		name, err := askServiceName(p, args)
		if err != nil {
			return err
		}
		intro := fmt.Sprintf("The certificate always names %s, localhost and 127.0.0.1. Traefik checks only the name of the Service.\nAdd the DNS names or IP addresses that other programs use to reach the Instances.\n", name)
		if addresses, err = askAddresses(cmd, p, intro, addresses); err != nil {
			return err
		}
		days, err := askDays(cmd, p, days, 365, "certificate validity in days")
		if err != nil {
			return err
		}
		secret, given, err := newZipPassword(cmd, p, password)
		if err != nil {
			return err
		}
		certPEM, keyPEM, leaf, err := issueFromCA(folder(), ca, signer, name, "service", name, days, func(leaf *x509.Certificate) { tlsca.FillService(leaf, name, addresses) })
		if err != nil {
			return err
		}
		chain, caPEM, err := writeFullChain(folder(), name, "service", certPEM)
		if err != nil {
			return err
		}
		data, err := certzip.Build([]certzip.File{
			{Name: "service.crt", Data: certPEM}, {Name: "service.key", Data: keyPEM}, {Name: "service-fullchain.crt", Data: chain},
			{Name: "ca.crt", Data: caPEM}, {Name: "README.txt", Data: []byte(fmt.Sprintf(serviceReadme, name))},
		}, secret)
		if err != nil {
			return err
		}
		zipPath, err := writeExport(cmd, folder(), "", name+"-service.zip", data)
		if err != nil {
			return err
		}
		out := cmd.OutOrStdout()
		fmt.Fprintf(out, "Service certificate for %s saved in %s.\n  Names: %s\n  Files: service.crt, service.key and service-fullchain.crt (the certificate, then the CA)\nZip file saved in %s, encrypted with AES-256.\n", name, filepath.Dir(issuedPath(folder(), name, "service", ".crt")), strings.Join(tlsca.Names(leaf), ", "), zipPath)
		if !given {
			fmt.Fprintf(out, "Password: %s\n  Save it now. It is not shown again.\n  Send it by another way than the zip file.\n", secret)
		}
		fmt.Fprintf(out, "Next:\n  1. Give the Service the zip file, or service.key and service-fullchain.crt.\n  2. Make Traefik trust the Workstation CA for Services:\n       %s tls mtls create\n", wsCommand(folder(), options.WorkstationFolder()))
		return nil
	}}
	cmd.Flags().StringArrayVar(&addresses, "address", nil, "Adds an IP address or DNS name to the certificate.")
	cmd.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
	cmd.Flags().StringVar(&password, "password", "", "Sets the zip file password (default: ask, or make a random one).")
	return cmd
}

// writeFullChain keeps the certificate followed by the certificate of the Workstation CA in
// <kind>-fullchain.crt, for a Service that sends the whole chain to its clients. It gives the
// chain and the CA certificate.
func writeFullChain(folder, owner, kind string, certPEM []byte) (chain, caPEM []byte, err error) {
	if caPEM, err = readCAPEM(folder); err != nil {
		return nil, nil, err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return nil, nil, err
	}
	defer func() { _ = root.Close() }()
	chain = append(append([]byte{}, certPEM...), caPEM...)
	if err := root.WriteFile(issuedPath("", owner, kind+"-fullchain", ".crt"), chain, 0600); err != nil {
		return nil, nil, err
	}
	return chain, caPEM, protectWorkstationFolder(folder)
}

const serviceReadme = `This zip file holds the certificate of the Service %s.

  service.key             The private key. Keep it private.
  service.crt             The certificate.
  service-fullchain.crt   The certificate, then the CA certificate.
  ca.crt                  The CA certificate. Make the Service trust it to check Traefik.

Serve HTTPS with service-fullchain.crt and service.key.
`

func askServiceName(p *prompter, args []string) (string, error) {
	var name string
	switch {
	case len(args) == 1:
		name = args[0]
	case p.terminal:
		var err error
		name, err = p.text("Service name", "", func(text string) error {
			return checkServiceName(text)
		})
		if err != nil {
			return "", err
		}
	default:
		return "", fmt.Errorf("Missing the Service name. Give it as an argument")
	}
	return name, checkServiceName(name)
}

func checkServiceName(name string) error {
	if !settings.ValidBaseDomain(name) || net.ParseIP(name) != nil {
		return fmt.Errorf("invalid Service name. Use a DNS name in lowercase, such as grafana or grafana.example.test")
	}
	return validateOutputName(name)
}

// askAddresses gives the names to add to a certificate: the flag, or the answer to a question.
// intro says which names the certificate has already.
func askAddresses(cmd *cobra.Command, p *prompter, intro string, addresses []string) ([]string, error) {
	if !cmd.Flags().Changed("address") && p.terminal {
		fmt.Fprint(p.out, intro)
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

const issueServiceHelp = `Signs a certificate for a Service with the Workstation CA.

Before:
  - Make the Workstation CA with "ws tls ca create".

Does:
  1. Writes ca/service/<name>/service.crt and service.key.
  2. Writes service-fullchain.crt next to them: the certificate, then the CA certificate.
  3. Names the Service, localhost, 127.0.0.1 and each --address.
  4. Allows server and client use.
  5. Packs the files and ca.crt in certificates/<name>-service.zip, encrypted with AES-256.

Next:
  1. Give the Service the zip file and its password, so that it serves HTTPS and sends the CA.
  2. Make Traefik trust the Workstation CA for Services:
       ws tls mtls create

Note:
  - Traefik checks that the certificate names the Service, at any Instance address. --address is for other programs that connect to the Service.
  - A Service with "publish --mutual-tls on" makes Traefik call its Instances over HTTPS.
  - The Service must ask for a certificate from Traefik to use mutual TLS, and trust ca/ca.crt.`
