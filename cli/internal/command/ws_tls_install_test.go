package command_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

// signedWorkstation makes a Workstation ws-a with a CA that signed the Traefik certificate of ws-a.
func signedWorkstation(t *testing.T, yard *commandtest.Harness) string {
	t.Helper()
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a"},
		{"ws", "tls", "ca", "create", "--dir", folder},
		{"ws", "tls", "ca", "issue", "traefik", "--dir", folder},
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

// issued is where a CA keeps what it signed for a Workstation.
func issued(workstation, role, extension string) string {
	return "ca/certs/" + workstation + "/" + role + extension
}

func TestWSTLSInstallTakesTheFilesOfAnotherCA(t *testing.T) {
	yard := commandtest.New(t)
	source := signedWorkstation(t, yard)
	run := func(args ...string) {
		t.Helper()
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	// A Workstation with another Base Domain has a Traefik certificate that lacks yard.lvh.me.
	elsewhere := t.TempDir()
	run("ws", "init", "--dir", elsewhere, "--workstation", "ws-c", "--base-domain", "elsewhere.test")
	run("ws", "tls", "ca", "create", "--dir", elsewhere)
	run("ws", "tls", "ca", "issue", "traefik", "--dir", elsewhere)
	other := t.TempDir()
	run("ws", "init", "--dir", other, "--workstation", "ws-a")
	run("ws", "tls", "ca", "create", "--dir", other)
	in := func(path string) string { return filepath.Join(source, path) }
	for _, tc := range []struct{ name, ca, cert, key, message string }{
		{"CA", filepath.Join(other, "ca/ca.crt"), in(issued("ws-a", "traefik", ".crt")), in(issued("ws-a", "traefik", ".key")), "neither"},
		{"name", filepath.Join(elsewhere, "ca/ca.crt"), filepath.Join(elsewhere, issued("ws-c", "traefik", ".crt")), filepath.Join(elsewhere, issued("ws-c", "traefik", ".key")), "yard.lvh.me"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := yard.Run("ws", "tls", "install", "--dir", other, "--ca", tc.ca, "--cert", tc.cert, "--key", tc.key)
			if r.ExitCode == 0 || !strings.Contains(r.Stderr, tc.message) {
				t.Fatal(r)
			}
			if exists(other, "tls/traefik/traefik.key") {
				t.Fatal("refused install wrote TLS files")
			}
		})
	}

	install := []string{"ws", "tls", "install", "--dir", other, "--ca", in("ca/ca.crt"), "--cert", in(issued("ws-a", "traefik", ".crt")), "--key", in(issued("ws-a", "traefik", ".key"))}
	run(install...)
	if !strings.Contains(readInitFile(t, other, ".env"), "YARD_HTTPS_PORT=443\n") {
		t.Fatal("install did not enable HTTPS")
	}
	if exists(other, "consul.d/10-tls.hcl") {
		t.Fatal("installing the Traefik certificate turned on Consul TLS")
	}
	tlsConfig := readInitFile(t, other, "traefik/dynamic/10-tls.yaml")
	for _, want := range []string{"yard-client-certificate:", "RequireAndVerifyClientCert", "/etc/traefik/tls/ca.crt", "/etc/traefik/tls/traefik.crt", "/etc/traefik/tls/traefik.key", "default:"} {
		if !strings.Contains(tlsConfig, want) {
			t.Fatalf("missing TLS contract %s", want)
		}
	}
	assertPrivateWorkstation(t, other)

	run("ws", "set", "--dir", other, "https-port", "8443")
	run(install...)
	if !strings.Contains(readInitFile(t, other, ".env"), "YARD_HTTPS_PORT=8443\n") {
		t.Fatal("install replaced the HTTPS port that was set")
	}

	if err := os.WriteFile(filepath.Join(other, "traefik", "dynamic", "10-tls.yaml"), []byte("broken"), 0600); err != nil {
		t.Fatal(err)
	}
	run("ws", "compose", "upgrade", "--dir", other)
	if restored := readInitFile(t, other, "traefik/dynamic/10-tls.yaml"); restored != tlsConfig {
		t.Fatal("upgrade did not regenerate TLS")
	}

	run("ws", "set", "--dir", other, "traefik", "off")
	if r := yard.Run(install...); r.ExitCode == 0 || !strings.Contains(r.Stderr, "Traefik is off") {
		t.Fatalf("installed a Traefik certificate while Traefik is off: %+v", r)
	}
}

func TestWSTLSInstallUsesTheCertificateOfThisWorkstationsCA(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedWorkstation(t, yard)
	if r := yard.Run("ws", "tls", "install", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for file, from := range map[string]string{"ca.crt": "ca/ca.crt", "traefik.crt": issued("ws-a", "traefik", ".crt"), "traefik.key": issued("ws-a", "traefik", ".key")} {
		if readInitFile(t, folder, "tls/traefik/"+file) != readInitFile(t, folder, from) {
			t.Fatalf("tls/traefik/%s is not %s", file, from)
		}
	}
	if exists(folder, "tls/consul/consul.crt") || exists(folder, "consul.d/10-tls.hcl") {
		t.Fatal("installing Traefik touched Consul")
	}
	if !exists(folder, "traefik/dynamic/10-tls.yaml") || !strings.Contains(readInitFile(t, folder, ".env"), "YARD_HTTPS_PORT=443\n") {
		t.Fatal("install turns on Traefik HTTPS")
	}
	assertPrivateWorkstation(t, folder)
}

// The Consul commands are gone: ws yard share and ws yard join install the Consul certificate.
func TestWSTLSInstallRefusals(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedWorkstation(t, yard)
	// The three files of another CA come together or not at all.
	for _, args := range [][]string{
		{"install", "traefik"}, {"install", "consul"}, {"install", "--cert", "x.crt"}, {"install", "--ca", "ca.crt", "--cert", "x.crt"},
		{"install", "--key", "x.key"}, {"enable", "consul"}, {"disable", "consul"}, {"enable", "traefik"}, {"disable", "traefik"},
	} {
		if r := yard.Run(append(append([]string{"ws", "tls"}, args...), "--dir", folder)...); r.ExitCode == 0 {
			t.Fatalf("accepted %v", args)
		}
	}
	// A group command prints its help for a name that it does not know, so check the help.
	if help := yard.Run("ws", "tls", "--help").Stdout; strings.Contains(help, "import") {
		t.Fatalf("ws tls still has an import command:\n%s", help)
	}
	if len(yard.Process.Commands) != 0 {
		t.Fatalf("ran a command: %+v", yard.Process.Commands)
	}

	only := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", only, "--workstation", "ws-a"},
		{"ws", "tls", "ca", "create", "--dir", only},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	if r := yard.Run("ws", "tls", "install", "--dir", only); r.ExitCode == 0 || !strings.Contains(r.Stderr, "no traefik certificate") || !strings.Contains(r.Stderr, "--cert") || exists(only, "tls/traefik/traefik.crt") {
		t.Fatalf("installed a Traefik certificate that was not signed: %+v", r)
	}

	if r := yard.Run("ws", "set", "--dir", folder, "traefik", "off"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "install", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "Traefik is off") || exists(folder, "tls/traefik/traefik.crt") {
		t.Fatalf("installed a Traefik certificate while Traefik is off: %+v", r)
	}
}

func TestWSTLSEnableAndDisable(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedWorkstation(t, yard)
	if r := yard.Run("ws", "tls", "enable", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "no traefik certificate") {
		t.Fatalf("enabled without a certificate: %+v", r)
	}
	if r := yard.Run("ws", "tls", "install", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, step := range []struct {
		command  string
		traefik  bool
		httpPort string
	}{
		{"disable", false, ""},
		{"enable", true, "443"},
	} {
		if r := yard.Run("ws", "tls", step.command, "--dir", folder); r.ExitCode != 0 {
			t.Fatalf("%s: %+v", step.command, r)
		}
		if exists(folder, "traefik/dynamic/10-tls.yaml") != step.traefik {
			t.Fatalf("after %s the files are wrong", step.command)
		}
		if !strings.Contains(readInitFile(t, folder, ".env"), "YARD_HTTPS_PORT="+step.httpPort+"\n") {
			t.Fatalf("after %s the HTTPS port is not %q", step.command, step.httpPort)
		}
	}
	if !exists(folder, "tls/traefik/traefik.key") {
		t.Fatal("disable removed the certificate")
	}
}

func TestWSTLSEnableChecksACertificateCopiedByHand(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedWorkstation(t, yard)
	if r := yard.Run("ws", "tls", "install", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if r := yard.Run("ws", "tls", "disable", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	// A key that does not match the certificate must not be turned on.
	other := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", other, "--workstation", "ws-a"},
		{"ws", "tls", "ca", "create", "--dir", other},
		{"ws", "tls", "ca", "issue", "traefik", "--dir", other},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	if err := os.WriteFile(filepath.Join(folder, "tls", "traefik", "traefik.key"), []byte(readInitFile(t, other, issued("ws-a", "traefik", ".key"))), 0600); err != nil {
		t.Fatal(err)
	}
	if r := yard.Run("ws", "tls", "enable", "--dir", folder); r.ExitCode == 0 || exists(folder, "traefik/dynamic/10-tls.yaml") {
		t.Fatalf("enabled with a key of another certificate: %+v", r)
	}
}
