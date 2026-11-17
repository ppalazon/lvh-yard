// Package tlsca makes and checks the certificates of a Workstation: its CA, the Consul and Traefik
// certificates of a Workstation, and the Client Certificates of people. It reads and writes no
// file, and asks nothing.
package tlsca

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/pem"
	"fmt"
	"math/big"
	"net"
	"slices"
	"time"

	pkcs12 "software.sslmate.com/src/go-pkcs12"
)

// NewCertificate is a template for a certificate with a new key and a random serial number. The
// caller sets its names and usages.
func NewCertificate(name string, days int) (*x509.Certificate, *ecdsa.PrivateKey, error) {
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

// NewCA is the template of a Workstation's CA, which signs itself. name is its common name.
func NewCA(name string, days int) (*x509.Certificate, *ecdsa.PrivateKey, error) {
	cert, key, err := NewCertificate(name, days)
	if err != nil {
		return nil, nil, err
	}
	cert.IsCA = true
	cert.BasicConstraintsValid = true
	cert.KeyUsage = x509.KeyUsageCertSign | x509.KeyUsageCRLSign
	return cert, key, nil
}

// FillConsul sets the names and usages of the Consul certificate. Consul checks
// server.<workstation>.consul and needs both usages.
func FillConsul(leaf *x509.Certificate, workstation string) {
	leaf.KeyUsage = x509.KeyUsageDigitalSignature
	leaf.IPAddresses = []net.IP{net.ParseIP("127.0.0.1")}
	leaf.DNSNames = []string{"server." + workstation + ".consul", "localhost"}
	leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth}
}

// FillTraefik sets the names and usages of the Traefik certificate for HTTPS. Traefik answers
// browsers, which need the Route names and a server usage. An address is an IP address or a DNS
// name.
func FillTraefik(leaf *x509.Certificate, baseDomain string, addresses []string) {
	leaf.KeyUsage = x509.KeyUsageDigitalSignature
	leaf.IPAddresses = []net.IP{net.ParseIP("127.0.0.1")}
	leaf.DNSNames = []string{"*." + baseDomain, "localhost"}
	leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}
	addAddresses(leaf, addresses)
}

// FillService sets the names and usages of the certificate of a Service. Traefik checks the
// address of the Instance against these names, so the caller gives that address. Both usages let
// the same certificate serve and call other Services.
func FillService(leaf *x509.Certificate, name string, addresses []string) {
	leaf.KeyUsage = x509.KeyUsageDigitalSignature
	leaf.IPAddresses = []net.IP{net.ParseIP("127.0.0.1")}
	leaf.DNSNames = []string{name, "localhost"}
	leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth}
	addAddresses(leaf, addresses)
}

// FillClient sets the usage of a certificate that only shows who the caller is. Traefik shows one
// to the Services that it calls over mutual TLS.
func FillClient(leaf *x509.Certificate) {
	leaf.KeyUsage = x509.KeyUsageDigitalSignature
	leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth}
}

func addAddresses(leaf *x509.Certificate, addresses []string) {
	for _, address := range addresses {
		if ip := net.ParseIP(address); ip != nil {
			leaf.IPAddresses = append(leaf.IPAddresses, ip)
			continue
		}
		leaf.DNSNames = append(leaf.DNSNames, address)
	}
}

// Names are the DNS names and IP addresses of a certificate, as DNS:name and IP:address.
func Names(cert *x509.Certificate) []string {
	names := make([]string, 0, len(cert.DNSNames)+len(cert.IPAddresses))
	for _, name := range cert.DNSNames {
		names = append(names, "DNS:"+name)
	}
	for _, ip := range cert.IPAddresses {
		names = append(names, "IP:"+ip.String())
	}
	return names
}

// CheckWithin refuses a certificate that is valid at a time when its CA is not.
func CheckWithin(leaf, ca *x509.Certificate) error {
	if leaf.NotAfter.After(ca.NotAfter) || leaf.NotBefore.Before(ca.NotBefore) {
		return fmt.Errorf("certificate validity is outside the CA validity: use a shorter --days value or make a new CA")
	}
	return nil
}

// Sign gives the PEM of the certificate that parent signs with signer, and the PEM of its key. The
// CA signs itself with parent == cert and signer == key.
func Sign(cert, parent *x509.Certificate, key, signer *ecdsa.PrivateKey) (certPEM, keyPEM []byte, err error) {
	der, err := x509.CreateCertificate(rand.Reader, cert, parent, &key.PublicKey, signer)
	if err != nil {
		return nil, nil, err
	}
	private, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		return nil, nil, err
	}
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}),
		pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: private}), nil
}

