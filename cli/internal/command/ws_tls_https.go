package command

import (
	"crypto/x509"
	"fmt"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

var httpsPart = traefikPart{
	name:      "https",
	label:     "HTTPS",
	short:     "Manages the HTTPS certificate of Traefik.",
	files:     []string{"https.crt", "https.key"},
	generated: "10-tls.yaml",
	template:  templates.TraefikHTTPS,
	check: func(env *envfile.File, files map[string][]byte) error {
		return tlsca.CheckHTTPS(files["https.crt"], files["https.key"], yardHostname(env))
	},
	announce: func(cmd *cobra.Command, folder string, env *envfile.File) {
		fmt.Fprintln(cmd.OutOrStdout(), "Traefik HTTPS is on.\nBrowsers: import ca/ca.crt as a trusted authority when the Workstation CA signed the certificate.")
	},
}

func yardHostname(env *envfile.File) string {
	return "yard." + strings.Trim(env.ValueOrDefault("YARD_BASE_DOMAIN"), "\"'")
}

func newWSTLSHTTPS(options *Options, folder func() string) *cobra.Command {
	return newWSTLSPart(folder, httpsPart, httpsHelp, newWSTLSHTTPSCreate(options, folder), newWSTLSHTTPSImport(folder))
}

func newWSTLSHTTPSCreate(options *Options, folder func() string) *cobra.Command {
	var addresses []string
	var days int
	cmd := &cobra.Command{Use: "create", Short: "Signs the HTTPS certificate and turns it on.", Long: httpsCreateHelp, Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		if traefikOff(env) {
			return errTraefikOff()
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
		base := strings.Trim(env.ValueOrDefault("YARD_BASE_DOMAIN"), "\"'")
		intro := fmt.Sprintf("The certificate always names *.%s, localhost and 127.0.0.1.\nAdd more names for a remote Workstation (*.<workstation>.%s) or an address that people use.\n", base, base)
		if addresses, err = askAddresses(cmd, p, intro, addresses); err != nil {
			return err
		}
		days, err := askDays(cmd, p, days, 365, "certificate validity in days")
		if err != nil {
			return err
		}
		certPEM, keyPEM, leaf, err := issueFromCA(folder(), ca, signer, self, "https", self, days, func(leaf *x509.Certificate) { tlsca.FillTraefik(leaf, base, addresses) })
		if err != nil {
			return err
		}
		fmt.Fprintf(cmd.OutOrStdout(), "HTTPS certificate for %s signed.\n  Names: %s\n", self, strings.Join(tlsca.Names(leaf), ", "))
		if err := httpsPart.install(cmd, folder(), env, map[string][]byte{"https.crt": certPEM, "https.key": keyPEM}); err != nil {
			return err
		}
		fmt.Fprintln(cmd.ErrOrStderr(), "\n"+privateCAWarning)
		return nil
	}}
	cmd.Flags().StringArrayVar(&addresses, "address", nil, "Adds an IP address, DNS name or *.<name> wildcard to the certificate.")
	cmd.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
	addCAKeyPasswordFlags(cmd.Flags(), caKeyFlagUsage, caKeyFileFlagUsage)
	return cmd
}

func newWSTLSHTTPSImport(folder func() string) *cobra.Command {
	var caFile, certFile, keyFile string
	cmd := &cobra.Command{Use: "import", Short: "Imports an HTTPS certificate and turns it on.", Long: httpsImportHelp, Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		var caPEM []byte
		if caFile != "" {
			if caPEM, err = readImportFile("ca", caFile); err != nil {
				return err
			}
		}
		certPEM, err := readImportFile("cert", certFile)
		if err != nil {
			return err
		}
		keyPEM, err := readImportFile("key", keyFile)
		if err != nil {
			return err
		}
		if err := tlsca.CheckTraefik(caPEM, certPEM, keyPEM, yardHostname(env)); err != nil {
			return err
		}
		return httpsPart.install(cmd, folder(), env, map[string][]byte{"https.crt": certPEM, "https.key": keyPEM})
	}}
	cmd.Flags().StringVar(&caFile, "ca", "", "Reads the CA certificate that signed it (default: public CAs).")
	cmd.Flags().StringVar(&certFile, "cert", "", "Reads the certificate from this file.")
	cmd.Flags().StringVar(&keyFile, "key", "", "Reads the certificate key from this file.")
	_ = cmd.MarkFlagRequired("cert")
	_ = cmd.MarkFlagRequired("key")
	return cmd
}

const httpsHelp = `Manages the certificate that Traefik shows to browsers.

Does:
  1. "create" signs it with the Workstation CA. "import" takes a company or public certificate.
  2. "enable" and "disable" turn HTTPS on and off. They keep the files.

Note:
  - HTTPS uses tls/traefik/https.crt and https.key, and traefik/dynamic/10-tls.yaml.
  - HTTPS is on when YARD_HTTPS_PORT has a value.`

const httpsCreateHelp = `Signs the HTTPS certificate of this Workstation with the Workstation CA, and turns HTTPS on.

Before:
  - Make the Workstation CA with "ws tls ca create".

Does:
  1. Writes ca/yard/<this Workstation>/https.crt and https.key.
  2. Names *.<Base Domain>, localhost, 127.0.0.1 and each --address.
  3. Allows server use only.
  4. Copies it to tls/traefik/ and turns HTTPS on.

Next:
  1. Apply it:
       ws compose recreate

Note:
  - A wildcard covers one label: *.lvh.me does not cover app.ws-b.lvh.me.
  - The key stays on this Workstation.
  - For a company or public certificate, use "ws tls https import".`

const httpsImportHelp = `Imports an HTTPS certificate that another CA signed, and turns HTTPS on.

Before:
  - Have the certificate, its key and, for a company CA, its CA certificate as files.

Does:
  1. Checks that the key matches, and that the CA signed the certificate.
  2. Checks that the certificate names yard.<Base Domain>.
  3. Copies the certificate and key to tls/traefik/ and turns HTTPS on.

Note:
  - Without --ca, a public CA must have signed the certificate.
  - Put the whole chain in the --cert file.`
