package command_test

import (
	"crypto/x509"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

// caWorkstation makes a Workstation ws-a with its own CA.
func caWorkstation(t *testing.T, yard *commandtest.Harness, workstation, baseDomain string) string {
	t.Helper()
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", workstation, "--base-domain", baseDomain},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	return folder
}

func exists(folder, path string) bool {
	_, err := os.Stat(filepath.Join(folder, path))
	return err == nil
}

func mustRun(t *testing.T, yard *commandtest.Harness, args ...string) commandtest.Result {
	t.Helper()
	r := yard.Run(args...)
	if r.ExitCode != 0 {
		t.Fatalf("%v: %+v", args, r)
	}
	return r
}

func envHas(t *testing.T, folder, setting string) bool {
	t.Helper()
	return strings.Contains("\n"+readInitFile(t, folder, ".env"), "\n"+setting+"\n")
}

// The three parts of Traefik TLS do not touch each other's files or settings.
func TestWSTLSHTTPSCreateTurnsOnOnlyHTTPS(t *testing.T) {
	yard := commandtest.New(t)
	folder := caWorkstation(t, yard, "ws-a", "lvh.me")
	mustRun(t, yard, "ws", "tls", "https", "create", "--dir", folder)
	for file, from := range map[string]string{"https.crt": "ca/yard/ws-a/https.crt", "https.key": "ca/yard/ws-a/https.key"} {
		if readInitFile(t, folder, "tls/traefik/"+file) != readInitFile(t, folder, from) {
			t.Fatalf("tls/traefik/%s is not %s", file, from)
		}
	}
	https := readInitFile(t, folder, "traefik/dynamic/10-tls.yaml")
	for _, want := range []string{"/etc/traefik/tls/https.crt", "/etc/traefik/tls/https.key", "default:"} {
		if !strings.Contains(https, want) {
			t.Fatalf("10-tls.yaml lacks %s", want)
		}
	}
	if strings.Contains(https, "yard-client-certificate") || exists(folder, "traefik/dynamic/11-client-auth.yaml") || exists(folder, "traefik/dynamic/12-mtls.yaml") {
		t.Fatal("HTTPS turned on another part")
	}
	if !envHas(t, folder, "YARD_HTTPS_PORT=443") || !envHas(t, folder, "YARD_CLIENT_AUTH_ENABLED=false") || !envHas(t, folder, "YARD_MTLS_ENABLED=false") {
		t.Fatalf(".env is wrong:\n%s", readInitFile(t, folder, ".env"))
	}
	if exists(folder, "consul.d/10-tls.hcl") || exists(folder, "tls/consul/consul.crt") {
		t.Fatal("HTTPS touched Consul")
	}
	assertPrivateWorkstation(t, folder)

	// Turning HTTPS off keeps the files, and turning it on again keeps a port that was set.
	mustRun(t, yard, "ws", "tls", "https", "disable", "--dir", folder)
	if exists(folder, "traefik/dynamic/10-tls.yaml") || !envHas(t, folder, "YARD_HTTPS_PORT=") || !exists(folder, "tls/traefik/https.key") {
		t.Fatal("disable did not turn HTTPS off and keep the files")
	}
	mustRun(t, yard, "ws", "set", "--dir", folder, "https-port", "8443")
	mustRun(t, yard, "ws", "tls", "https", "enable", "--dir", folder)
	if !exists(folder, "traefik/dynamic/10-tls.yaml") || !envHas(t, folder, "YARD_HTTPS_PORT=8443") {
		t.Fatal("enable did not turn HTTPS on with the port that was set")
	}

	// An upgrade writes the generated file again.
	if err := os.WriteFile(filepath.Join(folder, "traefik", "dynamic", "10-tls.yaml"), []byte("broken"), 0600); err != nil {
		t.Fatal(err)
	}
	mustRun(t, yard, "ws", "compose", "upgrade", "--dir", folder)
	if readInitFile(t, folder, "traefik/dynamic/10-tls.yaml") != https {
		t.Fatal("upgrade did not regenerate TLS")
	}
}

func TestWSTLSHTTPSImportChecksTheCertificate(t *testing.T) {
	yard := commandtest.New(t)
	source := caWorkstation(t, yard, "ws-a", "lvh.me")
	mustRun(t, yard, "ws", "tls", "https", "create", "--dir", source)
	elsewhere := caWorkstation(t, yard, "ws-c", "elsewhere.test")
	mustRun(t, yard, "ws", "tls", "https", "create", "--dir", elsewhere)
	other := caWorkstation(t, yard, "ws-a", "lvh.me")
	in := func(folder, path string) string { return filepath.Join(folder, path) }
	for _, tc := range []struct {
		name, ca, cert, key, message string
	}{
		{"CA", in(other, "ca/ca.crt"), in(source, "ca/yard/ws-a/https.crt"), in(source, "ca/yard/ws-a/https.key"), "neither"},
		{"public CAs only", "", in(source, "ca/yard/ws-a/https.crt"), in(source, "ca/yard/ws-a/https.key"), "neither"},
		{"name", in(elsewhere, "ca/ca.crt"), in(elsewhere, "ca/yard/ws-c/https.crt"), in(elsewhere, "ca/yard/ws-c/https.key"), "yard.lvh.me"},
		{"key", in(source, "ca/ca.crt"), in(source, "ca/yard/ws-a/https.crt"), in(elsewhere, "ca/yard/ws-c/https.key"), "do not match"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			args := []string{"ws", "tls", "https", "import", "--dir", other, "--cert", tc.cert, "--key", tc.key}
			if tc.ca != "" {
				args = append(args, "--ca", tc.ca)
			}
			r := yard.Run(args...)
			if r.ExitCode == 0 || !strings.Contains(r.Stderr, tc.message) {
				t.Fatal(r)
			}
			if exists(other, "tls/traefik/https.key") || exists(other, "traefik/dynamic/10-tls.yaml") {
				t.Fatal("a refused import wrote TLS files")
			}
		})
	}
	mustRun(t, yard, "ws", "tls", "https", "import", "--dir", other, "--ca", in(source, "ca/ca.crt"), "--cert", in(source, "ca/yard/ws-a/https.crt"), "--key", in(source, "ca/yard/ws-a/https.key"))
	if !envHas(t, other, "YARD_HTTPS_PORT=443") || !exists(other, "traefik/dynamic/10-tls.yaml") || exists(other, "tls/traefik/client-ca.crt") {
		t.Fatal("import did not turn on HTTPS alone")
	}
	for _, args := range [][]string{{"--cert", "x.crt"}, {"--key", "x.key"}} {
		if r := yard.Run(append([]string{"ws", "tls", "https", "import", "--dir", other}, args...)...); r.ExitCode == 0 {
			t.Fatalf("accepted %v", args)
		}
	}
}

