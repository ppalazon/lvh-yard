package command

import (
	"fmt"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

var mtlsPart = traefikPart{
	name:      "mtls",
	label:     "mutual TLS to Services",
	short:     "Manages mutual TLS from Traefik to Services.",
	files:     []string{"mtls.crt", "mtls.key", "mtls-ca.crt"},
	generated: "12-mtls.yaml",
	template:  templates.TraefikMutualTLS,
	flag:      "YARD_MTLS_ENABLED",
	check: func(env *envfile.File, files map[string][]byte) error {
		if err := tlsca.CheckCA(files["mtls-ca.crt"]); err != nil {
			return err
		}
		return tlsca.CheckMutualTLS(files["mtls.crt"], files["mtls.key"])
	},
	announce: func(cmd *cobra.Command, folder string, env *envfile.File) {
		fmt.Fprintln(cmd.OutOrStdout(), "Traefik calls the Instances over HTTPS, with its certificate, on Routes whose Route Profile has mutualTLS: {}.")
	},
}

func newWSTLSMTLS(options *Options, folder func() string) *cobra.Command {
	return newWSTLSPart(folder, mtlsPart, mtlsHelp, newWSTLSMTLSCreate(options, folder), newWSTLSMTLSImport(folder))
}

func newWSTLSMTLSCreate(options *Options, folder func() string) *cobra.Command {
	var days int
	cmd := &cobra.Command{Use: "create", Short: "Signs the Traefik certificate for Services.", Long: mtlsCreateHelp, Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
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
		caPEM, err := readCAPEM(folder())
		if err != nil {
			return err
		}
		days, err := askDays(cmd, p, days, 365, "certificate validity in days")
		if err != nil {
			return err
		}
		self := strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")
		certPEM, keyPEM, _, err := issueFromCA(folder(), ca, signer, self, "mtls", "traefik-"+self, days, tlsca.FillClient)
		if err != nil {
			return err
		}
		fmt.Fprintf(cmd.OutOrStdout(), "Traefik client certificate for %s signed.\n", self)
		if err := mtlsPart.install(cmd, folder(), env, map[string][]byte{"mtls.crt": certPEM, "mtls.key": keyPEM, "mtls-ca.crt": caPEM}); err != nil {
			return err
		}
		fmt.Fprintln(cmd.ErrOrStderr(), "\n"+privateCAWarning)
		return nil
	}}
	cmd.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
	addCAKeyPasswordFlags(cmd.Flags(), caKeyFlagUsage, caKeyFileFlagUsage)
	return cmd
}

func newWSTLSMTLSImport(folder func() string) *cobra.Command {
	var caFile, certFile, keyFile string
	cmd := &cobra.Command{Use: "import", Short: "Imports the Traefik client certificate and CA.", Long: mtlsImportHelp, Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		files := make(map[string][]byte, 3)
		for _, file := range []struct{ flag, path, name string }{{"ca", caFile, "mtls-ca.crt"}, {"cert", certFile, "mtls.crt"}, {"key", keyFile, "mtls.key"}} {
			if files[file.name], err = readImportFile(file.flag, file.path); err != nil {
				return err
			}
		}
		return mtlsPart.install(cmd, folder(), env, files)
	}}
	cmd.Flags().StringVar(&caFile, "ca", "", "Reads the CA certificate that checks the Services.")
	cmd.Flags().StringVar(&certFile, "cert", "", "Reads the certificate that Traefik shows to Services.")
	cmd.Flags().StringVar(&keyFile, "key", "", "Reads the key of that certificate.")
	for _, flag := range []string{"ca", "cert", "key"} {
		_ = cmd.MarkFlagRequired(flag)
	}
	return cmd
}

const mtlsHelp = `Manages mutual TLS from Traefik to the Services.

Does:
  1. "create" signs a certificate for Traefik with the Workstation CA, and trusts that CA for Services.
  2. "import" takes a certificate for Traefik and the CA that checks the Services.
  3. "enable" and "disable" turn it on and off. They keep the files.

Note:
  - It uses tls/traefik/mtls.crt, mtls.key and mtls-ca.crt, and traefik/dynamic/12-mtls.yaml.
  - It is on when YARD_MTLS_ENABLED is true.
  - Only Routes whose Route Profile has mutualTLS: {} use it. Other Routes keep plain HTTP.
  - A Service certificate comes from "ws tls ca issue service".`

const mtlsCreateHelp = `Signs a client certificate for Traefik with the Workstation CA, and turns mutual TLS to Services on.

Before:
  - Make the Workstation CA with "ws tls ca create".

Does:
  1. Writes ca/yard/<this Workstation>/mtls.crt and mtls.key. They allow client use only.
  2. Copies them to tls/traefik/, with ca/ca.crt as mtls-ca.crt.
  3. Turns mutual TLS on.

Next:
  1. Sign a certificate for each Service:
       ws tls ca issue service <name>
  2. Add mutualTLS: {} to a Route Profile of the Service.
  3. Apply it:
       ws compose recreate

Note:
  - Traefik checks each Service with ca/ca.crt, and each Service must check Traefik with the same CA.`

const mtlsImportHelp = `Imports the certificate that Traefik shows to Services, and the CA that checks them, and turns mutual TLS on.

Before:
  - Have the CA certificate, the Traefik certificate and its key as files.

Does:
  1. Checks that the key matches, and that the certificate allows client use and is valid now.
  2. Checks that the CA file holds CA certificates that are valid now.
  3. Copies them to tls/traefik/ and turns mutual TLS on.

Note:
  - --ca checks the certificates of the Services. The Services have their own CA to check --cert.`
