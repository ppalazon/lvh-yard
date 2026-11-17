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

	"github.com/ppalazon/lvh-yard/cli/internal/certzip"
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

func TestWSTLSIssueService(t *testing.T) {
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
	zipRun := yard.Run("ws", "tls", "ca", "issue", "service", "grafana", "--dir", folder, "--address", "192.168.1.22", "--address", "app.example.test", "--days", "42")
	if zipRun.ExitCode != 0 {
		t.Fatal(zipRun)
	}
	dir := filepath.Join(folder, "ca", "service", "grafana")
	pair, err := tls.LoadX509KeyPair(filepath.Join(dir, "service.crt"), filepath.Join(dir, "service.key"))
	if err != nil {
		t.Fatal(err)
	}
	if key, ok := pair.PrivateKey.(*ecdsa.PrivateKey); !ok || key.Curve != elliptic.P256() {
		t.Fatal("the Service certificate must use P-256")
	}
	service := readTLSCertificate(t, filepath.Join(dir, "service.crt"))
	// The full chain is the certificate, then the CA, and it works with the key.
	if got, want := readInitFile(t, folder, "ca/service/grafana/service-fullchain.crt"), readInitFile(t, folder, "ca/service/grafana/service.crt")+readInitFile(t, folder, "ca/ca.crt"); got != want {
		t.Fatal("service-fullchain.crt is not the certificate followed by the CA certificate")
	}
	chain, err := tls.LoadX509KeyPair(filepath.Join(dir, "service-fullchain.crt"), filepath.Join(dir, "service.key"))
	if err != nil || len(chain.Certificate) != 2 {
		t.Fatalf("the full chain does not load with the key as two certificates: %v", err)
	}
	for _, usage := range []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth} {
		for _, name := range []string{"grafana", "localhost", "127.0.0.1", "192.168.1.22", "app.example.test"} {
			if _, err := service.Verify(x509.VerifyOptions{Roots: roots, DNSName: name, KeyUsages: []x509.ExtKeyUsage{usage}}); err != nil {
				t.Fatalf("the Service certificate does not verify for %s with usage %v: %v", name, usage, err)
			}
		}
	}
	if service.NotAfter.Sub(service.NotBefore) != 42*24*time.Hour {
		t.Fatal("wrong leaf validity")
	}
	if exists(folder, "tls/traefik/https.crt") || exists(folder, "traefik/dynamic/12-mtls.yaml") {
		t.Fatal("signing a Service certificate installed or enabled TLS")
	}
	// The encrypted zip file holds the files to give to the Service, and opens only with its password.
	raw, err := os.ReadFile(filepath.Join(folder, "certificates", "grafana-service.zip"))
	if err != nil {
		t.Fatal(err)
	}
	password := ""
	for line := range strings.SplitSeq(zipRun.Stdout, "\n") {
		if after, ok := strings.CutPrefix(line, "Password: "); ok {
			password = after
		}
	}
	if password == "" {
		t.Fatalf("no random password was printed:\n%s", zipRun.Stdout)
	}
	if _, err := certzip.Open(raw, "wrong"); err == nil {
		t.Fatal("the zip file opened with a wrong password")
	}
	files, err := certzip.Open(raw, password)
	if err != nil {
		t.Fatal(err)
	}
	want := map[string]string{
		"service.crt":           readInitFile(t, folder, "ca/service/grafana/service.crt"),
		"service.key":           readInitFile(t, folder, "ca/service/grafana/service.key"),
		"service-fullchain.crt": readInitFile(t, folder, "ca/service/grafana/service-fullchain.crt"),
		"ca.crt":                readInitFile(t, folder, "ca/ca.crt"),
	}
	for _, file := range files {
		if expected, ok := want[file.Name]; ok {
			if string(file.Data) != expected {
				t.Fatalf("%s in the zip file differs from the file in ca/service/grafana", file.Name)
			}
			delete(want, file.Name)
		}
	}
	if len(want) != 0 {
		t.Fatalf("the zip file lacks %v", want)
	}
	assertPrivateWorkstation(t, folder)
}

func TestWSTLSIssueHasNoCommandForTraefikOrConsul(t *testing.T) {
	yard := commandtest.New(t)
	// The parts of Traefik sign their own certificates, and ws yard share signs Consul's. Nothing signs a
	// whole Workstation at once, and no command packs certificates but ca issue service and ws yard share.
	help := yard.Run("ws", "tls", "ca", "issue", "--help").Stdout
	for _, gone := range []string{"workstation ", "workstation\n", "consul", "traefik"} {
		if strings.Contains(help, gone) {
			t.Fatalf("ca issue still has a command for %q:\n%s", gone, help)
		}
	}
	if help := yard.Run("ws", "tls", "ca", "--help").Stdout; strings.Contains(help, "export") {
		t.Fatalf("ca still has an export command:\n%s", help)
	}
	if help := yard.Run("ws", "tls", "--help").Stdout; strings.Contains(help, "install") || strings.Contains(help, "enable") {
		t.Fatalf("ws tls still has the commands of one certificate for all of Traefik:\n%s", help)
	}
}

