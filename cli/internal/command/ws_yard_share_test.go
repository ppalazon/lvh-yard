package command_test

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/certzip"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

const yardTestKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

// originWorkstation makes ws-a with a CA that has no password, opened by ws yard init on 192.168.1.10.
func originWorkstation(t *testing.T) (*commandtest.Harness, string) {
	return originWorkstationWith(t)
}

// originWorkstationWith makes the same, with extra flags for ws tls ca create and ws yard init, such
// as --ca-key-password.
func originWorkstationWith(t *testing.T, flags ...string) (*commandtest.Harness, string) {
	t.Helper()
	yard := commandtest.New(t)
	yard.Process.LANIP = "192.168.1.10"
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	// The tests look for this key in what the commands print and write.
	env := regexp.MustCompile(`(?m)^YARD_GOSSIP_KEY=.*$`).ReplaceAllString(readInitFile(t, folder, ".env"), "YARD_GOSSIP_KEY="+yardTestKey)
	writeEnv(t, folder, env)
	for _, command := range []string{"tls ca create", "yard init"} {
		if r := yard.Run(append(append([]string{"ws"}, strings.Fields(command)...), append([]string{"--dir", folder}, flags...)...)...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	return yard, folder
}

func openInvitationZip(t *testing.T, path, password string) map[string][]byte {
	t.Helper()
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	files, err := certzip.Open(raw, password)
	if err != nil {
		t.Fatal(err)
	}
	byName := map[string][]byte{}
	for _, file := range files {
		byName[file.Name] = file.Data
	}
	return byName
}

func TestWSYardShareMakesAnEncryptedInvitationAndOpensThisWorkstation(t *testing.T) {
	yard, folder := originWorkstation(t)
	r := yard.Run("ws", "yard", "share", "ws-b", "--dir", folder, "--password", "zip-secret")
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	if strings.Contains(r.Stdout+r.Stderr, yardTestKey) || strings.Contains(r.Stdout+r.Stderr, "zip-secret") || strings.Contains(r.Stdout, "PRIVATE KEY") {
		t.Fatalf("share printed a secret:\n%s", r.Stdout)
	}
	for _, want := range []string{"ws yard join", "ca.key", "yard peers --sync"} {
		if !strings.Contains(r.Stdout, want) {
			t.Fatalf("the output lacks %q:\n%s", want, r.Stdout)
		}
	}
	path := filepath.Join(folder, "certificates", "ws-b-yard.zip")
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(raw), yardTestKey) || strings.Contains(string(raw), "PRIVATE KEY") || strings.Contains(string(raw), "ws-a") {
		t.Fatal("the invitation shows its content without the password")
	}
	assertOwnerOnly(t, path)
	files := openInvitationZip(t, path, "zip-secret")
	var invitation struct {
		Version                   int
		From, To, Peer, GossipKey string
	}
	if err := json.Unmarshal(files["yard.json"], &invitation); err != nil {
		t.Fatal(err)
	}
	if invitation.Version != 1 || invitation.From != "ws-a" || invitation.To != "ws-b" || invitation.Peer != "192.168.1.10:8302" || invitation.GossipKey != yardTestKey {
		t.Fatalf("invitation = %+v", invitation)
	}
	if string(files["consul/ca.crt"]) != readInitFile(t, folder, "ca/ca.crt") {
		t.Fatal("the invitation holds another CA than the one of this Workstation")
	}
	pair, err := tls.X509KeyPair(files["consul/consul.crt"], files["consul/consul.key"])
	if err != nil {
		t.Fatalf("the certificate and the key do not match: %v", err)
	}
	leaf, _ := x509.ParseCertificate(pair.Certificate[0])
	roots := x509.NewCertPool()
	roots.AddCert(readTLSCertificate(t, filepath.Join(folder, "ca", "ca.crt")))
	if _, err := leaf.Verify(x509.VerifyOptions{Roots: roots, DNSName: "server.ws-b.consul", KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth}}); err != nil {
		t.Fatalf("the Consul certificate of ws-b does not verify: %v", err)
	}

	// share changes nothing else: this Workstation was opened by ws yard init.
	env, tlsKey := readInitFile(t, folder, ".env"), readInitFile(t, folder, "tls/consul/consul.key")
	if r := yard.Run("ws", "yard", "share", "ws-c", "--dir", folder, "--password", "other-secret"); r.ExitCode != 0 || strings.Contains(r.Stdout, "Saved .env") {
		t.Fatalf("second share: %+v", r)
	}
	if readInitFile(t, folder, ".env") != env || readInitFile(t, folder, "tls/consul/consul.key") != tlsKey || !exists(folder, "certificates/ws-c-yard.zip") {
		t.Fatal("a second share changed this Workstation")
	}
}

