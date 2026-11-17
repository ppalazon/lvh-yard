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
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); r.ExitCode != 0 {
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

func TestWSTLSTraefikCertificate(t *testing.T) {
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
	load := func(name, role string) *x509.Certificate {
		dir := filepath.Join(folder, "ca", "certs", name)
		pair, err := tls.LoadX509KeyPair(filepath.Join(dir, role+".crt"), filepath.Join(dir, role+".key"))
		if err != nil {
			t.Fatal(err)
		}
		if key, ok := pair.PrivateKey.(*ecdsa.PrivateKey); !ok || key.Curve != elliptic.P256() {
			t.Fatalf("%s must use P-256", role)
		}
		return readTLSCertificate(t, filepath.Join(dir, role+".crt"))
	}
	verifies := func(cert *x509.Certificate, name string, usage x509.ExtKeyUsage) bool {
		_, err := cert.Verify(x509.VerifyOptions{Roots: roots, DNSName: name, KeyUsages: []x509.ExtKeyUsage{usage}})
		return err == nil
	}
	if r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder, "--address", "192.168.1.22", "--address", "extra.example.test", "--days", "42"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	traefik := load("ws-a", "traefik")
	for _, name := range []string{"web.example.test", "localhost", "127.0.0.1", "192.168.1.22", "extra.example.test"} {
		if !verifies(traefik, name, x509.ExtKeyUsageServerAuth) {
			t.Fatalf("the Traefik certificate does not verify for %s", name)
		}
	}
	if len(traefik.ExtKeyUsage) != 1 || traefik.ExtKeyUsage[0] != x509.ExtKeyUsageServerAuth || traefik.KeyUsage != x509.KeyUsageDigitalSignature {
		t.Fatal("the Traefik certificate allows server use only")
	}
	if traefik.NotAfter.Sub(traefik.NotBefore) != 42*24*time.Hour {
		t.Fatal("wrong leaf validity")
	}
	for _, path := range []string{"tls/traefik/traefik.crt", "traefik/dynamic/10-tls.yaml"} {
		if _, err := os.Stat(filepath.Join(folder, path)); !os.IsNotExist(err) {
			t.Fatalf("signing unexpectedly installed or enabled TLS: %s", path)
		}
	}
	if strings.Contains(readInitFile(t, folder, ".env"), "YARD_HTTPS_PORT=443") {
		t.Fatal("signing enabled HTTPS")
	}
	assertPrivateWorkstation(t, folder)
}

func TestWSTLSIssueIsForThisWorkstationOnly(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	// Traefik takes no Workstation name and no other Base Domain. Consul has no command here: ws yard
	// share signs its certificates.
	for _, args := range [][]string{
		{"issue", "traefik", "ws-b"}, {"issue", "traefik", "--base-domain", "other.test"},
	} {
		if r := yard.Run(append(append([]string{"ws", "tls", "ca"}, args...), "--dir", folder)...); r.ExitCode == 0 {
			t.Fatalf("accepted %v", args)
		}
	}
	// Nothing signs or packs a whole Workstation at once, and neither a Traefik nor a Consul
	// certificate is packed to send.
	for _, group := range []string{"issue", "export"} {
		help := yard.Run("ws", "tls", "ca", group, "--help").Stdout
		for _, gone := range []string{"workstation ", "workstation\n", "consul"} {
			if strings.Contains(help, gone) {
				t.Fatalf("ca %s still has a command for %q:\n%s", group, gone, help)
			}
		}
		if group == "export" && strings.Contains(help, "traefik") {
			t.Fatalf("ca export can pack a Traefik certificate:\n%s", help)
		}
	}
}

func TestWSTLSIssueAsksForWhatIsNotGiven(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "example.test"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	yard.Process.Terminal = true

	// The names to add, and the validity.
	yard.Process.Stdin = "192.168.1.22 *.ws-x.example.test\n42\n"
	r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder)
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	traefik := readTLSCertificate(t, filepath.Join(folder, "ca", "certs", "ws-a", "traefik.crt"))
	for _, name := range []string{"app.example.test", "192.168.1.22", "app.ws-x.example.test"} {
		if err := traefik.VerifyHostname(name); err != nil {
			t.Fatalf("the Traefik certificate lacks %s: %v", name, err)
		}
	}
	if traefik.NotAfter.Sub(traefik.NotBefore) != 42*24*time.Hour {
		t.Fatal("the answer for the validity was ignored")
	}
	if !strings.Contains(r.Stdout, "tls install") {
		t.Fatalf("the next step is missing:\n%s", r.Stdout)
	}
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
		args := []string{"ws", "tls", "ca", "issue", "traefik", "--dir", folder}
		if explicit {
			args = append(args, "--address", "*.ws-b.lvh.me")
		}
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
		cert := readTLSCertificate(t, filepath.Join(folder, "ca", "certs", "ws-a", "traefik.crt"))
		for _, tc := range []struct {
			hostname string
			matches  bool
		}{
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
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "no readable CA") {
		t.Fatalf("missing CA: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, flags := range [][]string{
		{"traefik", "--days", "0"}, {"traefik", "--days", "-1"}, {"traefik", "--days", "100001"}, {"traefik", "--days", "4000"},
		{"traefik", "--address", "not an address"}, {"user", "../escape"},
	} {
		if r := yard.Run(append([]string{"ws", "tls", "ca", "issue", "--dir", folder}, flags...)...); r.ExitCode == 0 {
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
			r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder, "--address", address)
			if r.ExitCode == 0 || !strings.Contains(r.Stderr, "invalid --address") {
				t.Fatalf("malformed wildcard not refused: %+v", r)
			}
		})
	}
	if err := os.Remove(filepath.Join(folder, "ca", "ca.key")); err != nil {
		t.Fatal(err)
	}
	if r := yard.Run("ws", "tls", "ca", "issue", "traefik", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "CA key") {
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

func TestWSTLSCAWarnsThatItIsOnlyForTesting(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
		{"ws", "tls", "ca", "issue", "traefik", "--dir", folder},
		{"ws", "tls", "ca", "issue", "user", "alice", "--dir", folder},
		{"ws", "tls", "install", "--dir", folder},
		{"ws", "tls", "ca", "export", "ca", "--dir", folder},
	} {
		r := yard.Run(args...)
		if r.ExitCode != 0 {
			t.Fatalf("%v: %+v", args, r)
		}
		warned := strings.Contains(r.Stderr, "for testing") && strings.Contains(r.Stderr, "ws tls install")
		if (len(args) > 2 && args[1] == "tls" && args[2] == "ca") != warned {
			t.Fatalf("%v: warning on stderr is %t:\n%s", args, warned, r.Stderr)
		}
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode == 0 || strings.Contains(r.Stderr, "for testing") {
		t.Fatalf("a refused command printed the warning: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "--help"); !strings.Contains(r.Stdout, "for testing") {
		t.Fatalf("the help of ca lacks the warning:\n%s", r.Stdout)
	}
	if r := yard.Run("ws", "tls", "install", "--help"); strings.Contains(r.Stdout+r.Stderr, "for testing") {
		t.Fatal("install takes the files of a CA that you bring, so it has no testing warning")
	}
}