func TestWSTLSHTTPSEnableChecksWhatIsInTheFolder(t *testing.T) {
	yard := commandtest.New(t)
	folder := caWorkstation(t, yard, "ws-a", "lvh.me")
	if r := yard.Run("ws", "tls", "https", "enable", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "no HTTPS files") {
		t.Fatalf("enabled without a certificate: %+v", r)
	}
	mustRun(t, yard, "ws", "tls", "https", "create", "--dir", folder)
	mustRun(t, yard, "ws", "tls", "https", "disable", "--dir", folder)
	// A key that does not match the certificate must not be turned on.
	other := caWorkstation(t, yard, "ws-a", "lvh.me")
	mustRun(t, yard, "ws", "tls", "https", "create", "--dir", other)
	if err := os.WriteFile(filepath.Join(folder, "tls", "traefik", "https.key"), []byte(readInitFile(t, other, "ca/yard/ws-a/https.key")), 0600); err != nil {
		t.Fatal(err)
	}
	if r := yard.Run("ws", "tls", "https", "enable", "--dir", folder); r.ExitCode == 0 || exists(folder, "traefik/dynamic/10-tls.yaml") {
		t.Fatalf("enabled with a key of another certificate: %+v", r)
	}
}

func TestWSTLSPartsNeedTraefik(t *testing.T) {
	yard := commandtest.New(t)
	folder := caWorkstation(t, yard, "ws-a", "lvh.me")
	mustRun(t, yard, "ws", "tls", "https", "create", "--dir", folder)
	mustRun(t, yard, "ws", "set", "--dir", folder, "traefik", "off")
	for _, args := range [][]string{{"https", "create"}, {"https", "enable"}, {"client", "create"}, {"mtls", "create"}} {
		if r := yard.Run(append(append([]string{"ws", "tls"}, args...), "--dir", folder)...); r.ExitCode == 0 || !strings.Contains(r.Stderr, "Traefik is off") {
			t.Fatalf("%v with Traefik off: %+v", args, r)
		}
	}
	if exists(folder, "tls/traefik/client-ca.crt") || exists(folder, "tls/traefik/mtls.key") {
		t.Fatal("a refused command wrote files")
	}
}

