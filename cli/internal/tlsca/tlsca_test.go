package tlsca_test

import (
	"crypto/ecdsa"
	"crypto/x509"
	"encoding/pem"
	"strings"
	"testing"
	"time"

	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	pkcs12 "software.sslmate.com/src/go-pkcs12"
)

type authority struct {
	cert   *x509.Certificate
	key    *ecdsa.PrivateKey
	caPEM  []byte
	keyPEM []byte
}

func newAuthority(t *testing.T, days int) authority {
	t.Helper()
	cert, key, err := tlsca.NewCA("LVH Yard CA - ws-a", days)
	if err != nil {
		t.Fatal(err)
	}
	caPEM, keyPEM, err := tlsca.Sign(cert, cert, key, key)
	if err != nil {
		t.Fatal(err)
	}
	parsed, signer, err := tlsca.ParseCA(caPEM, keyPEM, "")
	if err != nil {
		t.Fatal(err)
	}
	return authority{parsed, signer, caPEM, keyPEM}
}

// issue signs a leaf that fill has named, and gives its PEM files.
func (a authority) issue(t *testing.T, name string, fill func(*x509.Certificate)) (certPEM, keyPEM []byte) {
	t.Helper()
	leaf, key, err := tlsca.NewCertificate(name, 30)
	if err != nil {
		t.Fatal(err)
	}
	fill(leaf)
	certPEM, keyPEM, err = tlsca.Sign(leaf, a.cert, key, a.key)
	if err != nil {
		t.Fatal(err)
	}
	return certPEM, keyPEM
}

func TestNewCertificateTakesFrom1To100000Days(t *testing.T) {
	for _, days := range []int{-1, 0, 100001} {
		if _, _, err := tlsca.NewCertificate("x", days); err == nil {
			t.Errorf("%d days were accepted", days)
		}
	}
	for _, days := range []int{1, 100000} {
		leaf, _, err := tlsca.NewCertificate("x", days)
		if err != nil {
			t.Fatalf("%d days: %v", days, err)
		}
		if got := leaf.NotAfter.Sub(leaf.NotBefore); got != time.Duration(days)*24*time.Hour {
			t.Errorf("%d days: valid for %v", days, got)
		}
		if leaf.Subject.CommonName != "x" || leaf.SerialNumber.Sign() <= 0 {
			t.Errorf("%d days: %v", days, leaf.Subject)
		}
	}
}

func TestTheCAIsACertificateAuthorityThatSignsCertificates(t *testing.T) {
	ca := newAuthority(t, 3650)
	if !ca.cert.IsCA || ca.cert.KeyUsage&x509.KeyUsageCertSign == 0 || ca.cert.Subject.CommonName != "LVH Yard CA - ws-a" {
		t.Fatalf("CA = %+v", ca.cert)
	}
}

func TestParseCARefusesWhatIsNotACAWithItsKey(t *testing.T) {
	ca := newAuthority(t, 365)
	other := newAuthority(t, 365)
	leafPEM, leafKeyPEM := ca.issue(t, "leaf", func(*x509.Certificate) {})
	for name, tc := range map[string]struct {
		cert, key []byte
		want      string
	}{
		"no certificate": {[]byte("x"), ca.keyPEM, "invalid CA certificate"},
		"no key":         {ca.caPEM, []byte("x"), "invalid CA key"},
		"another key":    {ca.caPEM, other.keyPEM, "do not match"},
		"not a CA":       {leafPEM, leafKeyPEM, "do not match"},
	} {
		if _, _, err := tlsca.ParseCA(tc.cert, tc.key, ""); err == nil || !strings.Contains(err.Error(), tc.want) {
			t.Errorf("%s: error = %v; want %q", name, err, tc.want)
		}
	}
}

func TestConsulCertificateNamesTheServerAndAllowsBothUsages(t *testing.T) {
	leaf, _, err := tlsca.NewCertificate("ws-a", 30)
	if err != nil {
		t.Fatal(err)
	}
	tlsca.FillConsul(leaf, "ws-a")
	if got := strings.Join(tlsca.Names(leaf), ","); got != "DNS:server.ws-a.consul,DNS:localhost,IP:127.0.0.1" {
		t.Errorf("names = %s", got)
	}
	if len(leaf.ExtKeyUsage) != 2 || leaf.KeyUsage != x509.KeyUsageDigitalSignature {
		t.Errorf("usages = %v %v", leaf.KeyUsage, leaf.ExtKeyUsage)
	}
}