func TestWSYardInitOpensThisWorkstation(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.LANIP = "192.168.50.20"
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	r := yard.Run("ws", "yard", "init", "--dir", folder)
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, want := range []string{"Consul address: 192.168.50.20", "TCP 8300", "TCP+UDP 8302", "compose recreate", "yard share"} {
		if !strings.Contains(r.Stdout, want) {
			t.Fatalf("the output lacks %q:\n%s", want, r.Stdout)
		}
	}
	own := readTLSCertificate(t, filepath.Join(folder, "tls", "consul", "consul.crt"))
	if own.VerifyHostname("server.ws-a.consul") != nil || readInitFile(t, folder, "tls/consul/ca.crt") != readInitFile(t, folder, "ca/ca.crt") || !exists(folder, "consul.d/10-tls.hcl") {
		t.Fatal("init did not turn on Consul TLS with the CA of this Workstation")
	}
	if !strings.Contains(readInitFile(t, folder, ".env"), "YARD_BIND_ADDRESS=192.168.50.20\n") {
		t.Fatalf("Consul does not listen on the LAN address:\n%s", readInitFile(t, folder, ".env"))
	}
	assertPrivateWorkstation(t, folder)

	// Running it again changes nothing.
	before := snapshotFolder(t, folder)
	if r := yard.Run("ws", "yard", "init", "--dir", folder); r.ExitCode != 0 || !strings.Contains(r.Stdout, "Already open") {
		t.Fatalf("second init: %+v", r)
	}
	sameFolder(t, before, snapshotFolder(t, folder))
}

func TestWSYardInitWithoutACAInvitesToMakeIt(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.LANIP = "192.168.1.10"
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	before := snapshotFolder(t, folder)
	r := yard.Run("ws", "yard", "init", "--dir", folder)
	if r.ExitCode == 0 || !strings.Contains(r.Stderr, "tls ca create") || !strings.Contains(r.Stderr, "yard init") {
		t.Fatalf("init without a CA: %+v", r)
	}
	sameFolder(t, before, snapshotFolder(t, folder))
}

func TestWSYardInitAddress(t *testing.T) {
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
	before := snapshotFolder(t, folder)
	yard.Process.LANIP, yard.Process.LANError = "", errors.New("no route")
	for _, flags := range [][]string{nil, {"--address", "0.0.0.0"}, {"--address", "::1"}, {"--address", "not-an-ip"}} {
		if r := yard.Run(append([]string{"ws", "yard", "init", "--dir", folder}, flags...)...); r.ExitCode == 0 || !strings.Contains(r.Stderr, "--address") {
			t.Fatalf("%v: %+v", flags, r)
		}
	}
	sameFolder(t, before, snapshotFolder(t, folder))
	if r := yard.Run("ws", "yard", "init", "--dir", folder, "--address", "10.20.30.40"); r.ExitCode != 0 || !strings.Contains(readInitFile(t, folder, ".env"), "YARD_BIND_ADDRESS=10.20.30.40\n") {
		t.Fatalf("explicit address: %+v", r)
	}
}

// A Yard on one computer, such as the test Workstations, listens on loopback addresses.
func TestWSYardOnLoopbackCanShare(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
		{"ws", "yard", "init", "--dir", folder, "--address", "127.0.0.1"},
		{"ws", "yard", "share", "ws-b", "--dir", folder, "--password", "pw"},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatalf("%v: %+v", args, r)
		}
	}
	var invitation struct{ Peer string }
	if err := json.Unmarshal(openInvitationZip(t, filepath.Join(folder, "certificates", "ws-b-yard.zip"), "pw")["yard.json"], &invitation); err != nil || invitation.Peer != "127.0.0.1:8302" {
		t.Fatalf("peer = %q (%v)", invitation.Peer, err)
	}
}

