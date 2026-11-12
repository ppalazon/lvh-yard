package command

import (
	"crypto/rand"
	"crypto/x509"
	"encoding/base64"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
	pkcs12 "software.sslmate.com/src/go-pkcs12"
)

func newWSTLSIssueUser(options *Options, folder func() string) *cobra.Command {
	var password string
	var days int
	create := &cobra.Command{Use: "user [username-or-email]", Short: "Signs a Client Certificate for the browser of a user.", Args: cobra.MaximumNArgs(1), RunE: func(cmd *cobra.Command, args []string) error {
		if _, err := readWSEnv(folder()); err != nil {
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
		var name string
		switch {
		case len(args) == 1:
			name = args[0]
		case p.terminal:
			name, err = p.text("Username or email", "", func(text string) error {
				if text == "" {
					return fmt.Errorf("A Client Certificate needs a username or an email.")
				}
				return nil
			})
			if err != nil {
				return err
			}
		default:
			return fmt.Errorf("Missing the username or email: no terminal to ask for it, so give it as an argument")
		}
		if strings.TrimSpace(name) == "" || name == "." || name == ".." || strings.ContainsAny(name, "/\\\x00") {
			return fmt.Errorf("invalid username or email: use a name without path separators")
		}
		given := cmd.Flags().Changed("password")
		if given && password == "" {
			return fmt.Errorf("--password must not be empty")
		}
		if !given && p.terminal {
			typed, err := p.text("Password of the PKCS#12 file (empty for a random one)", "", nil)
			if err != nil {
				return err
			}
			password, given = typed, typed != ""
		}
		days, err := askDays(cmd, p, days, 365, "certificate validity in days")
		if err != nil {
			return err
		}
		leaf, key, err := newTLSCertificate(name, days)
		if err != nil {
			return err
		}
		leaf.KeyUsage = x509.KeyUsageDigitalSignature
		leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth}
		if leaf.NotAfter.After(ca.NotAfter) || leaf.NotBefore.Before(ca.NotBefore) {
			return fmt.Errorf("certificate validity is outside the Yard CA validity: use a shorter --days value or make a new CA")
		}
		der, err := x509.CreateCertificate(rand.Reader, leaf, ca, &key.PublicKey, signer)
		if err != nil {
			return err
		}
		cert, err := x509.ParseCertificate(der)
		if err != nil {
			return err
		}
		secret := password
		generated := !given
		if generated {
			var random [24]byte
			if _, err := rand.Read(random[:]); err != nil {
				return err
			}
			secret = base64.RawURLEncoding.EncodeToString(random[:])
		}
		data, err := pkcs12.Modern.Encode(key, cert, []*x509.Certificate{ca}, secret)
		if err != nil {
			return err
		}
		dir := filepath.Join(folder(), "ca", "clients")
		if err := os.MkdirAll(dir, 0700); err != nil {
			return err
		}
		path := filepath.Join(dir, name+".p12")
		if err := os.WriteFile(path, data, 0600); err != nil {
			return err
		}
		if err := protectWorkstationFolder(folder()); err != nil {
			return err
		}
		fmt.Fprintf(cmd.OutOrStdout(), "Client Certificate saved in %s. Import it into your browser's personal certificates.\n", path)
		if generated {
			fmt.Fprintf(cmd.OutOrStdout(), "Password: %s\n", secret)
		}
		return nil
	}}
	create.Flags().StringVar(&password, "password", "", "Sets the password of the PKCS#12 file.")
	create.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
	return create
}
