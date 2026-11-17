package command

import (
	"fmt"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

var clientPart = traefikPart{
	name:      "client",
	label:     "Client Certificate authentication",
	short:     "Manages the check of Client Certificates.",
	files:     []string{"client-ca.crt"},
	generated: "11-client-auth.yaml",
	template:  templates.TraefikClientAuth,
	flag:      "YARD_CLIENT_AUTH_ENABLED",
	check: func(env *envfile.File, files map[string][]byte) error {
		return tlsca.CheckCA(files["client-ca.crt"])
	},
	announce: func(cmd *cobra.Command, folder string, env *envfile.File) {
		out := cmd.OutOrStdout()
		fmt.Fprintln(out, "Traefik asks for a Client Certificate on Routes whose Route Profile has clientCertificate: {}.")
		if !httpsPart.isOn(env) {
			fmt.Fprintln(out, "HTTPS is off, so those Routes have no hostname yet. Turn it on with \"lvh-yard ws tls https create\".")
		}
	},
}

func newWSTLSClient(folder func() string) *cobra.Command {
	return newWSTLSPart(folder, clientPart, clientHelp, newWSTLSClientCreate(folder), newWSTLSClientImport(folder))
}

func newWSTLSClientCreate(folder func() string) *cobra.Command {
	return &cobra.Command{Use: "create", Short: "Trusts the Workstation CA for Client Certificates.", Long: clientCreateHelp, Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		caPEM, err := readCAPEM(folder())
		if err != nil {
			return err
		}
		return clientPart.install(cmd, folder(), env, map[string][]byte{"client-ca.crt": caPEM})
	}}
}

func newWSTLSClientImport(folder func() string) *cobra.Command {
	var caFile string
	cmd := &cobra.Command{Use: "import", Short: "Trusts another CA for Client Certificates.", Long: clientImportHelp, Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		caPEM, err := readImportFile("ca", caFile)
		if err != nil {
			return err
		}
		return clientPart.install(cmd, folder(), env, map[string][]byte{"client-ca.crt": caPEM})
	}}
	cmd.Flags().StringVar(&caFile, "ca", "", "Reads the CA certificate that signs Client Certificates.")
	_ = cmd.MarkFlagRequired("ca")
	return cmd
}

const clientHelp = `Manages the check of the Client Certificates of people.

Does:
  1. "create" trusts the Workstation CA. "import" trusts another CA.
  2. "enable" and "disable" turn the check on and off. They keep the files.

Note:
  - It uses tls/traefik/client-ca.crt and traefik/dynamic/11-client-auth.yaml.
  - It is on when YARD_CLIENT_AUTH_ENABLED is true.
  - A person's certificate comes from "ws tls ca issue user".
  - Traefik asks only on Routes whose Route Profile has clientCertificate: {}, and only on HTTPS.`

const clientCreateHelp = `Makes Traefik trust the Workstation CA for Client Certificates, and turns the check on.

Before:
  - Make the Workstation CA with "ws tls ca create".

Does:
  1. Copies ca/ca.crt to tls/traefik/client-ca.crt.
  2. Turns the check on.

Next:
  1. Sign a certificate for each person:
       ws tls ca issue user <name>
  2. Apply it:
       ws compose recreate

Note:
  - It signs nothing. The CA key is not used.`

const clientImportHelp = `Makes Traefik trust another CA for Client Certificates, and turns the check on.

Before:
  - Have the CA certificate as a file. The key of the CA is not needed.

Does:
  1. Checks that the file holds CA certificates that are valid now.
  2. Copies it to tls/traefik/client-ca.crt and turns the check on.

Note:
  - The file may hold more than one CA.`
