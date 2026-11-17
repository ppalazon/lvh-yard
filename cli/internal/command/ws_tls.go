package command

import (
	"crypto/ecdsa"
	"crypto/x509"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf8"

	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

func newWSTLS(options *Options, folder func() string) *cobra.Command {
	tls := &cobra.Command{Use: "tls", Short: "Manages the certificates and TLS.", Args: cobra.NoArgs}
	tls.AddCommand(newWSTLSCA(options, folder), newWSTLSInstall(options, folder), newWSTLSEnable(folder), newWSTLSDisable(folder))
	return tls
}

const privateCAWarning = "Warning: this CA is for testing and development.\n  - Its key is a file on this computer.\n  - Nothing can revoke a certificate that it signed.\nFor more than testing, use a company CA or a public CA:\n  lvh-yard ws tls install --ca <file> --cert <file> --key <file>"

// newWSTLSCA holds every command that needs the key of this Workstation's CA.
func newWSTLSCA(options *Options, folder func() string) *cobra.Command {
	ca := &cobra.Command{
		Use: "ca", Short: "Makes the Workstation CA and signs with it.", Long: "Makes the Workstation CA and signs with it.\n\n" + privateCAWarning, Args: cobra.NoArgs,
		// After every command under ca that succeeded.
		PersistentPostRun: func(cmd *cobra.Command, args []string) {
			fmt.Fprintln(cmd.ErrOrStderr(), "\n"+privateCAWarning)
		},
	}
	ca.AddCommand(newWSTLSCACreate(options, folder), newWSTLSIssue(options, folder), newWSTLSExport(options, folder))
	return ca
}

func newWSTLSCACreate(options *Options, folder func() string) *cobra.Command {
	var force bool
	var days int
	var name string
	create := &cobra.Command{Use: "create", Short: "Makes the Workstation CA.", Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
		env, err := readWSEnv(folder())
		if err != nil {
			return err
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		root, err := os.OpenRoot(folder())
		if err != nil {
			return err
		}
		defer func() { _ = root.Close() }()
		if err := checkNoCA(root, force); err != nil {
			return err
		}
		p := newPrompter(cmd, options)
		if name, err = askCAName(cmd, p, name, strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")); err != nil {
			return err
		}
		days, err := askDays(cmd, p, days, 3650, "CA validity in days")
		if err != nil {
			return err
		}
		password, err := askCAKeyPassword(cmd, p)
		if err != nil {
			return err
		}
		cert, key, err := tlsca.NewCA(name, days)
		if err != nil {
			return err
		}
		if err := writeTLSCertificate(root, "ca", "ca", cert, cert, key, key, password); err != nil {
			return err
		}
		out := cmd.OutOrStdout()
		fmt.Fprintf(out, "CA %q saved in %s.\nNext:\n  Import ca.crt into your browser's trusted authorities.\n  Never send ca.key.\n", name, filepath.Join(folder(), "ca"))
		if password != "" {
			fmt.Fprintf(out, "ca.key has a password.\n  Commands that sign ask for it, or take --%s or --%s.\n  Nothing can recover it.\n", caKeyPasswordFlag, caKeyPasswordFileFlag)
		}
		return nil
	}}
	create.Flags().BoolVar(&force, "force", false, "Replaces the existing CA.")
	create.Flags().IntVar(&days, "days", 3650, "Sets the CA validity in days.")
	create.Flags().StringVar(&name, "name", "", "Sets the CA common name (default: LVH Yard CA - <Workstation>).")
	addCAKeyPasswordFlags(create.Flags(), "Sets the ca.key password (default: none).", "Reads the ca.key password from a file, or - (default: none).")
	return create
}

// checkNoCA refuses to replace this Workstation's CA, unless force is set.
func checkNoCA(root *os.Root, force bool) error {
	for _, path := range []string{caCertPath, caKeyPath} {
		_, err := root.Lstat(path)
		switch {
		case err == nil && !force:
			return fmt.Errorf("a CA exists in %s. Use --force to replace it", path)
		case err != nil && !os.IsNotExist(err):
			return err
		}
	}
	return nil
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

// writeTLSCertificate writes name.crt and name.key. A key password encrypts the key; leaf
// certificates pass none.
func writeTLSCertificate(root *os.Root, dir, name string, cert, parent *x509.Certificate, key, signer *ecdsa.PrivateKey, keyPassword string) error {
	certPEM, keyPEM, err := tlsca.Sign(cert, parent, key, signer)
	if err != nil {
		return err
	}
	if keyPassword != "" {
		if keyPEM, err = tlsca.EncryptKey(key, keyPassword); err != nil {
			return err
		}
	}
	if err := root.MkdirAll(dir, 0700); err != nil {
		return err
	}
	directory, err := root.Open(dir)
	if err != nil {
		return err
	}
	if err := directory.Chmod(0700); err != nil {
		_ = directory.Close()
		return err
	}
	if err := directory.Close(); err != nil {
		return err
	}
	for filename, data := range map[string][]byte{name + ".crt": certPEM, name + ".key": keyPEM} {
		file, err := root.OpenFile(filepath.Join(dir, filename), os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0600)
		if err != nil {
			return err
		}
		if err := file.Chmod(0600); err != nil {
			_ = file.Close()
			return err
		}
		if _, err := file.Write(data); err != nil {
			_ = file.Close()
			return err
		}
		if err := file.Close(); err != nil {
			return err
		}
	}
	return nil
}

// Where this Workstation's CA keeps its files, relative to the Workstation Folder. What it signed
// is in ca/certs/<name>/ and ca/clients/.
const (
	caCertPath = "ca/ca.crt"
	caKeyPath  = "ca/ca.key"
)

// readCAPEM reads the certificate of this Workstation's CA alone, which install and export need.
// It asks for no password.
func readCAPEM(folder string) ([]byte, error) {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return nil, err
	}
	defer func() { _ = root.Close() }()
	caPEM, err := root.ReadFile(caCertPath)
	if err != nil {
		return nil, fmt.Errorf("this Workstation has no readable CA. Run \"lvh-yard ws tls ca create\": %w", err)
	}
	if _, err := tlsca.ParseCACertificate(caPEM); err != nil {
		return nil, err
	}
	return caPEM, nil
}

// readCA reads this Workstation's CA with its key, to sign. A key with a password is opened with
// --ca-key-password, or with the answer to a question at a terminal.
func readCA(cmd *cobra.Command, p *prompter, folder string) (*x509.Certificate, *ecdsa.PrivateKey, error) {
	caPEM, err := readCAPEM(folder)
	if err != nil {
		return nil, nil, err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return nil, nil, err
	}
	defer func() { _ = root.Close() }()
	keyPEM, err := root.ReadFile(caKeyPath)
	if err != nil {
		return nil, nil, fmt.Errorf("this Workstation has no readable CA key. Sign on the Workstation that made the CA: %w", err)
	}
	password := ""
	if tlsca.KeyEncrypted(keyPEM) {
		if password, err = askCAKeyUnlock(cmd, p); err != nil {
			return nil, nil, err
		}
	}
	return tlsca.ParseCA(caPEM, keyPEM, password)
}

// askCAName gives the common name of a new CA certificate: the flag, or the answer to a
// question that offers the default.
func askCAName(cmd *cobra.Command, p *prompter, flag, workstation string) (string, error) {
	name := "LVH Yard CA - " + workstation
	switch {
	case cmd.Flags().Changed("name"):
		name = flag
	case p.terminal:
		var err error
		if name, err = p.text("CA common name", name, checkCAName); err != nil {
			return "", err
		}
	}
	return name, checkCAName(name)
}

// checkCAName keeps the name to what a common name holds: 1 to 64 characters, no control characters.
func checkCAName(name string) error {
	if strings.TrimSpace(name) == "" || utf8.RuneCountInString(name) > 64 || strings.ContainsFunc(name, unicode.IsControl) {
		return fmt.Errorf("Use a name of 1 to 64 characters, without control characters.")
	}
	return nil
}