func TestWSTLSClientTrustsTheWorkstationCAOrAnother(t *testing.T) {
	yard := commandtest.New(t)
	folder := caWorkstation(t, yard, "ws-a", "lvh.me")
	r := mustRun(t, yard, "ws", "tls", "client", "create", "--dir", folder)
	if !strings.Contains(r.Stdout, "HTTPS is off") {
		t.Fatalf("no note that HTTPS is off:\n%s", r.Stdout)
	}
	if readInitFile(t, folder, "tls/traefik/client-ca.crt") != readInitFile(t, folder, "ca/ca.crt") {
		t.Fatal("client-ca.crt is not the Workstation CA")
	}
	client := readInitFile(t, folder, "traefik/dynamic/11-client-auth.yaml")
	for _, want := range []string{"yard-client-certificate:", "RequireAndVerifyClientCert", "/etc/traefik/tls/client-ca.crt"} {
		if !strings.Contains(client, want) {
			t.Fatalf("11-client-auth.yaml lacks %s", want)
		}
	}
	if !envHas(t, folder, "YARD_CLIENT_AUTH_ENABLED=true") || envHas(t, folder, "YARD_HTTPS_PORT=443") || exists(folder, "traefik/dynamic/10-tls.yaml") {
		t.Fatalf("client create changed more than its own part:\n%s", readInitFile(t, folder, ".env"))
	}
	assertPrivateWorkstation(t, folder)

	mustRun(t, yard, "ws", "tls", "client", "disable", "--dir", folder)
	if exists(folder, "traefik/dynamic/11-client-auth.yaml") || !envHas(t, folder, "YARD_CLIENT_AUTH_ENABLED=false") || !exists(folder, "tls/traefik/client-ca.crt") {
		t.Fatal("disable did not turn the check off and keep the CA")
	}
	mustRun(t, yard, "ws", "tls", "client", "enable", "--dir", folder)
	if !exists(folder, "traefik/dynamic/11-client-auth.yaml") || !envHas(t, folder, "YARD_CLIENT_AUTH_ENABLED=true") {
		t.Fatal("enable did not turn the check on")
	}

	// Another CA, with no key. A certificate that is not a CA is refused.
	other := caWorkstation(t, yard, "ws-b", "lvh.me")
	mustRun(t, yard, "ws", "tls", "client", "import", "--dir", folder, "--ca", filepath.Join(other, "ca", "ca.crt"))
	if readInitFile(t, folder, "tls/traefik/client-ca.crt") != readInitFile(t, other, "ca/ca.crt") {
		t.Fatal("import did not use the CA that was given")
	}
	mustRun(t, yard, "ws", "tls", "ca", "issue", "service", "svc", "--dir", other)
	for _, ca := range []string{filepath.Join(other, "ca", "service", "svc", "service.crt"), filepath.Join(other, "missing.crt")} {
		if r := yard.Run("ws", "tls", "client", "import", "--dir", folder, "--ca", ca); r.ExitCode == 0 {
			t.Fatalf("imported %s as a CA", ca)
		}
	}
	if readInitFile(t, folder, "tls/traefik/client-ca.crt") != readInitFile(t, other, "ca/ca.crt") {
		t.Fatal("a refused import changed the CA")
	}
	if r := yard.Run("ws", "tls", "client", "import", "--dir", folder); r.ExitCode == 0 {
		t.Fatal("import without --ca")
	}
}

func TestWSTLSMTLSUsesAClientCertificateAndTheCA(t *testing.T) {
	yard := commandtest.New(t)
	folder := caWorkstation(t, yard, "ws-a", "lvh.me")
	mustRun(t, yard, "ws", "tls", "mtls", "create", "--dir", folder)
	ca := readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt"))
	roots := x509.NewCertPool()
	roots.AddCert(ca)
	cert := readTLSCertificate(t, filepath.Join(folder, "tls", "traefik", "mtls.crt"))
	if len(cert.ExtKeyUsage) != 1 || cert.ExtKeyUsage[0] != x509.ExtKeyUsageClientAuth {
		t.Fatalf("the certificate of Traefik allows %v, want client use only", cert.ExtKeyUsage)
	}
	if _, err := cert.Verify(x509.VerifyOptions{Roots: roots, KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth}}); err != nil {
		t.Fatal(err)
	}
	if readInitFile(t, folder, "tls/traefik/mtls-ca.crt") != readInitFile(t, folder, "ca/ca.crt") || readInitFile(t, folder, "tls/traefik/mtls.key") != readInitFile(t, folder, "ca/yard/ws-a/mtls.key") {
		t.Fatal("tls/traefik does not hold the CA and the key that were signed")
	}
	transport := readInitFile(t, folder, "traefik/dynamic/12-mtls.yaml")
	for _, want := range []string{"serversTransports:", "yard-mtls:", "/etc/traefik/tls/mtls-ca.crt", "/etc/traefik/tls/mtls.crt", "/etc/traefik/tls/mtls.key"} {
		if !strings.Contains(transport, want) {
			t.Fatalf("12-mtls.yaml lacks %s", want)
		}
	}
	if !envHas(t, folder, "YARD_MTLS_ENABLED=true") || exists(folder, "traefik/dynamic/10-tls.yaml") || exists(folder, "traefik/dynamic/11-client-auth.yaml") {
		t.Fatalf("mtls create changed more than its own part:\n%s", readInitFile(t, folder, ".env"))
	}
	assertPrivateWorkstation(t, folder)

	mustRun(t, yard, "ws", "tls", "mtls", "disable", "--dir", folder)
	if exists(folder, "traefik/dynamic/12-mtls.yaml") || !envHas(t, folder, "YARD_MTLS_ENABLED=false") || !exists(folder, "tls/traefik/mtls.key") {
		t.Fatal("disable did not turn mutual TLS off and keep the files")
	}
	mustRun(t, yard, "ws", "tls", "mtls", "enable", "--dir", folder)
	if !exists(folder, "traefik/dynamic/12-mtls.yaml") || !envHas(t, folder, "YARD_MTLS_ENABLED=true") {
		t.Fatal("enable did not turn mutual TLS on")
	}
}

