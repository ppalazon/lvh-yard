package command

import (
	"fmt"
	"os"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/spf13/cobra"
)

// installRole checks a certificate, copies it to tls/<role>/ and turns the role on. The caller
// saves .env and prints the apply command with finishRoles.
func installRole(cmd *cobra.Command, folder string, env *envfile.File, role string, caPEM, certPEM, keyPEM []byte) error {
	if err := checkRoleCertificate(role, env, caPEM, certPEM, keyPEM); err != nil {
		return err
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	dir := roleDir(folder, role)
	if err := writeRole(root, roleDir("", role), role, caPEM, certPEM, keyPEM); err != nil {
		return err
	}
	if err := turnOnRole(folder, role, env); err != nil {
		return err
	}
	fmt.Fprintf(cmd.OutOrStdout(), "%s certificate installed in %s.\n", roleSettingName(role), dir)
	announceRole(cmd, role)
	return nil
}

// newWSTLSInstall installs the Traefik certificate and turns HTTPS on. It reads the files that this
// Workstation's CA signed for this Workstation, or the files that --ca, --cert and --key name. Both
// ways run the same checks. Consul has no such command: ws yard share and ws yard join install its
// certificate.
func newWSTLSInstall(options *Options, folder func() string) *cobra.Command {
	var caFile, certFile, keyFile string
	cmd := &cobra.Command{
		Use: "install", Short: "Installs the Traefik certificate.", Args: cobra.NoArgs,
		Long: `Installs the Traefik certificate and turns HTTPS on.

Before:
  - Without flags: sign it with "ws tls ca issue traefik".
  - With flags: have the certificate, its key and the CA certificate as files.

Does:
  1. Without flags, installs what the Workstation CA signed for this Workstation.
  2. With --ca, --cert and --key, installs the files that you name.
  3. Checks that the certificate names yard.<Base Domain>.

Note:
  - --ca is the CA of the Client Certificates, even when a public CA signed --cert.
  - Traefik uses --ca only to check Client Certificates, not for HTTPS.`,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if traefikOff(env) {
				return fmt.Errorf("Traefik is off on this Workstation. Turn it on with \"lvh-yard ws set traefik on\"")
			}
			var caPEM, certPEM, keyPEM []byte
			if cmd.Flags().Changed("cert") {
				caPEM, certPEM, keyPEM, err = readInstallFiles(caFile, certFile, keyFile)
			} else {
				self := strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")
				caPEM, certPEM, keyPEM, err = readOwnCertificate(folder(), wsCommand(folder(), options.WorkstationFolder()), self, roleTraefik)
			}
			if err != nil {
				return err
			}
			if err := installRole(cmd, folder(), env, roleTraefik, caPEM, certPEM, keyPEM); err != nil {
				return err
			}
			if err := saveWSEnv(cmd, folder(), env); err != nil {
				return err
			}
			return protectWorkstationFolder(folder())
		},
	}
	cmd.Flags().StringVar(&caFile, "ca", "", "Reads the CA certificate from this file (use with --cert and --key).")
	cmd.Flags().StringVar(&certFile, "cert", "", "Reads the certificate from this file (use with --ca and --key).")
	cmd.Flags().StringVar(&keyFile, "key", "", "Reads the certificate key from this file (use with --ca and --cert).")
	cmd.MarkFlagsRequiredTogether("ca", "cert", "key")
	return cmd
}

// readInstallFiles reads the three files that the flags name, which may be outside the Workstation
// Folder.
func readInstallFiles(caFile, certFile, keyFile string) (caPEM, certPEM, keyPEM []byte, err error) {
	if caPEM, err = os.ReadFile(caFile); err != nil { // #nosec G304 -- --ca selects an input file outside the Workstation Folder.
		return nil, nil, nil, fmt.Errorf("cannot read the CA certificate: %w", err)
	}
	if certPEM, err = os.ReadFile(certFile); err != nil { // #nosec G304 -- --cert selects an input file outside the Workstation Folder.
		return nil, nil, nil, fmt.Errorf("cannot read the certificate: %w", err)
	}
	if keyPEM, err = os.ReadFile(keyFile); err != nil { // #nosec G304 -- --key selects an input file outside the Workstation Folder.
		return nil, nil, nil, fmt.Errorf("cannot read the key: %w", err)
	}
	return caPEM, certPEM, keyPEM, nil
}

// readOwnCertificate reads what this Workstation's CA signed for this Workstation. It asks for no
// CA password, because it reads only certificates and the key of the certificate.
func readOwnCertificate(folder, ws, self, role string) (caPEM, certPEM, keyPEM []byte, err error) {
	if caPEM, err = readCAPEM(folder); err != nil {
		return nil, nil, nil, fmt.Errorf("%w. Or use --ca, --cert and --key", err)
	}
	certPEM, keyPEM, err = readSignedTLSRole(folder, self, role)
	if os.IsNotExist(err) {
		return nil, nil, nil, fmt.Errorf("this Workstation's CA has signed no %s certificate for %s. Run %s tls ca issue %s, or use --ca, --cert and --key", role, self, ws, role)
	}
	return caPEM, certPEM, keyPEM, err
}

// readSignedTLSRole reads what this Workstation's CA signed for a Workstation. A certificate that
// was never signed is an error that satisfies os.IsNotExist.
func readSignedTLSRole(folder, name, role string) (certPEM, keyPEM []byte, err error) {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return nil, nil, err
	}
	defer func() { _ = root.Close() }()
	if certPEM, err = root.ReadFile(issuedPath("", name, role, ".crt")); err != nil {
		return nil, nil, err
	}
	if keyPEM, err = root.ReadFile(issuedPath("", name, role, ".key")); err != nil {
		return nil, nil, err
	}
	return certPEM, keyPEM, nil
}
