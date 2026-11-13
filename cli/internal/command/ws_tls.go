package command

import (
	"crypto/ecdsa"
	"crypto/x509"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

func newWSTLS(options *Options, folder func() string) *cobra.Command {
	tls := &cobra.Command{Use: "tls", Short: "Manages this Workstation's certificates and TLS.", Args: cobra.NoArgs}
	tls.AddCommand(newWSTLSCA(options, folder), newWSTLSImport(options, folder), newWSTLSEnable(folder), newWSTLSDisable(folder))
	return tls
}

const privateCAWarning = "Warning: LVH Yard makes this Yard CA for testing or development. Its key is a plain file on this computer, and nothing can revoke a certificate it signed.\nFor more than testing, use a more robust CA, such as a company CA or a public CA, and install its certificates with 'lvh-yard ws tls import'."

// newWSTLSCA holds every command that needs the Yard CA key.
func newWSTLSCA(options *Options, folder func() string) *cobra.Command {
	ca := &cobra.Command{
		Use: "ca", Short: "Makes the Yard CA and signs with it.", Long: "Makes the Yard CA and signs with it.\n\n" + privateCAWarning, Args: cobra.NoArgs,
		// After every command under ca that succeeded.
		PersistentPostRun: func(cmd *cobra.Command, args []string) {
			fmt.Fprintln(cmd.ErrOrStderr(), "\n"+privateCAWarning)
		},
	}
	var force bool
	var days int
	create := &cobra.Command{Use: "create", Short: "Makes a Yard CA without starting TLS.", Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
		if _, err := readWSEnv(folder()); err != nil {
			return err
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		dir := filepath.Join(folder(), "ca")
		for _, name := range []string{"ca.crt", "ca.key"} {
			if _, err := os.Lstat(filepath.Join(dir, name)); err == nil && !force {
				return fmt.Errorf("a Yard CA exists: use --force to replace it")
			} else if err != nil && !os.IsNotExist(err) {
				return err
			}
		}
		days, err := askDays(cmd, newPrompter(cmd, options), days, 3650, "CA validity in days")
		if err != nil {
			return err
		}
		cert, key, err := tlsca.NewCA(days)
		if err != nil {
			return err
		}
		if err := writeTLSCertificate(dir, "ca", cert, cert, key, key); err != nil {
			return err
		}
		fmt.Fprintf(cmd.OutOrStdout(), "Yard CA saved in %s.\nImport ca.crt into your browser's trusted certificate authorities. Never share ca.key.\n", dir)
		return nil
	}}
	create.Flags().BoolVar(&force, "force", false, "Replaces an existing Yard CA.")
	create.Flags().IntVar(&days, "days", 3650, "Sets the CA validity in days.")
	ca.AddCommand(create, newWSTLSIssue(options, folder), newWSTLSInstall(options, folder))
	return ca
}

// askDays uses the --days flag when it was given, and asks for the validity at a terminal.
func askDays(cmd *cobra.Command, p *prompter, flag, fallback int, question string) (int, error) {
	if cmd.Flags().Changed("days") || !p.terminal {
		return flag, nil
	}
	answer, err := p.text(strings.ToUpper(question[:1])+question[1:], strconv.Itoa(fallback), func(text string) error {
		if n, err := strconv.Atoi(text); err != nil || n < 1 || n > 100000 {
			return fmt.Errorf("Use a number of days from 1 to 100000.")
		}
		return nil
	})
	if err != nil {
		return 0, err
	}
	return strconv.Atoi(answer)
}

func writeTLSCertificate(dir, name string, cert, parent *x509.Certificate, key, signer *ecdsa.PrivateKey) error {
	certPEM, keyPEM, err := tlsca.Sign(cert, parent, key, signer)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}
	if err := os.Chmod(dir, 0700); err != nil {
		return err
	}
	for filename, data := range map[string][]byte{name + ".crt": certPEM, name + ".key": keyPEM} {
		if err := os.WriteFile(filepath.Join(dir, filename), data, 0600); err != nil {
			return err
		}
		if err := os.Chmod(filepath.Join(dir, filename), 0600); err != nil {
			return err
		}
	}
	return nil
}

func readYardCA(folder string) (*x509.Certificate, *ecdsa.PrivateKey, []byte, error) {
	caPEM, err := os.ReadFile(filepath.Join(folder, "ca", "ca.crt"))
	if err != nil {
		return nil, nil, nil, fmt.Errorf("this Workstation has no readable Yard CA: run lvh-yard ws tls ca create on the signing Workstation: %w", err)
	}
	keyPEM, err := os.ReadFile(filepath.Join(folder, "ca", "ca.key"))
	if err != nil {
		return nil, nil, nil, fmt.Errorf("this Workstation has no readable Yard CA key: sign certificates on the Workstation that made the CA: %w", err)
	}
	ca, signer, err := tlsca.ParseCA(caPEM, keyPEM)
	if err != nil {
		return nil, nil, nil, err
	}
	return ca, signer, caPEM, nil
}