func TestTraefikCertificateNamesTheRoutesAndTakesMoreAddresses(t *testing.T) {
	leaf, _, err := tlsca.NewCertificate("ws-a", 30)
	if err != nil {
		t.Fatal(err)
	}
	tlsca.FillTraefik(leaf, "lvh.me", []string{"10.0.0.5", "*.ws-b.lvh.me", "app.example.com", "::1"})
	want := "DNS:*.lvh.me,DNS:localhost,DNS:*.ws-b.lvh.me,DNS:app.example.com,IP:127.0.0.1,IP:10.0.0.5,IP:::1"
	if got := strings.Join(tlsca.Names(leaf), ","); got != want {
		t.Errorf("names = %s; want %s", got, want)
	}
	if len(leaf.ExtKeyUsage) != 1 || leaf.ExtKeyUsage[0] != x509.ExtKeyUsageServerAuth {
		t.Errorf("usage = %v", leaf.ExtKeyUsage)
	}
}

func TestCheckWithinRefusesALeafThatOutlivesItsCA(t *testing.T) {
	ca, _, err := tlsca.NewCertificate("ca", 10)
	if err != nil {
		t.Fatal(err)
	}
	for days, ok := range map[int]bool{10: true, 5: true, 11: false} {
		leaf, _, err := tlsca.NewCertificate("leaf", days)
		if err != nil {
			t.Fatal(err)
		}
		leaf.NotBefore = ca.NotBefore
		leaf.NotAfter = ca.NotBefore.Add(time.Duration(days) * 24 * time.Hour)
		if got := tlsca.CheckWithin(leaf, ca) == nil; got != ok {
			t.Errorf("%d days: accepted = %v", days, got)
		}
	}
	early, _, _ := tlsca.NewCertificate("leaf", 1)
	early.NotBefore = ca.NotBefore.Add(-time.Hour)
	early.NotAfter = ca.NotAfter
	if tlsca.CheckWithin(early, ca) == nil {
		t.Error("a leaf valid before its CA was accepted")
	}
}

func TestCheckConsul(t *testing.T) {
	ca := newAuthority(t, 365)
	fill := func(workstation string) func(*x509.Certificate) {
		return func(leaf *x509.Certificate) { tlsca.FillConsul(leaf, workstation) }
	}
	certPEM, keyPEM := ca.issue(t, "ws-a", fill("ws-a"))
	if err := tlsca.CheckConsul(ca.caPEM, certPEM, keyPEM, "ws-a"); err != nil {
		t.Fatalf("a good certificate: %v", err)
	}
	if err := tlsca.CheckConsul(ca.caPEM, certPEM, keyPEM, "ws-b"); err == nil || !strings.Contains(err.Error(), "needs server.ws-b.consul") {
		t.Errorf("another name: %v", err)
	}
	serverOnly, serverKey := ca.issue(t, "ws-a", func(leaf *x509.Certificate) {
		tlsca.FillConsul(leaf, "ws-a")
		leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}
	})
	if err := tlsca.CheckConsul(ca.caPEM, serverOnly, serverKey, "ws-a"); err == nil || !strings.Contains(err.Error(), "RPC usage") {
		t.Errorf("a server-only certificate: %v", err)
	}
	other := newAuthority(t, 365)
	if err := tlsca.CheckConsul(other.caPEM, certPEM, keyPEM, "ws-a"); err == nil {
		t.Error("a certificate of another CA was accepted")
	}
	if err := tlsca.CheckConsul(ca.caPEM, certPEM, []byte("x"), "ws-a"); err == nil || !strings.Contains(err.Error(), "do not match or are invalid") {
		t.Errorf("a bad key: %v", err)
	}
	if err := tlsca.CheckConsul([]byte("x"), certPEM, keyPEM, "ws-a"); err == nil || !strings.Contains(err.Error(), "invalid CA certificate") {
		t.Errorf("a bad CA: %v", err)
	}
}

