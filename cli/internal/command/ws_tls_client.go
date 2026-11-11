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

func newWSTLSClient(folder func() string) *cobra.Command {
 client := &cobra.Command{Use: "client", Short: "Manages Client Certificates.", Args: cobra.NoArgs}
 var password string
 var days int
 create := &cobra.Command{Use: "create <name>", Short: "Signs a Client Certificate for a browser.", Args: cobra.ExactArgs(1), RunE: func(cmd *cobra.Command, args []string) error {
  name := args[0]
  if strings.TrimSpace(name) == "" || name == "." || name == ".." || strings.ContainsAny(name, "/\\\x00") { return fmt.Errorf("invalid client name: use a name without path separators") }
  if cmd.Flags().Changed("password") && password == "" { return fmt.Errorf("--password must not be empty") }
  if _, err := readWSEnv(folder()); err != nil { return err }
  if err := protectWorkstationFolder(folder()); err != nil { return err }
  ca, signer, _, err := readYardCA(folder()); if err != nil { return err }
  leaf, key, err := newTLSCertificate(name, days); if err != nil { return err }
  leaf.KeyUsage = x509.KeyUsageDigitalSignature
  leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth}
  if leaf.NotAfter.After(ca.NotAfter) || leaf.NotBefore.Before(ca.NotBefore) { return fmt.Errorf("certificate validity is outside the Yard CA validity: use a shorter --days value or make a new CA") }
  der, err := x509.CreateCertificate(rand.Reader, leaf, ca, &key.PublicKey, signer); if err != nil { return err }
  cert, err := x509.ParseCertificate(der); if err != nil { return err }
  secret := password
  generated := !cmd.Flags().Changed("password")
  if generated {
   var random [24]byte
   if _, err := rand.Read(random[:]); err != nil { return err }
   secret = base64.RawURLEncoding.EncodeToString(random[:])
  }
  data, err := pkcs12.Modern.Encode(key, cert, []*x509.Certificate{ca}, secret); if err != nil { return err }
  dir := filepath.Join(folder(), "certificates", "clients")
  if err := os.MkdirAll(dir, 0700); err != nil { return err }
  path := filepath.Join(dir, name+".p12")
  if err := os.WriteFile(path, data, 0600); err != nil { return err }
  if err := protectWorkstationFolder(folder()); err != nil { return err }
  fmt.Fprintf(cmd.OutOrStdout(), "Client Certificate saved in %s. Import it into your browser's personal certificates.\n", path)
  if generated { fmt.Fprintf(cmd.OutOrStdout(), "Password: %s\n", secret) }
  return nil
 }}
 create.Flags().StringVar(&password, "password", "", "Sets the password of the PKCS#12 file.")
 create.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
 client.AddCommand(create)
 return client
}
