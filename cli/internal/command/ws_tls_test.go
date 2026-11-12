package command_test

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"go.yaml.in/yaml/v3"
)

func readTLSCertificate(t *testing.T, path string) *x509.Certificate {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	block, _ := pem.Decode(data)
	if block == nil {
		t.Fatalf("no PEM certificate in %s", path)
	}
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		t.Fatal(err)
	}
	return cert
}

func TestWSTLSCA(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	args := []string{"ws", "tls", "ca", "create", "--dir", folder}
	if r := yard.Run(args...); r.ExitCode != 0 {
		t.Fatal(r)
	}
	cert := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt"))
	if err := cert.CheckSignatureFrom(cert); err != nil {
		t.Fatal(err)
	}
	if !cert.IsCA || !cert.BasicConstraintsValid || cert.KeyUsage&x509.KeyUsageCertSign == 0 {
		t.Fatal("not a signing CA")
	}
	public, ok := cert.PublicKey.(*ecdsa.PublicKey)
	if !ok || public.Curve != elliptic.P256() {
		t.Fatal("CA must use P-256")
	}
	if cert.NotAfter.Sub(cert.NotBefore) != 3650*24*time.Hour {
		t.Fatal("wrong CA validity")
	}
	if r := yard.Run(args...); r.ExitCode == 0 || !strings.Contains(r.Stderr, "--force") {
		t.Fatalf("replacement not refused: %+v", r)
	}
	if r := yard.Run(append(args, "--force", "--days", "40")...); r.ExitCode != 0 {
		t.Fatal(r)
	}
	replacement := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt"))
	if replacement.NotAfter.Sub(replacement.NotBefore) != 40*24*time.Hour || replacement.SerialNumber.Cmp(cert.SerialNumber) == 0 {
		t.Fatal("CA was not replaced")
	}
	if _, err := tls.LoadX509KeyPair(filepath.Join(folder, "ca", "ca.crt"), filepath.Join(folder, "ca", "ca.key")); err != nil {
		t.Fatal(err)
	}
	compose, err := os.ReadFile(filepath.Join(folder, "compose.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	var config struct {
		Services map[string]struct {
			Volumes []any `yaml:"volumes"`
		} `yaml:"services"`
	}
	if err := yaml.Unmarshal(compose, &config); err != nil {
		t.Fatal(err)
	}
	caKey := filepath.Join(folder, "ca", "ca.key")
	for service, settings := range config.Services {
		for _, volume := range settings.Volumes {
			var source string
			switch mount := volume.(type) {
			case string:
				source = strings.Split(mount, ":")[0]
			case map[string]any:
				source, _ = mount["source"].(string)
			default:
				t.Fatalf("unknown mount format: %v", volume)
			}
			if source == "" {
				continue
			}
			if !filepath.IsAbs(source) {
				source = filepath.Join(folder, source)
			}
			rel, err := filepath.Rel(source, caKey)
			if err == nil && rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
				t.Fatalf("%s mounts the CA key through %s", service, source)
			}
		}
	}
	assertPrivateWorkstation(t, folder)
}