func TestCheckTraefik(t *testing.T) {
	ca := newAuthority(t, 365)
	certPEM, keyPEM := ca.issue(t, "ws-a", func(leaf *x509.Certificate) { tlsca.FillTraefik(leaf, "lvh.me", nil) })
	if err := tlsca.CheckTraefik(ca.caPEM, certPEM, keyPEM, "yard.lvh.me"); err != nil {
		t.Fatalf("a good certificate: %v", err)
	}
	if err := tlsca.CheckTraefik(ca.caPEM, certPEM, keyPEM, "yard.other.me"); err == nil || !strings.Contains(err.Error(), "needs yard.other.me") {
		t.Errorf("another Base Domain: %v", err)
	}
	other := newAuthority(t, 365)
	if err := tlsca.CheckTraefik(other.caPEM, certPEM, keyPEM, "yard.lvh.me"); err == nil || !strings.Contains(err.Error(), "neither for the given CA nor for a public CA") {
		t.Errorf("a certificate of another CA: %v", err)
	}
}

func TestClientBundleHoldsAClientCertificateSignedByTheCA(t *testing.T) {
	ca := newAuthority(t, 365)
	data, err := tlsca.ClientBundle("alice@example.com", 30, ca.cert, ca.key, "secret")
	if err != nil {
		t.Fatal(err)
	}
	_, cert, chain, err := pkcs12.DecodeChain(data, "secret")
	if err != nil {
		t.Fatal(err)
	}
	if cert.Subject.CommonName != "alice@example.com" || len(cert.ExtKeyUsage) != 1 || cert.ExtKeyUsage[0] != x509.ExtKeyUsageClientAuth {
		t.Errorf("certificate = %v %v", cert.Subject, cert.ExtKeyUsage)
	}
	if len(chain) != 1 || !chain[0].Equal(ca.cert) {
		t.Errorf("chain = %v", chain)
	}
	roots := x509.NewCertPool()
	roots.AddCert(ca.cert)
	if _, err := cert.Verify(x509.VerifyOptions{Roots: roots, KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth}}); err != nil {
		t.Errorf("the CA does not vouch for it: %v", err)
	}
	if _, _, _, err := pkcs12.DecodeChain(data, "wrong"); err == nil {
		t.Error("the wrong password opened the file")
	}
}

func TestClientBundleRefusesAValidityBeyondTheCA(t *testing.T) {
	ca := newAuthority(t, 10)
	if _, err := tlsca.ClientBundle("alice", 11, ca.cert, ca.key, "x"); err == nil || !strings.Contains(err.Error(), "outside the CA validity") {
		t.Fatalf("error = %v", err)
	}
}

func TestRandomPasswordsDiffer(t *testing.T) {
	a, err := tlsca.RandomPassword()
	if err != nil {
		t.Fatal(err)
	}
	b, _ := tlsca.RandomPassword()
	if len(a) != 32 || a == b {
		t.Fatalf("passwords %q and %q", a, b)
	}
}

// expired is a leaf that the CA signed, whose validity ended a day ago.
func (a authority) expired(t *testing.T, fill func(*x509.Certificate)) (certPEM, keyPEM []byte) {
	t.Helper()
	return a.issue(t, "ws-a", func(leaf *x509.Certificate) {
		fill(leaf)
		leaf.NotBefore = time.Now().Add(-48 * time.Hour)
		leaf.NotAfter = time.Now().Add(-24 * time.Hour)
	})
}

func TestFillServiceNamesTheServiceAndTheGivenAddresses(t *testing.T) {
	ca := newAuthority(t, 365)
	certPEM, _ := ca.issue(t, "grafana", func(leaf *x509.Certificate) {
		tlsca.FillService(leaf, "grafana", []string{"192.168.1.5", "grafana.internal"})
	})
	leaf := parseCertificate(t, certPEM)
	names := strings.Join(tlsca.Names(leaf), " ")
	for _, want := range []string{"DNS:grafana", "DNS:localhost", "DNS:grafana.internal", "IP:127.0.0.1", "IP:192.168.1.5"} {
		if !strings.Contains(names, want) {
			t.Errorf("names %q lack %s", names, want)
		}
	}
	if len(leaf.ExtKeyUsage) != 2 {
		t.Errorf("a Service certificate serves and calls, got usages %v", leaf.ExtKeyUsage)
	}
}

func TestFillClientAllowsOnlyClientUse(t *testing.T) {
	ca := newAuthority(t, 365)
	certPEM, _ := ca.issue(t, "traefik", tlsca.FillClient)
	leaf := parseCertificate(t, certPEM)
	if len(leaf.ExtKeyUsage) != 1 || leaf.ExtKeyUsage[0] != x509.ExtKeyUsageClientAuth {
		t.Errorf("usages %v", leaf.ExtKeyUsage)
	}
	if len(tlsca.Names(leaf)) != 0 {
		t.Errorf("a client certificate has no names, got %v", tlsca.Names(leaf))
	}
}

