package command

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"math/big"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

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
		cert, key, err := newTLSCertificate("LVH Yard CA", days)
		if err != nil {
			return err
		}
		cert.IsCA = true
		cert.BasicConstraintsValid = true
		cert.KeyUsage = x509.KeyUsageCertSign | x509.KeyUsageCRLSign
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

func newTLSCertificate(name string, days int) (*x509.Certificate, *ecdsa.PrivateKey, error) {
	// time.Duration holds at most about 290 years.
	if days < 1 || days > 100000 {
		return nil, nil, fmt.Errorf("--days must be from 1 to 100000")
	}
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, nil, err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return nil, nil, err
	}
	now := time.Now().UTC().Truncate(time.Second)
	return &x509.Certificate{SerialNumber: serial, Subject: pkix.Name{CommonName: name}, NotBefore: now, NotAfter: now.Add(time.Duration(days) * 24 * time.Hour)}, key, nil
}

func writeTLSCertificate(dir, name string, cert, parent *x509.Certificate, key, signer *ecdsa.PrivateKey) error {
	der, err := x509.CreateCertificate(rand.Reader, cert, parent, &key.PublicKey, signer)
	if err != nil {
		return err
	}
	private, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}
	if err := os.Chmod(dir, 0700); err != nil {
		return err
	}
	for filename, block := range map[string]*pem.Block{name + ".crt": {Type: "CERTIFICATE", Bytes: der}, name + ".key": {Type: "EC PRIVATE KEY", Bytes: private}} {
		if err := os.WriteFile(filepath.Join(dir, filename), pem.EncodeToMemory(block), 0600); err != nil {
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
	block, _ := pem.Decode(caPEM)
	if block == nil {
		return nil, nil, nil, fmt.Errorf("invalid Yard CA certificate")
	}
	ca, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return nil, nil, nil, err
	}
	block, _ = pem.Decode(keyPEM)
	if block == nil {
		return nil, nil, nil, fmt.Errorf("invalid Yard CA key")
	}
	signer, err := x509.ParseECPrivateKey(block.Bytes)
	if err != nil {
		return nil, nil, nil, err
	}
	public, ok := ca.PublicKey.(*ecdsa.PublicKey)
	if !ok || !public.Equal(&signer.PublicKey) || !ca.IsCA || ca.KeyUsage&x509.KeyUsageCertSign == 0 {
		return nil, nil, nil, fmt.Errorf("Yard CA key and signing certificate do not match")
	}
	return ca, signer, caPEM, nil
}
