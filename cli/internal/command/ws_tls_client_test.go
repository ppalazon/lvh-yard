package command_test

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/x509"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	pkcs12 "software.sslmate.com/src/go-pkcs12"
)

func TestWSTLSClientCertificate(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, explicit := range []bool{true, false} {
		name, password := "alice", "browser-secret"
		args := []string{"ws", "tls", "ca", "issue", "user", name, "--dir", folder}
		if explicit {
			args = append(args, "--password", password)
		} else {
			name = "bob"
			args[5] = name
		}
		r := yard.Run(args...)
		if r.ExitCode != 0 {
			t.Fatal(r)
		}
		if explicit {
			if strings.Contains(r.Stdout+r.Stderr, password) {
				t.Fatal("explicit password was printed")
			}
		} else {
			for line := range strings.SplitSeq(r.Stdout, "\n") {
				if after, ok := strings.CutPrefix(line, "Password: "); ok {
					password = after
				}
			}
			if password == "browser-secret" || len(password) < 24 || strings.Count(r.Stdout+r.Stderr, password) != 1 {
				t.Fatalf("random password not printed once: %+v", r)
			}
		}
		data, err := os.ReadFile(filepath.Join(folder, "ca", "clients", name+".p12"))
		if err != nil {
			t.Fatal(err)
		}
		key, cert, chain, err := pkcs12.DecodeChain(data, password)
		if err != nil {
			t.Fatal(err)
		}
		if _, _, _, err := pkcs12.DecodeChain(data, "wrong-password"); err == nil {
			t.Fatal("wrong password decoded the bundle")
		}
		ca := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt"))
		if len(chain) != 1 || !chain[0].Equal(ca) {
			t.Fatal("bundle does not contain the Yard CA")
		}
		roots := x509.NewCertPool()
		roots.AddCert(ca)
		if _, err := cert.Verify(x509.VerifyOptions{Roots: roots, KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth}}); err != nil {
			t.Fatal(err)
		}
		if _, err := cert.Verify(x509.VerifyOptions{Roots: roots, KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}); err == nil {
			t.Fatal("client certificate allows server authentication")
		}
		if cert.Subject.CommonName != name || cert.KeyUsage != x509.KeyUsageDigitalSignature || len(cert.ExtKeyUsage) != 1 || cert.ExtKeyUsage[0] != x509.ExtKeyUsageClientAuth || cert.IsCA {
			t.Fatal("wrong client identity or key usage")
		}
		private, ok := key.(*ecdsa.PrivateKey)
		if !ok || private.Curve != elliptic.P256() || !private.PublicKey.Equal(cert.PublicKey) {
			t.Fatal("wrong client key")
		}
		if cert.NotAfter.Sub(cert.NotBefore) != 365*24*time.Hour {
			t.Fatal("wrong client validity")
		}
	}
	assertPrivateWorkstation(t, folder)
}

func TestWSTLSClientRefusals(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, args := range [][]string{{"../escape"}, {"alice", "--password", ""}} {
		r := yard.Run(append([]string{"ws", "tls", "ca", "issue", "user", "--dir", folder}, args...)...)
		if r.ExitCode == 0 {
			t.Fatalf("accepted invalid input: %+v", args)
		}
	}
	if err := os.Remove(filepath.Join(folder, "ca", "ca.key")); err != nil {
		t.Fatal(err)
	}
	r := yard.Run("ws", "tls", "ca", "issue", "user", "alice", "--dir", folder)
	if r.ExitCode == 0 || !strings.Contains(r.Stderr, "Yard CA key") || !strings.Contains(r.Stderr, "Workstation that made the CA") {
		t.Fatalf("missing signing key not explained: %+v", r)
	}
}

func TestWSTLSIssueUserAsksForUsernameOrEmail(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	yard.Process.Terminal = true
	// An empty name is asked again. Empty answers then take a random password and 365 days.
	yard.Process.Stdin = "\nbob@example.com\n\n\n"
	r := yard.Run("ws", "tls", "ca", "issue", "user", "--dir", folder)
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	if !strings.Contains(r.Stdout, "Username or email") || !strings.Contains(r.Stdout, "needs a username or an email") || !strings.Contains(r.Stdout, "Password: ") {
		t.Fatalf("unexpected questions or output:\n%s", r.Stdout)
	}
	if _, err := os.Stat(filepath.Join(folder, "ca", "clients", "bob@example.com.p12")); err != nil {
		t.Fatal(err)
	}
}
