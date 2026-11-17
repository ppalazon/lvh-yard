package command

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

const issueUserHelp = `Signs a Client Certificate for a person, in a .p12 file.

Does:
  1. Signs the certificate with the Workstation CA.
  2. Writes it in ca/client/<name>.p12.

Next:
  1. Import the .p12 file into the browser of the person.

Note:
  - Traefik asks for it on Routes whose Route Profile has clientCertificate: {}.
  - Turn that check on with "ws tls client create".`

func newWSTLSIssueUser(options *Options, folder func() string) *cobra.Command {
	var password string
	var days int
	create := &cobra.Command{Use: "user [username-or-email]", Short: "Signs a Client Certificate for a user.", Long: issueUserHelp, Args: cobra.MaximumNArgs(1), RunE: func(cmd *cobra.Command, args []string) error {
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
		name, err := askTLSClientName(p, args)
		if err != nil {
			return err
		}
		var given bool
		password, given, err = askTLSClientPassword(cmd, p, password, "Password of the PKCS#12 file (empty for a random one)")
		if err != nil {
			return err
		}
		days, err := askDays(cmd, p, days, 365, "certificate validity in days")
		if err != nil {
			return err
		}
		secret := password
		generated := !given
		if generated {
			if secret, err = tlsca.RandomPassword(); err != nil {
				return err
			}
		}
		data, err := tlsca.ClientBundle(name, days, ca, signer, secret)
		if err != nil {
			return err
		}
		dir := filepath.Join(folder(), "ca", clientDir)
		root, err := os.OpenRoot(folder())
		if err != nil {
			return err
		}
		defer func() { _ = root.Close() }()
		if err := root.MkdirAll(filepath.Join("ca", clientDir), 0700); err != nil {
			return err
		}
		path := filepath.Join(dir, name+".p12")
		if err := root.WriteFile(filepath.Join("ca", clientDir, name+".p12"), data, 0600); err != nil {
			return err
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		fmt.Fprintf(cmd.OutOrStdout(), "Client Certificate saved in %s.\n", path)
		if generated {
			fmt.Fprintf(cmd.OutOrStdout(), "Password: %s\n  Save it now. It is not shown again.\n", secret)
		}
		fmt.Fprintln(cmd.OutOrStdout(), "Next:\n  Import the .p12 file into your browser's personal certificates.")
		return nil
	}}
	create.Flags().StringVar(&password, "password", "", "Sets the .p12 file password (default: ask, or make a random one).")
	create.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
	return create
}

func askTLSClientName(p *prompter, args []string) (string, error) {
	var name string
	switch {
	case len(args) == 1:
		name = args[0]
	case p.terminal:
		var err error
		name, err = p.text("Username or email", "", func(text string) error {
			if text == "" {
				return fmt.Errorf("A Client Certificate needs a username or an email.")
			}
			return nil
		})
		if err != nil {
			return "", err
		}
	default:
		return "", fmt.Errorf("Missing the username or email. Give it as an argument")
	}
	if strings.TrimSpace(name) == "" || name == "." || name == ".." || strings.ContainsAny(name, "/\\\x00") {
		return "", fmt.Errorf("invalid username or email. Use a name without path separators")
	}
	if err := validateOutputName(name); err != nil {
		return "", err
	}
	return name, nil
}

func askTLSClientPassword(cmd *cobra.Command, p *prompter, password, question string) (string, bool, error) {
	given := cmd.Flags().Changed("password")
	if given && password == "" {
		return password, given, fmt.Errorf("--password must not be empty")
	}
	if !given && p.terminal {
		typed, err := p.newSecret(question)
		if err != nil {
			return password, given, err
		}
		password, given = typed, typed != ""
	}
	return password, given, nil
}