func TestCheckHTTPS(t *testing.T) {
	ca := newAuthority(t, 365)
	certPEM, keyPEM := ca.issue(t, "ws-a", func(leaf *x509.Certificate) { tlsca.FillTraefik(leaf, "lvh.me", nil) })
	if err := tlsca.CheckHTTPS(certPEM, keyPEM, "yard.lvh.me"); err != nil {
		t.Fatalf("a good certificate: %v", err)
	}
	if err := tlsca.CheckHTTPS(certPEM, keyPEM, "yard.other.me"); err == nil || !strings.Contains(err.Error(), "needs yard.other.me") {
		t.Errorf("another Base Domain: %v", err)
	}
	_, otherKey := ca.issue(t, "ws-b", func(leaf *x509.Certificate) { tlsca.FillTraefik(leaf, "lvh.me", nil) })
	if err := tlsca.CheckHTTPS(certPEM, otherKey, "yard.lvh.me"); err == nil || !strings.Contains(err.Error(), "do not match or are invalid") {
		t.Errorf("the key of another certificate: %v", err)
	}
	oldPEM, oldKey := ca.expired(t, func(leaf *x509.Certificate) { tlsca.FillTraefik(leaf, "lvh.me", nil) })
	if err := tlsca.CheckHTTPS(oldPEM, oldKey, "yard.lvh.me"); err == nil || !strings.Contains(err.Error(), "not valid now") {
		t.Errorf("an expired certificate: %v", err)
	}
}

func TestCheckTraefikWithoutACAAcceptsOnlyAPublicCA(t *testing.T) {
	ca := newAuthority(t, 365)
	certPEM, keyPEM := ca.issue(t, "ws-a", func(leaf *x509.Certificate) { tlsca.FillTraefik(leaf, "lvh.me", nil) })
	if err := tlsca.CheckTraefik(nil, certPEM, keyPEM, "yard.lvh.me"); err == nil || !strings.Contains(err.Error(), "neither for the given CA nor for a public CA") {
		t.Errorf("a private CA with no CA file: %v", err)
	}
	if err := tlsca.CheckTraefik([]byte("x"), certPEM, keyPEM, "yard.lvh.me"); err == nil || !strings.Contains(err.Error(), "invalid CA certificate") {
		t.Errorf("a CA file with no certificate: %v", err)
	}
}

func TestCheckCA(t *testing.T) {
	ca := newAuthority(t, 365)
	if err := tlsca.CheckCA(ca.caPEM); err != nil {
		t.Fatalf("a good CA: %v", err)
	}
	leafPEM, _ := ca.issue(t, "ws-a", tlsca.FillClient)
	if err := tlsca.CheckCA(leafPEM); err == nil || !strings.Contains(err.Error(), "is not a CA certificate") {
		t.Errorf("a leaf: %v", err)
	}
	if err := tlsca.CheckCA([]byte("not pem")); err == nil || !strings.Contains(err.Error(), "invalid CA certificate") {
		t.Errorf("no certificate: %v", err)
	}

	expiredCA := expiredAuthority(t)
	if err := tlsca.CheckCA(expiredCA.caPEM); err == nil || !strings.Contains(err.Error(), "expired or is not valid yet") {
		t.Errorf("an expired CA: %v", err)
	}
	both := append(append([]byte{}, expiredCA.caPEM...), ca.caPEM...)
	if err := tlsca.CheckCA(both); err != nil {
		t.Errorf("a file with one valid CA among others must pass: %v", err)
	}
}

