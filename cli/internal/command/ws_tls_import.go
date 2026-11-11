package command

import (
 "crypto/tls"
 "crypto/x509"
 "fmt"
 "os"
 "path/filepath"

 "github.com/ppalazon/lvh-yard/cli/internal/templates"
 "github.com/spf13/cobra"
)


func writeConsulTLS(folder string) error {
 if err := os.MkdirAll(filepath.Join(folder,"consul.d"),0700); err != nil { return err }
 return os.WriteFile(filepath.Join(folder,"consul.d","10-tls.hcl"), []byte(templates.ConsulTLS()),0600)
}

func writeTraefikTLS(folder string) error {
 if err := os.MkdirAll(filepath.Join(folder,"traefik","dynamic"),0700); err != nil { return err }
 return os.WriteFile(filepath.Join(folder,"traefik","dynamic","10-tls.yaml"), []byte(templates.TraefikTLS()),0600)
}

func newWSTLSImport(folder func() string) *cobra.Command {
 var caFile, certFile, keyFile string
 cmd := &cobra.Command{Use:"import", Short:"Installs a Workstation Certificate and enables TLS.", Args:cobra.NoArgs, RunE:func(cmd *cobra.Command, args []string) error {
  env, err := readWSEnv(folder()); if err != nil { return err }
  caPEM, err := os.ReadFile(caFile); if err != nil { return fmt.Errorf("read CA: %w",err) }
  certPEM, err := os.ReadFile(certFile); if err != nil { return fmt.Errorf("read certificate: %w",err) }
  keyPEM, err := os.ReadFile(keyFile); if err != nil { return fmt.Errorf("read key: %w",err) }
  pair, err := tls.X509KeyPair(certPEM,keyPEM); if err != nil { return fmt.Errorf("certificate and key do not match or are invalid: %w",err) }
  leaf, err := x509.ParseCertificate(pair.Certificate[0]); if err != nil { return err }
  roots := x509.NewCertPool(); if !roots.AppendCertsFromPEM(caPEM) { return fmt.Errorf("invalid CA certificate") }
  intermediates := x509.NewCertPool()
  for _, der := range pair.Certificate[1:] { cert, err := x509.ParseCertificate(der); if err != nil { return err }; intermediates.AddCert(cert) }
  for _, usage := range []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth,x509.ExtKeyUsageClientAuth} {
   if _, err := leaf.Verify(x509.VerifyOptions{Roots:roots, Intermediates:intermediates, KeyUsages:[]x509.ExtKeyUsage{usage}}); err != nil { return fmt.Errorf("certificate is not valid for this CA and RPC usage: %w",err) }
  }
  name := "server."+env.get("YARD_WORKSTATION")+".consul"
  if err := leaf.VerifyHostname(name); err != nil { return fmt.Errorf("certificate needs %s: %w",name,err) }
  if err := protectWorkstationFolder(folder()); err != nil { return err }
  dir := filepath.Join(folder(),"tls")
  if err := os.MkdirAll(dir,0700); err != nil { return err }
  for name, data := range map[string][]byte{"ca.crt":caPEM,"workstation.crt":certPEM,"workstation.key":keyPEM} {
   if err := os.WriteFile(filepath.Join(dir,name),data,0600); err != nil { return err }
  }
  if err := writeConsulTLS(folder()); err != nil { return err }
  if env.get("YARD_TRAEFIK_ENABLED") != "false" {
   if err := writeTraefikTLS(folder()); err != nil { return err }
   if err := env.set("YARD_HTTPS_PORT","443"); err != nil { return err }
   if err := saveWSEnv(cmd,folder(),env); err != nil { return err }
  }
  if err := protectWorkstationFolder(folder()); err != nil { return err }
  fmt.Fprintln(cmd.OutOrStdout(),"Consul TLS is installed. Every Workstation in the Yard needs TLS. A Workstation without TLS cannot federate.\nApply it with docker compose up -d --force-recreate in the Workstation Folder. Import tls/ca.crt into your browser's trusted certificate authorities.")
  return nil
 }}
 cmd.Flags().StringVar(&caFile,"ca","","Reads this CA certificate.")
 cmd.Flags().StringVar(&certFile,"cert","","Reads this Workstation Certificate.")
 cmd.Flags().StringVar(&keyFile,"key","","Reads this certificate key.")
 for _, name := range []string{"ca","cert","key"} { _ = cmd.MarkFlagRequired(name) }
 return cmd
}