// ParseCACertificate reads the CA certificate from its PEM file.
func ParseCACertificate(caPEM []byte) (*x509.Certificate, error) {
	block, _ := pem.Decode(caPEM)
	if block == nil {
		return nil, fmt.Errorf("invalid CA certificate")
	}
	return x509.ParseCertificate(block.Bytes)
}

// ParseCA reads a CA from its PEM files, and refuses a key that does not belong to a CA
// certificate that can sign. The password opens a key that has one: see KeyEncrypted. A key without
// a password ignores it.
func ParseCA(caPEM, keyPEM []byte, password string) (*x509.Certificate, *ecdsa.PrivateKey, error) {
	ca, err := ParseCACertificate(caPEM)
	if err != nil {
		return nil, nil, err
	}
	block, _ := pem.Decode(keyPEM)
	if block == nil {
		return nil, nil, fmt.Errorf("invalid CA key")
	}
	var signer *ecdsa.PrivateKey
	if block.Type == encryptedKeyType {
		if password == "" {
			return nil, nil, ErrPasswordRequired
		}
		signer, err = decryptKey(block.Bytes, password)
	} else {
		signer, err = x509.ParseECPrivateKey(block.Bytes)
	}
	if err != nil {
		return nil, nil, err
	}
	public, ok := ca.PublicKey.(*ecdsa.PublicKey)
	if !ok || !public.Equal(&signer.PublicKey) || !ca.IsCA || ca.KeyUsage&x509.KeyUsageCertSign == 0 {
		return nil, nil, fmt.Errorf("CA key and signing certificate do not match")
	}
	return ca, signer, nil
}

// ClientBundle signs a Client Certificate for a person, and gives it with its key and the CA as a
// PKCS#12 file.
func ClientBundle(name string, days int, ca *x509.Certificate, signer *ecdsa.PrivateKey, password string) ([]byte, error) {
	leaf, key, err := NewCertificate(name, days)
	if err != nil {
		return nil, err
	}
	leaf.KeyUsage = x509.KeyUsageDigitalSignature
	leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth}
	if err := CheckWithin(leaf, ca); err != nil {
		return nil, err
	}
	der, err := x509.CreateCertificate(rand.Reader, leaf, ca, &key.PublicKey, signer)
	if err != nil {
		return nil, err
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		return nil, err
	}
	return pkcs12.Modern.Encode(key, cert, []*x509.Certificate{ca}, password)
}

// RandomPassword is 24 random bytes in base64.
func RandomPassword() (string, error) {
	var random [24]byte
	if _, err := rand.Read(random[:]); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(random[:]), nil
}

// CheckConsul refuses a certificate that Consul of the Workstation could not use: it must work with
// the CA for server and client use, and name server.<workstation>.consul.
func CheckConsul(caPEM, certPEM, keyPEM []byte, workstation string) error {
	leaf, roots, intermediates, err := parseBundle(caPEM, certPEM, keyPEM)
	if err != nil {
		return err
	}
	for _, usage := range []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth} {
		if _, err := leaf.Verify(x509.VerifyOptions{Roots: roots, Intermediates: intermediates, KeyUsages: []x509.ExtKeyUsage{usage}}); err != nil {
			return fmt.Errorf("certificate is not valid for this CA and RPC usage: %w", err)
		}
	}
	name := "server." + workstation + ".consul"
	if err := leaf.VerifyHostname(name); err != nil {
		return fmt.Errorf("certificate needs %s: %w", name, err)
	}
	return nil
}

// CheckTraefik refuses a certificate that Traefik could not use for HTTPS at the address of the Yard
// Route. Browsers need only a server certificate. It must be valid for the given CA, or for a
// public CA. Without a CA file, only a public CA counts.
func CheckTraefik(caPEM, certPEM, keyPEM []byte, yardHostname string) error {
	leaf, intermediates, err := parseLeaf(certPEM, keyPEM)
	if err != nil {
		return err
	}
	options := x509.VerifyOptions{Intermediates: intermediates, KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}
	var yardErr error
	if len(caPEM) != 0 {
		options.Roots = x509.NewCertPool()
		if !options.Roots.AppendCertsFromPEM(caPEM) {
			return fmt.Errorf("invalid CA certificate")
		}
		if _, yardErr = leaf.Verify(options); yardErr == nil {
			return checkHostname(leaf, yardHostname)
		}
		options.Roots = nil
	}
	if _, publicErr := leaf.Verify(options); publicErr != nil {
		if yardErr == nil {
			yardErr = publicErr
		}
		return fmt.Errorf("certificate is valid neither for the given CA nor for a public CA: %w", yardErr)
	}
	return checkHostname(leaf, yardHostname)
}

