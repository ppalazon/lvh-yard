package command

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// A Workstation has one certificate for each job. Consul uses its own with the Yard CA. Traefik
// uses another for HTTPS, from the Yard CA or from a public or company CA. Each has its own key,
// and Consul and Traefik mount only their own folder.
const (
	roleConsul  = "consul"
	roleTraefik = "traefik"
)

var tlsRoles = []string{roleConsul, roleTraefik}

func roleSettingName(role string) string {
	if role == roleConsul {
		return "Consul"
	}
	return "Traefik"
}

// roleDir is where Compose mounts the files of one role: tls/consul or tls/traefik.
func roleDir(folder, role string) string {
	return filepath.Join(folder, "tls", role)
}

// roleFiles are the three files of a role in its folder: the CA to check peers or clients with,
// the certificate and the key.
func roleFiles(role string) [3]string {
	return [3]string{"ca.crt", role + ".crt", role + ".key"}
}

func readRole(dir, role string) (caPEM, certPEM, keyPEM []byte, err error) {
	var pems [3][]byte
	for i, name := range roleFiles(role) {
		if pems[i], err = os.ReadFile(filepath.Join(dir, name)); err != nil {
			return nil, nil, nil, err
		}
	}
	return pems[0], pems[1], pems[2], nil
}

func writeRole(dir, role string, caPEM, certPEM, keyPEM []byte) error {
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}
	for i, data := range [3][]byte{caPEM, certPEM, keyPEM} {
		if err := os.WriteFile(filepath.Join(dir, roleFiles(role)[i]), data, 0600); err != nil {
			return err
		}
	}
	return nil
}

// issuedPath is where the Yard CA keeps what it signed for a Workstation.
func issuedPath(folder, workstation, role, extension string) string {
	return filepath.Join(folder, "ca", "certs", workstation, role+extension)
}

// bundleRoot holds the folders to copy to another Workstation: one folder for each role, laid out
// as its tls/ folder is.
func bundleRoot(folder, workstation string) string {
	return filepath.Join(folder, "certificates", workstation)
}

// checkRoleCertificate refuses a certificate that Consul or Traefik could not use here.
func checkRoleCertificate(role string, env *envFile, caPEM, certPEM, keyPEM []byte) error {
	pair, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil {
		return fmt.Errorf("certificate and key do not match or are invalid: %w", err)
	}
	leaf, err := x509.ParseCertificate(pair.Certificate[0])
	if err != nil {
		return err
	}
	roots := x509.NewCertPool()
	if !roots.AppendCertsFromPEM(caPEM) {
		return fmt.Errorf("invalid CA certificate")
	}
	intermediates := x509.NewCertPool()
	for _, der := range pair.Certificate[1:] {
		cert, err := x509.ParseCertificate(der)
		if err != nil {
			return err
		}
		intermediates.AddCert(cert)
	}
	if role == roleConsul {
		for _, usage := range []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth} {
			if _, err := leaf.Verify(x509.VerifyOptions{Roots: roots, Intermediates: intermediates, KeyUsages: []x509.ExtKeyUsage{usage}}); err != nil {
				return fmt.Errorf("certificate is not valid for this CA and RPC usage: %w", err)
			}
		}
		name := "server." + strings.Trim(env.get("YARD_WORKSTATION"), "\"'") + ".consul"
		if err := leaf.VerifyHostname(name); err != nil {
			return fmt.Errorf("certificate needs %s: %w", name, err)
		}
		return nil
	}
	// Browsers need only a server certificate, and a public CA may have signed it. The Yard CA
	// file is what Traefik checks Client Certificates with.
	options := x509.VerifyOptions{Intermediates: intermediates, KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}
	options.Roots = roots
	if _, yardErr := leaf.Verify(options); yardErr != nil {
		options.Roots = nil
		if _, publicErr := leaf.Verify(options); publicErr != nil {
			return fmt.Errorf("certificate is valid neither for the given CA nor for a public CA: %w", yardErr)
		}
	}
	name := "yard." + env.valueOrDefault("YARD_BASE_DOMAIN")
	if err := leaf.VerifyHostname(name); err != nil {
		return fmt.Errorf("certificate needs %s, the address of the Yard Route: %w", name, err)
	}
	return nil
}

// checkInstalledRole runs the checks of an import on what is in tls/<role>/, so a certificate
// that was copied there by hand is checked too.
func checkInstalledRole(folder, role string, env *envFile) error {
	caPEM, certPEM, keyPEM, err := readRole(roleDir(folder, role), role)
	if err != nil {
		return fmt.Errorf("this Workstation has no %s certificate in tls/%s: install one with lvh-yard ws tls import %s, or with lvh-yard ws tls ca install on the Workstation with the Yard CA: %w", role, role, role, err)
	}
	if err := checkRoleCertificate(role, env, caPEM, certPEM, keyPEM); err != nil {
		return fmt.Errorf("the certificate in tls/%s cannot be used: %w", role, err)
	}
	return nil
}
