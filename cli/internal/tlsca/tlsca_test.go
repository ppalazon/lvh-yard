package tlsca_test

import (
	"crypto/ecdsa"
	"crypto/x509"
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
	cert, key, err := tlsca.NewCA(days)
	if err != nil {
		t.Fatal(err)
	}
	caPEM, keyPEM, err := tlsca.Sign(cert, cert, key, key)
	if err != nil {
		t.Fatal(err)
	}
	parsed, signer, err := tlsca.ParseCA(caPEM, keyPEM)
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
	if !ca.cert.IsCA || ca.cert.KeyUsage&x509.KeyUsageCertSign == 0 || ca.cert.Subject.CommonName != "LVH Yard CA" {
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
		"no certificate": {[]byte("x"), ca.keyPEM, "invalid Yard CA certificate"},
		"no key":         {ca.caPEM, []byte("x"), "invalid Yard CA key"},
		"another key":    {ca.caPEM, other.keyPEM, "do not match"},
		"not a CA":       {leafPEM, leafKeyPEM, "do not match"},
	} {
		if _, _, err := tlsca.ParseCA(tc.cert, tc.key); err == nil || !strings.Contains(err.Error(), tc.want) {
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
	if _, err := tlsca.ClientBundle("alice", 11, ca.cert, ca.key, "x"); err == nil || !strings.Contains(err.Error(), "outside the Yard CA validity") {
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
