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
 "net"
 "os"
 "path/filepath"
 "time"

 "github.com/spf13/cobra"
)

func newWSTLS(folder func() string) *cobra.Command {
 tls := &cobra.Command{Use: "tls", Short: "Makes certificates for this Yard.", Args: cobra.NoArgs}
 ca := &cobra.Command{Use: "ca", Short: "Manages the Yard CA.", Args: cobra.NoArgs}
 var force bool
 var days int
 create := &cobra.Command{Use: "create", Short: "Makes a Yard CA without starting TLS.", Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
  if _, err := readWSEnv(folder()); err != nil { return err }
  if err := protectWorkstationFolder(folder()); err != nil { return err }
  dir := filepath.Join(folder(), "ca")
  for _, name := range []string{"ca.crt", "ca.key"} {
   if _, err := os.Lstat(filepath.Join(dir, name)); err == nil && !force { return fmt.Errorf("a Yard CA exists: use --force to replace it") } else if err != nil && !os.IsNotExist(err) { return err }
  }
  cert, key, err := newTLSCertificate("LVH Yard CA", days); if err != nil { return err }
  cert.IsCA = true
  cert.BasicConstraintsValid = true
  cert.KeyUsage = x509.KeyUsageCertSign | x509.KeyUsageCRLSign
  if err := writeTLSCertificate(dir, "ca", cert, cert, key, key); err != nil { return err }
  fmt.Fprintf(cmd.OutOrStdout(), "Yard CA saved in %s.\nImport ca.crt into your browser's trusted certificate authorities. Never share ca.key.\n", dir)
  return nil
 }}
 create.Flags().BoolVar(&force, "force", false, "Replaces an existing Yard CA.")
 create.Flags().IntVar(&days, "days", 3650, "Sets the CA validity in days.")
 ca.AddCommand(create); tls.AddCommand(ca)
 tls.AddCommand(newWSTLSCert(folder))
 return tls
}

func newTLSCertificate(name string, days int) (*x509.Certificate, *ecdsa.PrivateKey, error) {
 // time.Duration holds at most about 290 years.
 if days < 1 || days > 100000 { return nil, nil, fmt.Errorf("--days must be from 1 to 100000") }
 key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader); if err != nil { return nil, nil, err }
 serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128)); if err != nil { return nil, nil, err }
 now := time.Now().UTC().Truncate(time.Second)
 return &x509.Certificate{SerialNumber: serial, Subject: pkix.Name{CommonName: name}, NotBefore: now, NotAfter: now.Add(time.Duration(days)*24*time.Hour)}, key, nil
}

func writeTLSCertificate(dir, name string, cert, parent *x509.Certificate, key, signer *ecdsa.PrivateKey) error {
 der, err := x509.CreateCertificate(rand.Reader, cert, parent, &key.PublicKey, signer); if err != nil { return err }
 private, err := x509.MarshalECPrivateKey(key); if err != nil { return err }
 if err := os.MkdirAll(dir, 0700); err != nil { return err }
 if err := os.Chmod(dir, 0700); err != nil { return err }
 for filename, block := range map[string]*pem.Block{name+".crt": {Type: "CERTIFICATE", Bytes: der}, name+".key": {Type: "EC PRIVATE KEY", Bytes: private}} {
  if err := os.WriteFile(filepath.Join(dir, filename), pem.EncodeToMemory(block), 0600); err != nil { return err }
  if err := os.Chmod(filepath.Join(dir, filename), 0600); err != nil { return err }
 }
 return nil
}