func TestWSTLSMTLSImportChecksTheFiles(t *testing.T) {
	yard := commandtest.New(t)
	source := caWorkstation(t, yard, "ws-b", "lvh.me")
	mustRun(t, yard, "ws", "tls", "mtls", "create", "--dir", source)
	mustRun(t, yard, "ws", "tls", "https", "create", "--dir", source)
	folder := caWorkstation(t, yard, "ws-a", "lvh.me")
	in := func(path string) string { return filepath.Join(source, path) }
	imp := func(ca, cert, key string) commandtest.Result {
		return yard.Run("ws", "tls", "mtls", "import", "--dir", folder, "--ca", ca, "--cert", cert, "--key", key)
	}
	// The key of another certificate, and a certificate that allows server use only.
	if r := imp(in("ca/ca.crt"), in("ca/yard/ws-b/mtls.crt"), in("ca/yard/ws-b/https.key")); r.ExitCode == 0 || !strings.Contains(r.Stderr, "do not match") {
		t.Fatalf("mismatched key: %+v", r)
	}
	if r := imp(in("ca/ca.crt"), in("ca/yard/ws-b/https.crt"), in("ca/yard/ws-b/https.key")); r.ExitCode == 0 || !strings.Contains(r.Stderr, "client use") {
		t.Fatalf("server certificate: %+v", r)
	}
	if r := imp(in("ca/yard/ws-b/mtls.crt"), in("ca/yard/ws-b/mtls.crt"), in("ca/yard/ws-b/mtls.key")); r.ExitCode == 0 {
		t.Fatalf("a certificate that is not a CA: %+v", r)
	}
	if exists(folder, "tls/traefik/mtls.key") || exists(folder, "traefik/dynamic/12-mtls.yaml") {
		t.Fatal("a refused import wrote files")
	}
	// The CA that checks the Services may differ from the one that signed the certificate of Traefik.
	other := caWorkstation(t, yard, "ws-c", "lvh.me")
	if r := imp(filepath.Join(other, "ca", "ca.crt"), in("ca/yard/ws-b/mtls.crt"), in("ca/yard/ws-b/mtls.key")); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if readInitFile(t, folder, "tls/traefik/mtls-ca.crt") != readInitFile(t, other, "ca/ca.crt") || !envHas(t, folder, "YARD_MTLS_ENABLED=true") {
		t.Fatal("import did not use the CA and certificate that were given")
	}
	if r := yard.Run("ws", "tls", "mtls", "import", "--dir", folder, "--ca", in("ca/ca.crt")); r.ExitCode == 0 {
		t.Fatal("import without --cert and --key")
	}
}