func TestWSTLSIssueServiceAsksForWhatIsNotGiven(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "example.test"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "issue", "service", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "Missing the Service name") {
		t.Fatalf("no name and no terminal: %+v", r)
	}
	yard.Process.Terminal = true

	// The name, the names to add, the validity, and an empty zip password for a random one.
	yard.Process.Stdin = "grafana\n192.168.1.22 *.ws-x.example.test\n42\n\n"
	r := yard.Run("ws", "tls", "ca", "issue", "service", "--dir", folder)
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	service := readTLSCertificate(t, filepath.Join(folder, "ca", "service", "grafana", "service.crt"))
	for _, name := range []string{"grafana", "192.168.1.22", "app.ws-x.example.test"} {
		if err := service.VerifyHostname(name); err != nil {
			t.Fatalf("the Service certificate lacks %s: %v", name, err)
		}
	}
	if service.NotAfter.Sub(service.NotBefore) != 42*24*time.Hour {
		t.Fatal("the answer for the validity was ignored")
	}
	if !strings.Contains(r.Stdout, "tls mtls create") {
		t.Fatalf("the next step is missing:\n%s", r.Stdout)
	}
}

func TestWSTLSHTTPSWildcardAddresses(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, explicit := range []bool{false, true} {
		args := []string{"ws", "tls", "https", "create", "--dir", folder}
		if explicit {
			args = append(args, "--address", "*.ws-b.lvh.me")
		}
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
		cert := readTLSCertificate(t, filepath.Join(folder, "ca", "yard", "ws-a", "https.crt"))
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
	if r := yard.Run("ws", "tls", "ca", "issue", "service", "svc", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "no readable CA") {
		t.Fatalf("missing CA: %+v", r)
	}
	if r := yard.Run("ws", "tls", "https", "create", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "no readable CA") {
		t.Fatalf("missing CA for HTTPS: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, args := range [][]string{
		{"ca", "issue", "service", "svc", "--days", "0"}, {"ca", "issue", "service", "svc", "--days", "-1"}, {"ca", "issue", "service", "svc", "--days", "100001"}, {"ca", "issue", "service", "svc", "--days", "4000"},
		{"ca", "issue", "service", "svc", "--address", "not an address"}, {"ca", "issue", "user", "../escape"},
		{"ca", "issue", "service", "Grafana"}, {"ca", "issue", "service", "a_b"}, {"ca", "issue", "service", "10.0.0.1"}, {"ca", "issue", "service", "../escape"}, {"ca", "issue", "service", "a", "b"},
		{"https", "create", "--days", "0"}, {"https", "create", "--days", "4000"}, {"mtls", "create", "--days", "0"}, {"mtls", "create", "--days", "4000"},
	} {
		if r := yard.Run(append(append([]string{"ws", "tls"}, args...), "--dir", folder)...); r.ExitCode == 0 {
			t.Fatalf("accepted %v", args)
		}
	}
	for _, address := range []string{
		"*", "*.127.0.0.1", "*.::1", "*.[::1]", "*.2001:db8::1",
		"ws-*.lvh.me", "*ws-b.lvh.me", "app.*.lvh.me", "*.*.lvh.me",
		"*.ws-b..lvh.me", "*.ws_b.lvh.me", "*.-ws-b.lvh.me",
		"*.ws-b.lvh.me.", "*." + strings.Repeat("a", 64) + ".lvh.me",
	} {
		t.Run(address, func(t *testing.T) {
			r := yard.Run("ws", "tls", "https", "create", "--dir", folder, "--address", address)
			if r.ExitCode == 0 || !strings.Contains(r.Stderr, "invalid --address") {
				t.Fatalf("malformed wildcard not refused: %+v", r)
			}
		})
	}
	if err := os.Remove(filepath.Join(folder, "ca", "ca.key")); err != nil {
		t.Fatal(err)
	}
	if r := yard.Run("ws", "tls", "ca", "issue", "service", "svc", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "CA key") {
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
	// The commands that use the key of the CA warn. A command that signs nothing, or imports your own files, does
	// not.
	for _, step := range []struct {
		args   []string
		warned bool
	}{
		{[]string{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"}, false},
		{[]string{"ws", "tls", "ca", "create", "--dir", folder}, true},
		{[]string{"ws", "tls", "ca", "issue", "service", "svc", "--dir", folder}, true},
		{[]string{"ws", "tls", "ca", "issue", "user", "alice", "--dir", folder}, true},
		{[]string{"ws", "tls", "https", "create", "--dir", folder}, true},
		{[]string{"ws", "tls", "mtls", "create", "--dir", folder}, true},
		{[]string{"ws", "tls", "client", "create", "--dir", folder}, false},
	} {
		r := yard.Run(step.args...)
		if r.ExitCode != 0 {
			t.Fatalf("%v: %+v", step.args, r)
		}
		warned := strings.Contains(r.Stderr, "for testing") && strings.Contains(r.Stderr, "ws tls https import")
		if warned != step.warned {
			t.Fatalf("%v: warning on stderr is %t:\n%s", step.args, warned, r.Stderr)
		}
	}
	if r := yard.Run("ws", "tls", "ca", "create", "--dir", folder); r.ExitCode == 0 || strings.Contains(r.Stderr, "for testing") {
		t.Fatalf("a refused command printed the warning: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "--help"); !strings.Contains(r.Stdout, "for testing") {
		t.Fatalf("the help of ca lacks the warning:\n%s", r.Stdout)
	}
	if r := yard.Run("ws", "tls", "https", "import", "--help"); strings.Contains(r.Stdout+r.Stderr, "for testing") {
		t.Fatal("import takes the files of a CA that you bring, so it has no testing warning")
	}
}