func TestCheckMutualTLS(t *testing.T) {
	ca := newAuthority(t, 365)
	certPEM, keyPEM := ca.issue(t, "traefik", tlsca.FillClient)
	if err := tlsca.CheckMutualTLS(certPEM, keyPEM); err != nil {
		t.Fatalf("a client certificate: %v", err)
	}
	bothPEM, bothKey := ca.issue(t, "grafana", func(leaf *x509.Certificate) { tlsca.FillService(leaf, "grafana", nil) })
	if err := tlsca.CheckMutualTLS(bothPEM, bothKey); err != nil {
		t.Errorf("a Service certificate also allows client use: %v", err)
	}
	serverOnly, serverKey := ca.issue(t, "ws-a", func(leaf *x509.Certificate) {
		tlsca.FillTraefik(leaf, "lvh.me", nil)
	})
	if err := tlsca.CheckMutualTLS(serverOnly, serverKey); err == nil || !strings.Contains(err.Error(), "does not allow client use") {
		t.Errorf("a server-only certificate: %v", err)
	}
	if err := tlsca.CheckMutualTLS(certPEM, serverKey); err == nil || !strings.Contains(err.Error(), "do not match or are invalid") {
		t.Errorf("the key of another certificate: %v", err)
	}
	oldPEM, oldKey := ca.expired(t, tlsca.FillClient)
	if err := tlsca.CheckMutualTLS(oldPEM, oldKey); err == nil || !strings.Contains(err.Error(), "not valid now") {
		t.Errorf("an expired certificate: %v", err)
	}
}

func TestCheckConsulRefusesAnExpiredCertificate(t *testing.T) {
	ca := newAuthority(t, 365)
	oldPEM, oldKey := ca.expired(t, func(leaf *x509.Certificate) { tlsca.FillConsul(leaf, "ws-a") })
	if err := tlsca.CheckConsul(ca.caPEM, oldPEM, oldKey, "ws-a"); err == nil || !strings.Contains(err.Error(), "RPC usage") {
		t.Errorf("an expired certificate: %v", err)
	}
}

func TestACertificateKeepsTheChainThatItWasGivenWith(t *testing.T) {
	// A leaf file that holds the leaf and an intermediate CA is valid for the root, so Consul accepts it.
	root := newAuthority(t, 365)
	intermediateCert, intermediateKey, err := tlsca.NewCA("intermediate", 300)
	if err != nil {
		t.Fatal(err)
	}
	intermediatePEM, _, err := tlsca.Sign(intermediateCert, root.cert, intermediateKey, root.key)
	if err != nil {
		t.Fatal(err)
	}
	intermediate := authority{cert: parseCertificate(t, intermediatePEM), key: intermediateKey}
	leafPEM, leafKey := intermediate.issue(t, "ws-a", func(leaf *x509.Certificate) { tlsca.FillConsul(leaf, "ws-a") })
	chain := append(append([]byte{}, leafPEM...), intermediatePEM...)
	if err := tlsca.CheckConsul(root.caPEM, chain, leafKey, "ws-a"); err != nil {
		t.Errorf("a leaf with its intermediate: %v", err)
	}
	if err := tlsca.CheckConsul(root.caPEM, leafPEM, leafKey, "ws-a"); err == nil {
		t.Error("a leaf without its intermediate was accepted")
	}
}

func expiredAuthority(t *testing.T) authority {
	t.Helper()
	cert, key, err := tlsca.NewCA("old CA", 30)
	if err != nil {
		t.Fatal(err)
	}
	cert.NotBefore = time.Now().Add(-48 * time.Hour)
	cert.NotAfter = time.Now().Add(-24 * time.Hour)
	caPEM, keyPEM, err := tlsca.Sign(cert, cert, key, key)
	if err != nil {
		t.Fatal(err)
	}
	return authority{cert: cert, key: key, caPEM: caPEM, keyPEM: keyPEM}
}

func parseCertificate(t *testing.T, certPEM []byte) *x509.Certificate {
	t.Helper()
	block, _ := pem.Decode(certPEM)
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		t.Fatal(err)
	}
	return cert
}

// selfSigned signs a template with its own key, so a test chooses the CA flag and the usages.
func selfSigned(t *testing.T, change func(*x509.Certificate)) (certPEM, keyPEM []byte) {
	t.Helper()
	cert, key, err := tlsca.NewCertificate("custom", 30)
	if err != nil {
		t.Fatal(err)
	}
	change(cert)
	certPEM, keyPEM, err = tlsca.Sign(cert, cert, key, key)
	if err != nil {
		t.Fatal(err)
	}
	return certPEM, keyPEM
}