func TestWSYardInitRefusesAMemberOfAnotherYard(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.LANIP = "192.168.1.10"
	folder, other := t.TempDir(), t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
		{"ws", "init", "--dir", other, "--workstation", "ws-x", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", other},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	if err := os.WriteFile(filepath.Join(folder, "consul.d", "10-tls.hcl"), []byte("tls"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(folder, "tls", "consul", "ca.crt"), []byte(readInitFile(t, other, "ca/ca.crt")), 0600); err != nil {
		t.Fatal(err)
	}
	if r := yard.Run("ws", "yard", "init", "--dir", folder); r.ExitCode == 0 || !strings.Contains(r.Stderr, "another Yard") {
		t.Fatalf("init in the Yard of another CA: %+v", r)
	}
}

func TestWSYardShareRefusals(t *testing.T) {
	yard, folder := originWorkstation(t)
	before := snapshotFolder(t, folder)
	for name, tc := range map[string]struct {
		args []string
		want string
	}{
		"no name":   {[]string{"ws", "yard", "share"}, "name of the Workstation that joins"},
		"own name":  {[]string{"ws", "yard", "share", "ws-a"}, "this Workstation"},
		"bad name":  {[]string{"ws", "yard", "share", "Bad Name"}, "invalid Workstation name"},
		"path name": {[]string{"ws", "yard", "share", "../escape"}, "invalid Workstation name"},
		"bad days":  {[]string{"ws", "yard", "share", "ws-b", "--days", "0"}, "--days"},
		"long days": {[]string{"ws", "yard", "share", "ws-b", "--days", "4000"}, "CA validity"},
		"address":   {[]string{"ws", "yard", "share", "ws-b", "--address", "10.0.0.1"}, "unknown flag"},
	} {
		r := yard.Run(append(tc.args, "--dir", folder, "--password", "pw")...)
		if r.ExitCode == 0 || !strings.Contains(r.Stderr, tc.want) {
			t.Fatalf("%s: %+v", name, r)
		}
	}
	sameFolder(t, before, snapshotFolder(t, folder))
}

// share signs for another name and changes nothing else. ws yard init opens the Workstation first.
func TestWSYardShareNeedsAWorkstationThatYardInitOpened(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.LANIP = "192.168.1.10"
	folder := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", folder},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	before := snapshotFolder(t, folder)
	r := yard.Run("ws", "yard", "share", "ws-b", "--dir", folder, "--password", "pw")
	if r.ExitCode == 0 || !strings.Contains(r.Stderr, "yard init") {
		t.Fatalf("share before init: %+v", r)
	}
	sameFolder(t, before, snapshotFolder(t, folder))
}