func newWSTLSCert(folder func() string) *cobra.Command {
 cert := &cobra.Command{Use: "cert", Short: "Manages Workstation Certificates.", Args: cobra.NoArgs}
 var workstation, domain string
 var addresses []string
 var days int
 create := &cobra.Command{Use: "create", Short: "Signs a Workstation Certificate without starting TLS.", Args: cobra.NoArgs, RunE: func(cmd *cobra.Command, args []string) error {
  env, err := readWSEnv(folder()); if err != nil { return err }
  if err := protectWorkstationFolder(folder()); err != nil { return err }
  name := workstation
  if !cmd.Flags().Changed("workstation") { name = env.get("YARD_WORKSTATION") }
  if !workstationName.MatchString(name) { return fmt.Errorf("invalid --workstation: use a lowercase Workstation name") }
  base := domain
  if !cmd.Flags().Changed("base-domain") { base = env.valueOrDefault("YARD_BASE_DOMAIN") }
  if _, err := checkWSSetting("base-domain", base); err != nil { return err }
  caPEM, err := os.ReadFile(filepath.Join(folder(), "ca", "ca.crt")); if err != nil { return fmt.Errorf("this Workstation has no readable Yard CA: run lvh-yard ws tls ca create on the signing Workstation: %w", err) }
  keyPEM, err := os.ReadFile(filepath.Join(folder(), "ca", "ca.key")); if err != nil { return fmt.Errorf("this Workstation has no readable Yard CA key: sign certificates on the Workstation that made the CA: %w", err) }
  block, _ := pem.Decode(caPEM); if block == nil { return fmt.Errorf("invalid Yard CA certificate") }
  ca, err := x509.ParseCertificate(block.Bytes); if err != nil { return err }
  block, _ = pem.Decode(keyPEM); if block == nil { return fmt.Errorf("invalid Yard CA key") }
  signer, err := x509.ParseECPrivateKey(block.Bytes); if err != nil { return err }
  public, ok := ca.PublicKey.(*ecdsa.PublicKey)
  if !ok || !public.Equal(&signer.PublicKey) || !ca.IsCA || ca.KeyUsage&x509.KeyUsageCertSign == 0 { return fmt.Errorf("Yard CA key and signing certificate do not match") }
  leaf, key, err := newTLSCertificate(name, days); if err != nil { return err }
  leaf.DNSNames = []string{"server."+name+".consul", "localhost", "*."+base}
  leaf.IPAddresses = []net.IP{net.ParseIP("127.0.0.1")}
  for _, address := range addresses {
   if ip := net.ParseIP(address); ip != nil { leaf.IPAddresses = append(leaf.IPAddresses, ip) } else if baseDomainName.MatchString(address) { leaf.DNSNames = append(leaf.DNSNames, address) } else { return fmt.Errorf("invalid --address %q: use an IP address or DNS name", address) }
  }
  leaf.KeyUsage = x509.KeyUsageDigitalSignature
  leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth}
  if leaf.NotAfter.After(ca.NotAfter) || leaf.NotBefore.Before(ca.NotBefore) { return fmt.Errorf("certificate validity is outside the Yard CA validity: use a shorter --days value or make a new CA") }
  dir := filepath.Join(folder(), "tls")
  if cmd.Flags().Changed("workstation") { dir = filepath.Join(folder(), "certificates", name) }
  if err := writeTLSCertificate(dir, "workstation", leaf, ca, key, signer); err != nil { return err }
  if err := os.WriteFile(filepath.Join(dir,"ca.crt"), caPEM, 0600); err != nil { return err }
  if err := protectWorkstationFolder(folder()); err != nil { return err }
  fmt.Fprintf(cmd.OutOrStdout(), "Workstation Certificate saved in %s. TLS is not started.\nImport ca.crt into your browser's trusted certificate authorities. Never share the Yard CA key.\n", dir)
  return nil
 }}
 create.Flags().StringVar(&workstation, "workstation", "", "Signs a bundle for this Workstation name.")
 create.Flags().StringVar(&domain, "base-domain", "", "Uses this Base Domain for the certificate.")
 create.Flags().StringArrayVar(&addresses, "address", nil, "Adds an IP address or DNS name to the certificate.")
 create.Flags().IntVar(&days, "days", 365, "Sets the certificate validity in days.")
 cert.AddCommand(create)
 return cert
}