func TestParseCANeedsTheCAFlagAndTheSigningUsageTogether(t *testing.T) {
	flagOnly, flagOnlyKey := selfSigned(t, func(c *x509.Certificate) {
		c.IsCA, c.BasicConstraintsValid = true, true
		c.KeyUsage = x509.KeyUsageDigitalSignature
	})
	usageOnly, usageOnlyKey := selfSigned(t, func(c *x509.Certificate) {
		c.KeyUsage = x509.KeyUsageCertSign
	})
	both, bothKey := selfSigned(t, func(c *x509.Certificate) {
		c.IsCA, c.BasicConstraintsValid = true, true
		c.KeyUsage = x509.KeyUsageCertSign
	})
	if _, _, err := tlsca.ParseCA(flagOnly, flagOnlyKey, ""); err == nil {
		t.Error("a CA that cannot sign certificates was accepted")
	}
	if _, _, err := tlsca.ParseCA(usageOnly, usageOnlyKey, ""); err == nil {
		t.Error("a certificate that is not a CA was accepted")
	}
	if _, _, err := tlsca.ParseCA(both, bothKey, ""); err != nil {
		t.Errorf("a CA that signs: %v", err)
	}
}

func TestEveryKindOfLeafAllowsOnlyDigitalSignatures(t *testing.T) {
	ca := newAuthority(t, 365)
	for name, fill := range map[string]func(*x509.Certificate){
		"consul":  func(c *x509.Certificate) { tlsca.FillConsul(c, "ws-a") },
		"traefik": func(c *x509.Certificate) { tlsca.FillTraefik(c, "lvh.me", nil) },
		"service": func(c *x509.Certificate) { tlsca.FillService(c, "grafana", nil) },
		"client":  tlsca.FillClient,
	} {
		certPEM, _ := ca.issue(t, name, fill)
		if usage := parseCertificate(t, certPEM).KeyUsage; usage != x509.KeyUsageDigitalSignature {
			t.Errorf("%s: key usage %v", name, usage)
		}
	}
}

func TestACertificateThatIsNotValidYetIsRefusedEverywhere(t *testing.T) {
	ca := newAuthority(t, 365)
	future := func(leaf *x509.Certificate) {
		leaf.NotBefore = time.Now().Add(24 * time.Hour)
		leaf.NotAfter = time.Now().Add(48 * time.Hour)
	}
	httpsPEM, httpsKey := ca.issue(t, "ws-a", func(leaf *x509.Certificate) {
		tlsca.FillTraefik(leaf, "lvh.me", nil)
		future(leaf)
	})
	if err := tlsca.CheckHTTPS(httpsPEM, httpsKey, "yard.lvh.me"); err == nil || !strings.Contains(err.Error(), "not valid now") {
		t.Errorf("HTTPS: %v", err)
	}
	clientPEM, clientKey := ca.issue(t, "traefik", func(leaf *x509.Certificate) {
		tlsca.FillClient(leaf)
		future(leaf)
	})
	if err := tlsca.CheckMutualTLS(clientPEM, clientKey); err == nil || !strings.Contains(err.Error(), "not valid now") {
		t.Errorf("mutual TLS: %v", err)
	}
	futureCA, _ := selfSigned(t, func(c *x509.Certificate) {
		c.IsCA, c.BasicConstraintsValid = true, true
		c.KeyUsage = x509.KeyUsageCertSign
		future(c)
	})
	if err := tlsca.CheckCA(futureCA); err == nil || !strings.Contains(err.Error(), "expired or is not valid yet") {
		t.Errorf("CA: %v", err)
	}
}

func TestCheckCAIgnoresBlocksThatAreNotCertificates(t *testing.T) {
	ca := newAuthority(t, 365)
	withKeyFirst := append(append([]byte{}, ca.keyPEM...), ca.caPEM...)
	if err := tlsca.CheckCA(withKeyFirst); err != nil {
		t.Errorf("a file with a key block before the CA: %v", err)
	}
}

func TestMutualTLSAcceptsACertificateThatAllowsAnyUse(t *testing.T) {
	ca := newAuthority(t, 365)
	certPEM, keyPEM := ca.issue(t, "any", func(leaf *x509.Certificate) {
		leaf.KeyUsage = x509.KeyUsageDigitalSignature
		leaf.ExtKeyUsage = []x509.ExtKeyUsage{x509.ExtKeyUsageAny}
	})
	if err := tlsca.CheckMutualTLS(certPEM, keyPEM); err != nil {
		t.Errorf("a certificate for any use: %v", err)
	}
	noUsage, noUsageKey := ca.issue(t, "none", func(leaf *x509.Certificate) { leaf.KeyUsage = x509.KeyUsageDigitalSignature })
	if err := tlsca.CheckMutualTLS(noUsage, noUsageKey); err != nil {
		t.Errorf("a certificate with no extended usage: %v", err)
	}
}