func TestWSTLSWorkstationCertificates(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "example.test"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	ca := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt"))
	roots := x509.NewCertPool()
	roots.AddCert(ca)
	for _, tc := range []struct {
		name, dir, domain string
		days              int
		flags             []string
	}{
		{"ws-a", "tls", "example.test", 365, nil},
		{"ws-b", filepath.Join("certificates", "ws-b"), "other.test", 42, []string{"--workstation", "ws-b", "--base-domain", "other.test", "--days", "42"}},
		{"ws-c", filepath.Join("certificates", "ws-c"), "example.test", 365, []string{"--workstation", "ws-c"}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			args := []string{"ws", "tls", "cert", "create", "--dir", folder, "--address", "192.168.1.22", "--address", "extra.example.test"}
			if r := yard.Run(append(args, tc.flags...)...); r.ExitCode != 0 {
				t.Fatal(r)
			}
			dir := filepath.Join(folder, tc.dir)
			cert := readTLSCertificate(t, filepath.Join(dir, "workstation.crt"))
			pair, err := tls.LoadX509KeyPair(filepath.Join(dir, "workstation.crt"), filepath.Join(dir, "workstation.key"))
			if err != nil {
				t.Fatal(err)
			}
			key, ok := pair.PrivateKey.(*ecdsa.PrivateKey)
			if !ok || key.Curve != elliptic.P256() {
				t.Fatal("leaf must use P-256")
			}
			for _, name := range []string{"server." + tc.name + ".consul", "localhost", "127.0.0.1", "192.168.1.22", "extra.example.test", "web." + tc.domain} {
				if _, err := cert.Verify(x509.VerifyOptions{Roots: roots, DNSName: name, KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}); err != nil {
					t.Fatalf("server verification for %s: %v", name, err)
				}
			}
			if _, err := cert.Verify(x509.VerifyOptions{Roots: roots, KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth}}); err != nil {
				t.Fatal(err)
			}
			if cert.KeyUsage != x509.KeyUsageDigitalSignature || len(cert.ExtKeyUsage) != 2 {
				t.Fatal("wrong key usages")
			}
			if cert.NotAfter.Sub(cert.NotBefore) != time.Duration(tc.days)*24*time.Hour {
				t.Fatal("wrong leaf validity")
			}
			copiedCA := readTLSCertificate(t, filepath.Join(dir, "ca.crt"))
			if !copiedCA.Equal(ca) {
				t.Fatal("bundle has a different CA")
			}
			if _, err := os.Stat(filepath.Join(dir, "ca.key")); !os.IsNotExist(err) {
				t.Fatal("CA key leaked into bundle")
			}
		})
	}
	for _, path := range []string{"consul.d/10-tls.hcl", "traefik/dynamic/10-tls.yaml"} {
		if _, err := os.Stat(filepath.Join(folder, path)); !os.IsNotExist(err) {
			t.Fatalf("signing unexpectedly enabled TLS: %s", path)
		}
	}
	env, err := os.ReadFile(filepath.Join(folder, ".env"))
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(env), "YARD_HTTPS_PORT=443") {
		t.Fatal("signing enabled HTTPS")
	}
	assertPrivateWorkstation(t, folder)
}

func TestWSTLSWildcardAddresses(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, explicit := range []bool{false, true} {
		args := []string{"ws", "tls", "cert", "create", "--dir", folder}
		if explicit {
			args = append(args, "--address", "*.ws-b.lvh.me")
		}
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
		cert := readTLSCertificate(t, filepath.Join(folder, "tls", "workstation.crt"))
		for _, tc := range []struct {
			hostname string
			matches  bool
		}{
			{"server.ws-a.consul", true},
			{"localhost", true},
			{"127.0.0.1", true},
			{"app.lvh.me", true},
			{"app.ws-b.lvh.me", explicit},
			{"app.ws-c.lvh.me", false},
			{"deeper.app.ws-b.lvh.me", false},
		} {
			err := cert.VerifyHostname(tc.hostname)
			if (err == nil) != tc.matches {
				t.Fatalf("explicit wildcard %t: VerifyHostname(%q) = %v, want match %t", explicit, tc.hostname, err, tc.matches)
			}
		}
	}
}

func TestWSTLSRefusals(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "cert", "create", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "Yard CA") {
		t.Fatalf("missing CA: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, flags := range [][]string{
		{"--workstation", "../escape"}, {"--base-domain", "bad/domain"}, {"--address", "not an address"},
		{"--days", "0"}, {"--days", "-1"}, {"--days", "100001"}, {"--days", "4000"},
	} {
		if r := yard.Run(append([]string{"ws", "tls", "cert", "create", "--dir", folder}, flags...)...); r.ExitCode == 0 {
			t.Fatalf("accepted %v", flags)
		}
	}
	for _, address := range []string{
		"*", "*.127.0.0.1", "*.::1", "*.[::1]", "*.2001:db8::1",
		"ws-*.lvh.me", "*ws-b.lvh.me", "app.*.lvh.me", "*.*.lvh.me",
		"*.ws-b..lvh.me", "*.ws_b.lvh.me", "*.-ws-b.lvh.me",
		"*.ws-b.lvh.me.", "*." + strings.Repeat("a", 64) + ".lvh.me",
	} {
		t.Run(address, func(t *testing.T) {
			r := yard.Run("ws", "tls", "cert", "create", "--dir", folder, "--address", address)
			if r.ExitCode == 0 || !strings.Contains(r.Stderr, "invalid --address") {
				t.Fatalf("malformed wildcard not refused: %+v", r)
			}
		})
	}
	if err := os.Remove(filepath.Join(folder, "ca", "ca.key")); err != nil {
		t.Fatal(err)
	}
	if r := yard.Run("ws", "tls", "cert", "create", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "Yard CA key") {
		t.Fatalf("missing key: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode == 0 {
		t.Fatal("replaced partial CA")
	}
	for _, days := range []string{"0", "-1", "100001"} {
		if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder, "--force", "--days", days); r.ExitCode == 0 {
			t.Fatalf("accepted CA days %s", days)
		}
	}
}
