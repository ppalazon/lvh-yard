package command_test

import (
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

// signedYard makes a Workstation ws-a with a Yard CA that signed both certificates of ws-a and
// of ws-b.
func signedYard(t *testing.T, yard *commandtest.Harness) string {
	t.Helper()
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a"},
		{"ws", "tls", "ca", "create", "--dir", folder},
		{"ws", "tls", "ca", "issue", "workstation", "--dir", folder},
		{"ws", "tls", "ca", "issue", "workstation", "ws-b", "--dir", folder},
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

// issued is where the Yard CA keeps what it signed for a Workstation.
func issued(workstation, role, extension string) string {
	return "ca/certs/" + workstation + "/" + role + extension
}

func TestWSTLSImport(t *testing.T) {
	yard := commandtest.New(t)
	source := signedYard(t, yard)
	run := func(args ...string) {
		t.Helper()
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	run("ws", "tls", "ca", "issue", "traefik", "ws-c", "--base-domain", "elsewhere.test", "--dir", source)
	other := t.TempDir()
	run("ws", "init", "--dir", other, "--workstation", "ws-a")
	run("ws", "tls", "ca", "create", "--dir", other)
	in := func(path string) string { return filepath.Join(source, path) }
	for _, tc := range []struct{ name, role, ca, cert, key, message string }{
		{"consul key", "consul", in("ca/ca.crt"), in(issued("ws-a", "consul", ".crt")), in(issued("ws-b", "consul", ".key")), "key"},
		{"consul CA", "consul", filepath.Join(other, "ca/ca.crt"), in(issued("ws-a", "consul", ".crt")), in(issued("ws-a", "consul", ".key")), "CA"},
		{"consul name", "consul", in("ca/ca.crt"), in(issued("ws-b", "consul", ".crt")), in(issued("ws-b", "consul", ".key")), "server.ws-a.consul"},
		{"traefik CA", "traefik", filepath.Join(other, "ca/ca.crt"), in(issued("ws-a", "traefik", ".crt")), in(issued("ws-a", "traefik", ".key")), "neither"},
		{"traefik name", "traefik", in("ca/ca.crt"), in(issued("ws-c", "traefik", ".crt")), in(issued("ws-c", "traefik", ".key")), "yard.lvh.me"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := yard.Run("ws", "tls", "import", tc.role, "--dir", other, "--ca", tc.ca, "--cert", tc.cert, "--key", tc.key)
			if r.ExitCode == 0 || !strings.Contains(r.Stderr, tc.message) {
				t.Fatal(r)
			}
			if exists(other, "tls/"+tc.role+"/"+tc.role+".key") {
				t.Fatal("refused import wrote TLS files")
			}
		})
	}

	r := yard.Run("ws", "tls", "import", "consul", "--dir", other, "--ca", in("ca/ca.crt"), "--cert", in(issued("ws-a", "consul", ".crt")), "--key", in(issued("ws-a", "consul", ".key")))
	if r.ExitCode != 0 || !strings.Contains(r.Stdout, "cannot federate") {
		t.Fatal(r)
	}
	for file, from := range map[string]string{"tls/consul/ca.crt": "ca/ca.crt", "tls/consul/consul.crt": issued("ws-a", "consul", ".crt"), "tls/consul/consul.key": issued("ws-a", "consul", ".key")} {
		if readInitFile(t, other, file) != readInitFile(t, source, from) {
			t.Fatalf("imported %s is not %s", file, from)
		}
	}
	if exists(other, "traefik/dynamic/10-tls.yaml") || strings.Contains(readInitFile(t, other, ".env"), "YARD_HTTPS_PORT=443") {
		t.Fatal("importing the Consul certificate turned on Traefik HTTPS")
	}
	config := readInitFile(t, other, "consul.d/10-tls.hcl")
	for _, setting := range []string{"/consul/tls/consul.crt", "/consul/tls/consul.key", "verify_incoming = true", "verify_outgoing = true", "verify_server_hostname = true", "http", "verify_incoming = false"} {
		if !strings.Contains(config, setting) {
			t.Fatalf("missing %s", setting)
		}
	}

	run("ws", "tls", "import", "traefik", "--dir", other, "--ca", in("ca/ca.crt"), "--cert", in(issued("ws-a", "traefik", ".crt")), "--key", in(issued("ws-a", "traefik", ".key")))
	if !strings.Contains(readInitFile(t, other, ".env"), "YARD_HTTPS_PORT=443\n") {
		t.Fatal("import did not enable HTTPS")
	}
	tlsConfig := readInitFile(t, other, "traefik/dynamic/10-tls.yaml")
	for _, want := range []string{"yard-client-certificate:", "RequireAndVerifyClientCert", "/etc/traefik/tls/ca.crt", "/etc/traefik/tls/traefik.crt", "/etc/traefik/tls/traefik.key", "default:"} {
		if !strings.Contains(tlsConfig, want) {
			t.Fatalf("missing TLS contract %s", want)
		}
	}
	assertPrivateWorkstation(t, other)

	run("ws", "set", "--dir", other, "https-port", "8443")
	run("ws", "tls", "import", "traefik", "--dir", other, "--ca", in("ca/ca.crt"), "--cert", in(issued("ws-a", "traefik", ".crt")), "--key", in(issued("ws-a", "traefik", ".key")))
	if !strings.Contains(readInitFile(t, other, ".env"), "YARD_HTTPS_PORT=8443\n") {
		t.Fatal("import replaced the HTTPS port that was set")
	}

	run("ws", "set", "--dir", other, "traefik", "off")
	if r := yard.Run("ws", "tls", "import", "traefik", "--dir", other, "--ca", in("ca/ca.crt"), "--cert", in(issued("ws-a", "traefik", ".crt")), "--key", in(issued("ws-a", "traefik", ".key"))); r.ExitCode == 0 || !strings.Contains(r.Stderr, "Traefik is off") {
		t.Fatalf("imported a Traefik certificate while Traefik is off: %+v", r)
	}

	if err := os.WriteFile(filepath.Join(other, "consul.d", "10-tls.hcl"), []byte("broken"), 0600); err != nil {
		t.Fatal(err)
	}
	run("ws", "compose", "upgrade", "--dir", other)
	if restored := readInitFile(t, other, "consul.d/10-tls.hcl"); restored != config {
		t.Fatal("upgrade did not regenerate TLS")
	}
}

func TestWSTLSInstallHereAndForAnotherWorkstation(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	if r := yard.Run("ws", "tls", "ca", "install", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, role := range []string{"consul", "traefik"} {
		for file, from := range map[string]string{"ca.crt": "ca/ca.crt", role + ".crt": issued("ws-a", role, ".crt"), role + ".key": issued("ws-a", role, ".key")} {
			if readInitFile(t, folder, "tls/"+role+"/"+file) != readInitFile(t, folder, from) {
				t.Fatalf("tls/%s/%s is not %s", role, file, from)
			}
		}
	}
	if exists(folder, "tls/consul/traefik.key") || exists(folder, "tls/traefik/consul.key") {
		t.Fatal("a service folder holds the key of the other service")
	}
	if !exists(folder, "consul.d/10-tls.hcl") || !exists(folder, "traefik/dynamic/10-tls.yaml") || !strings.Contains(readInitFile(t, folder, ".env"), "YARD_HTTPS_PORT=443\n") {
		t.Fatal("without a terminal, install turns on Consul TLS and Traefik HTTPS")
	}

	if r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	other := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", other, "--workstation", "ws-b"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	bundle := filepath.Join(folder, "certificates", "ws-b")
	for _, role := range []string{"consul", "traefik"} {
		dir := filepath.Join(bundle, role)
		if r := yard.Run("ws", "tls", "import", role, "--dir", other, "--ca", dir+"/ca.crt", "--cert", dir+"/"+role+".crt", "--key", dir+"/"+role+".key"); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	if !exists(other, "consul.d/10-tls.hcl") || !exists(other, "traefik/dynamic/10-tls.yaml") {
		t.Fatal("the bundle did not import")
	}

	if r := yard.Run("ws", "tls", "ca", "install", "ws-z", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "signed nothing for ws-z") {
		t.Fatalf("a Workstation without a certificate was installed: %+v", r)
	}
	assertPrivateWorkstation(t, folder)

	// A role that was never signed is not installed, and a flag that names it is an error.
	only := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", only, "--workstation", "ws-a"},
		{"ws", "tls", "ca", "create", "--dir", only},
		{"ws", "tls", "ca", "issue", "consul", "--dir", only},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	if r := yard.Run("ws", "tls", "ca", "install", "--dir", only, "--traefik"); r.ExitCode == 0 || !strings.Contains(r.Stderr, "no traefik certificate") {
		t.Fatalf("installed a Traefik certificate that was not signed: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "install", "--dir", only); r.ExitCode != 0 || !exists(only, "tls/consul/consul.crt") || exists(only, "tls/traefik/traefik.crt") || exists(only, "traefik/dynamic/10-tls.yaml") {
		t.Fatalf("install should take only the Consul certificate: %+v", r)
	}
}

func TestWSTLSInstallAsksWhichServicesToTurnOn(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	yard.Process.Terminal = true
	yard.Process.Stdin = "\nmaybe\ny\nn\n"
	r := yard.Run("ws", "tls", "ca", "install", "--dir", folder)
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	if !strings.Contains(r.Stdout, "Answer y or n.") {
		t.Fatalf("an unclear answer was not asked again:\n%s", r.Stdout)
	}
	if !exists(folder, "consul.d/10-tls.hcl") || exists(folder, "traefik/dynamic/10-tls.yaml") || exists(folder, "tls/traefik/traefik.crt") || strings.Contains(readInitFile(t, folder, ".env"), "YARD_HTTPS_PORT=443") {
		t.Fatal("the answers were y for Consul and n for Traefik")
	}
}

func TestWSTLSEnableAndDisable(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	if r := yard.Run("ws", "tls", "enable", "consul", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "no consul certificate") {
		t.Fatalf("enabled without a certificate: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "install", "--dir", folder, "--consul=false", "--traefik=false"); r.ExitCode != 0 || exists(folder, "tls/consul/consul.crt") {
		t.Fatalf("install with both flags off should install nothing: %+v", r)
	}
	if r := yard.Run("ws", "tls", "ca", "install", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, step := range []struct {
		args     []string
		consul   bool
		traefik  bool
		httpPort string
	}{
		{[]string{"disable", "consul"}, false, true, "443"},
		{[]string{"enable", "consul"}, true, true, "443"},
		{[]string{"disable", "traefik"}, true, false, ""},
		{[]string{"enable", "traefik"}, true, true, "443"},
	} {
		if r := yard.Run(append([]string{"ws", "tls"}, append(step.args, "--dir", folder)...)...); r.ExitCode != 0 {
			t.Fatalf("%v: %+v", step.args, r)
		}
		if exists(folder, "consul.d/10-tls.hcl") != step.consul || exists(folder, "traefik/dynamic/10-tls.yaml") != step.traefik {
			t.Fatalf("after %v the files are wrong", step.args)
		}
		if !strings.Contains(readInitFile(t, folder, ".env"), "YARD_HTTPS_PORT="+step.httpPort+"\n") {
			t.Fatalf("after %v the HTTPS port is not %q", step.args, step.httpPort)
		}
	}
	if !exists(folder, "tls/consul/consul.key") || !exists(folder, "tls/traefik/traefik.key") {
		t.Fatal("disable removed a certificate")
	}
	if r := yard.Run("ws", "tls", "enable", "docker", "--dir", folder); r.ExitCode == 0 {
		t.Fatal("accepted an unknown service")
	}
}

func TestWSTLSInstallForAnotherWorkstationExplainsEachWay(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)

	by := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder)
	if by.ExitCode != 0 {
		t.Fatal(by)
	}
	if len(yard.Process.Commands) != 0 {
		t.Fatalf("copied without being asked: %+v", yard.Process.Commands)
	}
	for _, want := range []string{
		"consul/: ca.crt, consul.crt and consul.key", "traefik/: ca.crt, traefik.crt and traefik.key", "never send ca.key",
		"If ws-b runs on another computer", "scp -r", "<user@host>:.config/lvh-yard/tls/", "--scp <user@host>",
		"lvh-yard ws tls enable consul", "lvh-yard ws tls enable traefik",
		"If ws-b runs on this computer", "ws --dir '<ws-b-workstation-folder>' tls import consul", "tls import traefik",
	} {
		if !strings.Contains(by.Stdout, want) {
			t.Fatalf("hand copy lacks %q:\n%s", want, by.Stdout)
		}
	}

	bundle := filepath.Join(folder, "certificates", "ws-b")
	for _, tc := range []struct{ target, folder, ws string }{
		{"me@ws-b.lan", ".config/lvh-yard", "lvh-yard ws tls enable consul"},
		{"me@ws-b.lan:/srv/yard", "/srv/yard", "lvh-yard ws --dir '/srv/yard' tls enable consul"},
	} {
		yard.Process.Commands = nil
		over := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", tc.target)
		if over.ExitCode != 0 {
			t.Fatal(over)
		}
		commands := yard.Process.Commands
		if len(commands) != 2 || commands[0].Name != "ssh" || commands[1].Name != "scp" {
			t.Fatalf("expected ssh then scp: %+v", commands)
		}
		if got := commands[0].Args; len(got) != 2 || got[0] != "me@ws-b.lan" || got[1] != "umask 077 && mkdir -p '"+tc.folder+"/tls'" {
			t.Fatalf("the remote tls folder is not made private first: %v", got)
		}
		want := []string{"-r", bundle + "/consul", bundle + "/traefik", "me@ws-b.lan:" + tc.folder + "/tls/"}
		if strings.Join(commands[1].Args, " ") != strings.Join(want, " ") {
			t.Fatalf("scp arguments %v, want %v", commands[1].Args, want)
		}
		if !strings.Contains(over.Stdout, tc.ws) || strings.Contains(over.Stdout, "If ws-b runs on another computer") {
			t.Fatalf("remote steps are wrong:\n%s", over.Stdout)
		}
	}

	yard.Process.RunCommand = func(name string, args []string, dir string, stdout, stderr io.Writer) (int, error) {
		return 1, nil
	}
	if failed := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", "me@ws-b.lan"); failed.ExitCode == 0 || !strings.Contains(failed.Stderr, "ssh failed") {
		t.Fatalf("a failed copy was not reported: %+v", failed)
	}
	for _, bad := range []string{"-oProxyCommand=x", "me@host:~/yard", "me@host:it's"} {
		if r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", bad); r.ExitCode == 0 {
			t.Fatalf("accepted %q", bad)
		}
	}
}

func TestWSTLSEnableChecksACertificateCopiedByHand(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	copyRole := func(workstation string) {
		t.Helper()
		for file, from := range map[string]string{"ca.crt": "ca/ca.crt", "consul.crt": issued(workstation, "consul", ".crt"), "consul.key": issued(workstation, "consul", ".key")} {
			if err := os.WriteFile(filepath.Join(folder, "tls", "consul", file), []byte(readInitFile(t, folder, from)), 0600); err != nil {
				t.Fatal(err)
			}
		}
	}
	copyRole("ws-b")
	r := yard.Run("ws", "tls", "enable", "consul", "--dir", folder)
	if r.ExitCode == 0 || !strings.Contains(r.Stderr, "server.ws-a.consul") || exists(folder, "consul.d/10-tls.hcl") {
		t.Fatalf("enabled with the certificate of another Workstation: %+v", r)
	}
	copyRole("ws-a")
	if r := yard.Run("ws", "tls", "enable", "consul", "--dir", folder); r.ExitCode != 0 || !exists(folder, "consul.d/10-tls.hcl") {
		t.Fatalf("a good certificate was refused: %+v", r)
	}
}

func TestWSTLSInstallAsksWhereTheFilesGoBeforeWhichOnes(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	yard.Process.Terminal = true
	// The Workstation name, the scp destination, then y for Consul and n for Traefik.
	yard.Process.Stdin = "ws-b\nme@ws-b.lan\ny\nn\n"
	r := yard.Run("ws", "tls", "ca", "install", "--dir", folder)
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	scp := strings.Index(r.Stdout, "with scp?")
	consul := strings.Index(r.Stdout, "Copy the Consul certificate?")
	if scp < 0 || consul < 0 || scp > consul {
		t.Fatalf("the scp question must come before the questions about the certificates:\n%s", r.Stdout)
	}
	commands := yard.Process.Commands
	bundle := filepath.Join(folder, "certificates", "ws-b")
	if len(commands) != 2 || strings.Join(commands[1].Args, " ") != "-r "+bundle+"/consul me@ws-b.lan:.config/lvh-yard/tls/" {
		t.Fatalf("only the Consul folder should be copied: %+v", commands)
	}
}