// CheckHTTPS refuses a certificate that Traefik could not use for HTTPS now: the key must match,
// it must be valid now and name the address of the Yard Route. The CA that signed it is not known
// here, so the chain is not checked.
func CheckHTTPS(certPEM, keyPEM []byte, yardHostname string) error {
	leaf, _, err := parseLeaf(certPEM, keyPEM)
	if err != nil {
		return err
	}
	now := time.Now()
	if now.Before(leaf.NotBefore) || now.After(leaf.NotAfter) {
		return fmt.Errorf("the certificate is not valid now")
	}
	return checkHostname(leaf, yardHostname)
}

func checkHostname(leaf *x509.Certificate, yardHostname string) error {
	if err := leaf.VerifyHostname(yardHostname); err != nil {
		return fmt.Errorf("certificate needs %s, the address of the Yard Route: %w", yardHostname, err)
	}
	return nil
}

// CheckCA refuses a file that holds no CA certificate, a certificate that is not a CA, or only
// certificates that are not valid now. Traefik uses such a file to check the Client Certificates of
// people, and to check the Services that it calls. A file may hold more than one CA.
func CheckCA(caPEM []byte) error {
	found, valid := 0, 0
	now := time.Now()
	for rest := caPEM; ; {
		var block *pem.Block
		block, rest = pem.Decode(rest)
		if block == nil {
			break
		}
		if block.Type != "CERTIFICATE" {
			continue
		}
		cert, err := x509.ParseCertificate(block.Bytes)
		if err != nil {
			return fmt.Errorf("invalid CA certificate: %w", err)
		}
		if !cert.IsCA {
			return fmt.Errorf("%q is not a CA certificate", cert.Subject.CommonName)
		}
		found++
		if !now.Before(cert.NotBefore) && !now.After(cert.NotAfter) {
			valid++
		}
	}
	switch {
	case found == 0:
		return fmt.Errorf("invalid CA certificate")
	case valid == 0:
		return fmt.Errorf("the CA certificate has expired or is not valid yet")
	}
	return nil
}

// CheckMutualTLS refuses a certificate that Traefik could not show to a Service: the key must match,
// the certificate must be valid now and allow client use. The Service has its own CA, so the
// certificate is not checked against the CA file, which checks the Services.
func CheckMutualTLS(certPEM, keyPEM []byte) error {
	pair, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil {
		return fmt.Errorf("certificate and key do not match or are invalid: %w", err)
	}
	leaf, err := x509.ParseCertificate(pair.Certificate[0])
	if err != nil {
		return err
	}
	now := time.Now()
	if now.Before(leaf.NotBefore) || now.After(leaf.NotAfter) {
		return fmt.Errorf("the certificate is not valid now")
	}
	if len(leaf.ExtKeyUsage) != 0 && !slices.Contains(leaf.ExtKeyUsage, x509.ExtKeyUsageClientAuth) && !slices.Contains(leaf.ExtKeyUsage, x509.ExtKeyUsageAny) {
		return fmt.Errorf("the certificate does not allow client use")
	}
	return nil
}

func parseBundle(caPEM, certPEM, keyPEM []byte) (leaf *x509.Certificate, roots, intermediates *x509.CertPool, err error) {
	leaf, intermediates, err = parseLeaf(certPEM, keyPEM)
	if err != nil {
		return nil, nil, nil, err
	}
	roots = x509.NewCertPool()
	if !roots.AppendCertsFromPEM(caPEM) {
		return nil, nil, nil, fmt.Errorf("invalid CA certificate")
	}
	return leaf, roots, intermediates, nil
}

// parseLeaf reads a certificate and its key, and gives the leaf with the rest of the chain.
func parseLeaf(certPEM, keyPEM []byte) (leaf *x509.Certificate, intermediates *x509.CertPool, err error) {
	pair, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil {
		return nil, nil, fmt.Errorf("certificate and key do not match or are invalid: %w", err)
	}
	leaf, err = x509.ParseCertificate(pair.Certificate[0])
	if err != nil {
		return nil, nil, err
	}
	intermediates = x509.NewCertPool()
	for _, der := range pair.Certificate[1:] {
		cert, err := x509.ParseCertificate(der)
		if err != nil {
			return nil, nil, err
		}
		intermediates.AddCert(cert)
	}
	return leaf, intermediates, nil
}