func TestWSYardShareRefusesAWorkstationOfAnotherYard(t *testing.T) {
	yard, folder := originWorkstation(t)
	other := t.TempDir()
	for _, args := range [][]string{
		{"ws", "init", "--dir", other, "--workstation", "ws-x", "--base-domain", "lvh.me"},
		{"ws", "tls", "ca", "create", "--dir", other},
	} {
		if r := yard.Run(args...); r.ExitCode != 0 {
			t.Fatal(r)
		}
	}
	// ws-a trusts the CA of ws-x for Consul, as a member of the Yard of ws-x does.
	if err := os.WriteFile(filepath.Join(folder, "consul.d", "10-tls.hcl"), []byte("tls"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(folder, "tls", "consul", "ca.crt"), []byte(readInitFile(t, other, "ca/ca.crt")), 0600); err != nil {
		t.Fatal(err)
	}
	r := yard.Run("ws", "yard", "share", "ws-b", "--dir", folder, "--password", "pw")
	if r.ExitCode == 0 || !strings.Contains(r.Stderr, "another Yard") || exists(folder, "certificates/ws-b-yard.zip") {
		t.Fatalf("shared from a member of another Yard: %+v", r)
	}
}

func TestWSYardShareAsksForTheNameAndHidesThePassword(t *testing.T) {
	yard, folder := originWorkstation(t)
	yard.Process.Terminal = true
	// A name that is wrong is asked again, then the validity, then the password twice.
	yard.Process.Stdin = "Bad Name\nws-b\n\nzip-pass\nzip-pass\n"
	r := yard.Run("ws", "yard", "share", "--dir", folder)
	if r.ExitCode != 0 || !strings.Contains(r.Stdout, "invalid Workstation name") || yard.Process.SecretReads != 2 || strings.Contains(r.Stdout, "Password of the zip file: ") {
		t.Fatalf("%d hidden reads: %+v", yard.Process.SecretReads, r)
	}
	openInvitationZip(t, filepath.Join(folder, "certificates", "ws-b-yard.zip"), "zip-pass")
}

// share signs, so it needs the password of ca.key as ws tls ca issue does.
func TestWSYardShareOpensACAKeyThatHasAPassword(t *testing.T) {
	yard, folder := originWorkstationWith(t, "--ca-key-password", "hunter2")
	share := []string{"ws", "yard", "share", "ws-b", "--dir", folder, "--password", "zip-pass"}
	if r := yard.Run(share...); r.ExitCode == 0 || !strings.Contains(r.Stderr, "--ca-key-password") {
		t.Fatalf("no password: %+v", r)
	}
	if r := yard.Run(append(share, "--ca-key-password", "wrong")...); r.ExitCode == 0 || !strings.Contains(r.Stderr, "wrong password") {
		t.Fatalf("wrong password: %+v", r)
	}
	if exists(folder, "certificates/ws-b-yard.zip") {
		t.Fatal("a refused share left an invitation")
	}
	if r := yard.Run(append(share, "--ca-key-password", "hunter2")...); r.ExitCode != 0 {
		t.Fatalf("right password: %+v", r)
	}
	openInvitationZip(t, filepath.Join(folder, "certificates", "ws-b-yard.zip"), "zip-pass")

	yard.Process.Terminal = true
	yard.Process.Stdin = "hunter2\n"
	r := yard.Run("ws", "yard", "share", "ws-c", "--dir", folder, "--password", "zip-pass", "--days", "30")
	if r.ExitCode != 0 || !strings.Contains(r.Stdout, "Password of the CA key") || yard.Process.SecretReads != 1 {
		t.Fatalf("question: %d hidden reads: %+v", yard.Process.SecretReads, r)
	}
}

// joiningWorkstation makes ws-b, which listens on 192.168.1.20.
func joiningWorkstation(t *testing.T, yard *commandtest.Harness, name string) string {
	t.Helper()
	folder := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", folder, "--workstation", name, "--base-domain", "lvh.me"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	return folder
}

func TestWSYardJoinInstallsTheInvitation(t *testing.T) {
	yard, origin := originWorkstation(t)
	if r := yard.Run("ws", "yard", "share", "ws-b", "--dir", origin, "--password", "pw"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	zip := filepath.Join(origin, "certificates", "ws-b-yard.zip")
	yard.Process.LANIP = "192.168.1.20"
	joining := joiningWorkstation(t, yard, "ws-b")
	if runtime.GOOS != "windows" && !strings.Contains(readInitFile(t, joining, ".env"), "YARD_BIND_ADDRESS=127.0.0.1\n") {
		t.Fatal("a new Workstation should listen on this computer only")
	}
	r := yard.Run("ws", "yard", "join", zip, "--dir", joining, "--password", "pw")
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	for _, want := range []string{"Joined the Yard of ws-a", "192.168.1.10:8302", "TCP 8300", "compose recreate"} {
		if !strings.Contains(r.Stdout, want) {
			t.Fatalf("the output lacks %q:\n%s", want, r.Stdout)
		}
	}
	env := readInitFile(t, joining, ".env")
	for _, want := range []string{"YARD_GOSSIP_KEY=" + yardTestKey + "\n", "YARD_PEERS=192.168.1.10:8302\n", "YARD_BIND_ADDRESS=192.168.1.20\n"} {
		if !strings.Contains(env, want) {
			t.Fatalf(".env lacks %q:\n%s", want, env)
		}
	}
	files := openInvitationZip(t, zip, "pw")
	for name, from := range map[string]string{"tls/consul/ca.crt": "consul/ca.crt", "tls/consul/consul.crt": "consul/consul.crt", "tls/consul/consul.key": "consul/consul.key"} {
		if readInitFile(t, joining, name) != string(files[from]) {
			t.Fatalf("%s is not %s of the invitation", name, from)
		}
	}
	if !exists(joining, "consul.d/10-tls.hcl") {
		t.Fatal("Consul TLS is not on")
	}
	assertPrivateWorkstation(t, joining)
	if r := yard.Run("ws", "yard", "join", zip, "--dir", joining, "--password", "pw"); r.ExitCode != 0 {
		t.Fatalf("a second join with the same invitation failed: %+v", r)
	}

	// Standalone undoes it.
	if r := yard.Run("ws", "yard", "standalone", "--dir", joining); r.ExitCode != 0 || !strings.Contains(r.Stdout, "standalone") {
		t.Fatalf("standalone: %+v", r)
	}
	env = readInitFile(t, joining, ".env")
	if strings.Contains(env, "YARD_GOSSIP_KEY="+yardTestKey) || !strings.Contains(env, "YARD_GOSSIP_KEY=") || strings.Contains(env, "192.168.1.10") {
		t.Fatalf("standalone kept the Yard's Gossip Key or Peer:\n%s", env)
	}
	if runtime.GOOS != "windows" && !strings.Contains(env, "YARD_BIND_ADDRESS=127.0.0.1\n") {
		t.Fatalf("Consul still listens on the LAN:\n%s", env)
	}
	if exists(joining, "consul.d/10-tls.hcl") || exists(joining, "tls/consul/consul.key") || exists(joining, "tls/consul/consul.crt") || exists(joining, "tls/consul/ca.crt") || !exists(joining, "tls/consul") {
		t.Fatal("standalone left Consul TLS files, or removed the folder that Compose mounts")
	}
	if r := yard.Run("ws", "yard", "standalone", "--dir", joining); r.ExitCode != 0 {
		t.Fatalf("standalone twice: %+v", r)
	}
	assertPrivateWorkstation(t, joining)
}

func TestWSYardJoinRefusals(t *testing.T) {
	yard, origin := originWorkstation(t)
	if r := yard.Run("ws", "yard", "share", "ws-b", "--dir", origin, "--password", "pw"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	zip := filepath.Join(origin, "certificates", "ws-b-yard.zip")
	yard.Process.LANIP = "192.168.1.20"
	joining := joiningWorkstation(t, yard, "ws-b")
	wrongName := joiningWorkstation(t, yard, "ws-c")
	before := snapshotFolder(t, joining)
	for name, tc := range map[string]struct {
		args []string
		want string
	}{
		"wrong password": {[]string{"--dir", joining, "--password", "nope"}, "wrong password"},
		"no password":    {[]string{"--dir", joining}, "--password"},
		"another name":   {[]string{"--dir", wrongName, "--password", "pw"}, "for ws-b"},
	} {
		r := yard.Run(append([]string{"ws", "yard", "join", zip}, tc.args...)...)
		if r.ExitCode == 0 || !strings.Contains(r.Stderr, tc.want) {
			t.Fatalf("%s: %+v", name, r)
		}
	}
	notInvitation := filepath.Join(t.TempDir(), "other.zip")
	data, err := certzip.Build([]certzip.File{{Name: "notes.txt", Data: []byte("x")}}, "")
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(notInvitation, data, 0600); err != nil {
		t.Fatal(err)
	}
	if r := yard.Run("ws", "yard", "join", notInvitation, "--dir", joining); r.ExitCode == 0 || !strings.Contains(r.Stderr, "not a Yard Invitation") {
		t.Fatalf("a zip file that is no invitation: %+v", r)
	}
	if r := yard.Run("ws", "yard", "join", filepath.Join(t.TempDir(), "none.zip"), "--dir", joining); r.ExitCode == 0 {
		t.Fatal("joined with a file that does not exist")
	}
	yard.Process.LANIP, yard.Process.LANError = "", errors.New("no route")
	if r := yard.Run("ws", "yard", "join", zip, "--dir", joining, "--password", "pw"); r.ExitCode == 0 || !strings.Contains(r.Stderr, "--address") {
		t.Fatalf("no LAN address: %+v", r)
	}
	sameFolder(t, before, snapshotFolder(t, joining))
}

func TestWSYardJoinAsksForThePasswordAndRefusesAnotherYard(t *testing.T) {
	yard, origin := originWorkstation(t)
	if r := yard.Run("ws", "yard", "share", "ws-b", "--dir", origin, "--password", "pw"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	zip := filepath.Join(origin, "certificates", "ws-b-yard.zip")
	yard.Process.LANIP = "192.168.1.20"
	joining := joiningWorkstation(t, yard, "ws-b")
	yard.Process.Terminal = true
	yard.Process.Stdin = "wrong\npw\n"
	if r := yard.Run("ws", "yard", "join", zip, "--dir", joining); r.ExitCode != 0 || !strings.Contains(r.Stdout, "The password is wrong.") || yard.Process.SecretReads != 2 {
		t.Fatalf("%d hidden reads: %+v", yard.Process.SecretReads, r)
	}

	// A Workstation that trusts the CA of another Yard leaves it first.
	yard.Process.Terminal = false
	otherOrigin, other := originWorkstation(t)
	_ = otherOrigin
	if err := os.WriteFile(filepath.Join(other, "consul.d", "10-tls.hcl"), []byte("tls"), 0600); err != nil {
		t.Fatal(err)
	}
	ws := t.TempDir()
	if r := yard.Run("ws", "init", "--dir", ws, "--workstation", "ws-b", "--base-domain", "lvh.me"); r.ExitCode != 0 {
		t.Fatal(r)
	}
	if err := os.WriteFile(filepath.Join(ws, "consul.d", "10-tls.hcl"), []byte("tls"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(ws, "tls", "consul", "ca.crt"), []byte(readInitFile(t, other, "ca/ca.crt")), 0600); err != nil {
		t.Fatal(err)
	}
	if r := yard.Run("ws", "yard", "join", zip, "--dir", ws, "--password", "pw"); r.ExitCode == 0 || !strings.Contains(r.Stderr, "another Yard") {
		t.Fatalf("joined a second Yard: %+v", r)
	}
}

func TestInitListensOnThisComputerOnly(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("native Windows needs the LAN address at setup")
	}
	for _, tc := range []struct {
		flags []string
		want  string
	}{
		{nil, "YARD_BIND_ADDRESS=127.0.0.1\n"},
		{[]string{"--address", "192.168.1.10"}, "YARD_BIND_ADDRESS=192.168.1.10\n"},
	} {
		folder := t.TempDir()
		if r := commandtest.New(t).Run(append([]string{"ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"}, tc.flags...)...); r.ExitCode != 0 {
			t.Fatal(r)
		}
		if !strings.Contains(readInitFile(t, folder, ".env"), tc.want) {
			t.Fatalf("%v: .env lacks %q:\n%s", tc.flags, tc.want, readInitFile(t, folder, ".env"))
		}
	}
}

// snapshotFolder lists every path of a folder with its content, so that a test can prove that a
// command left the folder as it was.
func snapshotFolder(t *testing.T, folder string) map[string]string {
	t.Helper()
	files := map[string]string{}
	err := filepath.WalkDir(folder, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		rel, _ := filepath.Rel(folder, path)
		if entry.IsDir() {
			files[rel] = "<dir>"
			return nil
		}
		content, err := os.ReadFile(path)
		files[rel] = string(content)
		return err
	})
	if err != nil {
		t.Fatal(err)
	}
	return files
}

func sameFolder(t *testing.T, before, after map[string]string) {
	t.Helper()
	for path, content := range after {
		if old, ok := before[path]; !ok || old != content {
			t.Fatalf("%s was added or changed", path)
		}
	}
	for path := range before {
		if _, ok := after[path]; !ok {
			t.Fatalf("%s was removed", path)
		}
	}
}