// A folder from before the three parts has one set of Traefik files, and HTTPS turned on the check of Client
// Certificates with it.
func TestUpgradeSplitsTheTraefikFilesIntoParts(t *testing.T) {
	yard := commandtest.New(t)
	folder := caWorkstation(t, yard, "ws-a", "lvh.me")
	mustRun(t, yard, "ws", "tls", "https", "create", "--dir", folder)
	// Make it look like the old layout: old file names, no setting for the new parts, layout 1.
	for from, to := range map[string]string{"https.crt": "traefik.crt", "https.key": "traefik.key", "client-ca.crt": "ca.crt"} {
		source := filepath.Join(folder, "tls", "traefik", from)
		if from == "client-ca.crt" {
			if err := os.WriteFile(filepath.Join(folder, "tls", "traefik", to), []byte(readInitFile(t, folder, "ca/ca.crt")), 0600); err != nil {
				t.Fatal(err)
			}
			continue
		}
		if err := os.Rename(source, filepath.Join(folder, "tls", "traefik", to)); err != nil {
			t.Fatal(err)
		}
	}
	old := strings.NewReplacer("YARD_CLIENT_AUTH_ENABLED=false\n", "", "YARD_MTLS_ENABLED=false\n", "").Replace(readInitFile(t, folder, ".env"))
	if err := os.WriteFile(filepath.Join(folder, ".env"), []byte(old), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(folder, ".lvh-yard-layout"), []byte("1\n"), 0600); err != nil {
		t.Fatal(err)
	}

	r := mustRun(t, yard, "ws", "compose", "upgrade", "--dir", folder)
	if !strings.Contains(r.Stdout, "three parts") {
		t.Fatalf("no note about the new parts:\n%s", r.Stdout)
	}
	for _, name := range []string{"tls/traefik/https.crt", "tls/traefik/https.key", "tls/traefik/client-ca.crt", "traefik/dynamic/10-tls.yaml", "traefik/dynamic/11-client-auth.yaml"} {
		if !exists(folder, name) {
			t.Fatalf("missing %s after the upgrade", name)
		}
	}
	for _, name := range []string{"tls/traefik/traefik.crt", "tls/traefik/traefik.key", "tls/traefik/ca.crt", "ca/yard/ws-a/traefik.crt"} {
		if exists(folder, name) {
			t.Fatalf("%s is still there after the upgrade", name)
		}
	}
	if !envHas(t, folder, "YARD_CLIENT_AUTH_ENABLED=true") || envHas(t, folder, "YARD_MTLS_ENABLED=true") {
		t.Fatalf("the settings after the upgrade are wrong:\n%s", readInitFile(t, folder, ".env"))
	}
	// A second upgrade has nothing to say.
	if r := mustRun(t, yard, "ws", "compose", "upgrade", "--dir", folder); strings.Contains(r.Stdout, "three parts") {
		t.Fatalf("the migration ran twice:\n%s", r.Stdout)
	}
}

// A folder from before the groups keeps what the CA signed in ca/certs/<name>/ and ca/clients/.
func TestUpgradeMovesWhatTheCASignedIntoGroups(t *testing.T) {
	yard := commandtest.New(t)
	folder := caWorkstation(t, yard, "ws-a", "lvh.me")
	files := map[string]string{
		"ca/certs/ws-a/consul.crt":               "consul",
		"ca/certs/ws-a/https.key":                "https",
		"ca/certs/grafana/service.key":           "service",
		"ca/certs/grafana/service-fullchain.crt": "chain",
		"ca/clients/alice.p12":                   "p12",
		"ca/certs/ws-a/unknown.txt":              "kept",
	}
	for name, text := range files {
		if err := os.MkdirAll(filepath.Dir(filepath.Join(folder, name)), 0700); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(folder, name), []byte(text), 0600); err != nil {
			t.Fatal(err)
		}
	}
	if err := os.WriteFile(filepath.Join(folder, ".lvh-yard-layout"), []byte("2\n"), 0600); err != nil {
		t.Fatal(err)
	}

	r := mustRun(t, yard, "ws", "compose", "upgrade", "--dir", folder)
	if !strings.Contains(r.Stdout, "new places") {
		t.Fatalf("no note about the new places:\n%s", r.Stdout)
	}
	for from, to := range map[string]string{
		"ca/certs/ws-a/consul.crt":               "ca/yard/ws-a/consul.crt",
		"ca/certs/ws-a/https.key":                "ca/yard/ws-a/https.key",
		"ca/certs/grafana/service.key":           "ca/service/grafana/service.key",
		"ca/certs/grafana/service-fullchain.crt": "ca/service/grafana/service-fullchain.crt",
		"ca/clients/alice.p12":                   "ca/client/alice.p12",
	} {
		if exists(folder, from) || readInitFile(t, folder, to) != files[from] {
			t.Fatalf("%s did not move to %s", from, to)
		}
	}
	if !exists(folder, "ca/certs/ws-a/unknown.txt") || !exists(folder, "ca/ca.crt") || !exists(folder, "ca/ca.key") {
		t.Fatal("the migration moved a file that it does not know, or the CA")
	}
	if exists(folder, "ca/clients") || exists(folder, "ca/certs/grafana") {
		t.Fatal("an empty old directory is still there")
	}
	assertPrivateWorkstation(t, folder)
}
