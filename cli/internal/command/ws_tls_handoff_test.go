package command_test

import (
	"bytes"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

// snapshot lists every path in a folder with its content, so a test can prove that a command left
// the folder as it was.
func snapshot(t *testing.T, folder string) map[string]string {
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

func sameSnapshot(t *testing.T, before, after map[string]string) {
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

func commandLines(yard *commandtest.Harness) []string {
	var lines []string
	for _, command := range yard.Process.Commands {
		lines = append(lines, command.Name+" "+strings.Join(command.Args, " "))
	}
	return lines
}

func TestWindowsSenderRejectsScpBeforeAnyTransferOrChange(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	yard.Process.System = "windows"
	before := snapshot(t, folder)

	r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", "me@ws-b.lan")

	if r.ExitCode == 0 {
		t.Fatalf("native Windows accepted --scp: %+v", r)
	}
	for _, want := range []string{"--scp", "Windows", "without --scp", "tls import", "ca.key", "Nothing was changed"} {
		if !strings.Contains(r.Stderr, want) {
			t.Fatalf("the error lacks %q:\n%s", want, r.Stderr)
		}
	}
	if len(yard.Process.Commands) != 0 {
		t.Fatalf("ran a command: %v", commandLines(yard))
	}
	sameSnapshot(t, before, snapshot(t, folder))
}

func TestWindowsSenderStillRejectsScpWhenTheNameIsGivenByPrompt(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	yard.Process.System = "windows"
	yard.Process.Terminal = true
	yard.Process.Stdin = "ws-b\ny\nn\n"
	before := snapshot(t, folder)

	r := yard.Run("ws", "tls", "ca", "install", "--dir", folder, "--scp", "me@ws-b.lan")

	if r.ExitCode == 0 || len(yard.Process.Commands) != 0 {
		t.Fatalf("native Windows accepted --scp: %+v %v", r, commandLines(yard))
	}
	sameSnapshot(t, before, snapshot(t, folder))
}

func TestWindowsSenderDoesNotOfferScpAndExplainsTheManualWay(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	yard.Process.System = "windows"
	yard.Process.Terminal = true
	// The Workstation name, then y for Consul and n for Traefik. There is no scp question.
	yard.Process.Stdin = "ws-b\ny\nn\n"

	r := yard.Run("ws", "tls", "ca", "install", "--dir", folder)

	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	if strings.Contains(r.Stdout, "with scp?") || strings.Contains(r.Stdout, "run this command again with --scp") {
		t.Fatalf("offered a transfer that native Windows cannot do:\n%s", r.Stdout)
	}
	if len(yard.Process.Commands) != 0 {
		t.Fatalf("ran a command: %v", commandLines(yard))
	}
	for _, want := range []string{"never send ca.key", "If ws-b runs on Windows", "tls import consul", "Windows cannot copy"} {
		if !strings.Contains(r.Stdout, want) {
			t.Fatalf("the manual steps lack %q:\n%s", want, r.Stdout)
		}
	}
	bundle := filepath.Join(folder, "certificates", "ws-b")
	for _, path := range []string{"consul/ca.crt", "consul/consul.crt", "consul/consul.key"} {
		if _, err := os.Stat(filepath.Join(bundle, path)); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := os.Stat(filepath.Join(bundle, "traefik")); err == nil {
		t.Fatal("copied Traefik, which was not chosen")
	}
}

func TestHandoffBundleNeverHoldsTheYardCAKey(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	if r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	caKey, err := os.ReadFile(filepath.Join(folder, "ca", "ca.key"))
	if err != nil {
		t.Fatal(err)
	}
	var names []string
	_ = filepath.WalkDir(filepath.Join(folder, "certificates", "ws-b"), func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return err
		}
		content, _ := os.ReadFile(path)
		if bytes.Contains(content, caKey) || entry.Name() == "ca.key" {
			t.Fatalf("%s holds the Yard CA key", path)
		}
		rel, _ := filepath.Rel(folder, path)
		names = append(names, filepath.ToSlash(rel))
		return nil
	})
	want := "certificates/ws-b/consul/ca.crt certificates/ws-b/consul/consul.crt certificates/ws-b/consul/consul.key certificates/ws-b/traefik/ca.crt certificates/ws-b/traefik/traefik.crt certificates/ws-b/traefik/traefik.key"
	if strings.Join(names, " ") != want {
		t.Fatalf("the bundle holds %v", names)
	}
}

func TestUnixSenderFindsAWindowsDestinationBeforeAnyPreparation(t *testing.T) {
	for _, tc := range []struct {
		name   string
		answer func(stdout io.Writer) int
	}{
		// Windows OpenSSH runs cmd.exe, which does not know uname.
		{"cmd.exe does not know uname", func(io.Writer) int { return 1 }},
		{"an MSYS uname", func(stdout io.Writer) int { _, _ = io.WriteString(stdout, "MINGW64_NT-10.0-26300\n"); return 0 }},
	} {
		t.Run(tc.name, func(t *testing.T) {
			yard := commandtest.New(t)
			folder := signedYard(t, yard)
			yard.Process.RunCommand = func(name string, args []string, dir string, stdout, stderr io.Writer) (int, error) {
				return tc.answer(stdout), nil
			}
			before := snapshot(t, folder)

			r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", "me@win.lan")

			if r.ExitCode == 0 {
				t.Fatalf("accepted a Windows destination: %+v", r)
			}
			for _, want := range []string{"Windows", "tls import", "Nothing was changed"} {
				if !strings.Contains(r.Stderr, want) {
					t.Fatalf("the error lacks %q:\n%s", want, r.Stderr)
				}
			}
			if got := commandLines(yard); len(got) != 1 || got[0] != "ssh me@win.lan uname -s" {
				t.Fatalf("only the read-only probe may run, got %v", got)
			}
			sameSnapshot(t, before, snapshot(t, folder))
		})
	}
}

func TestUnixSenderRejectsAWindowsFolderWithoutAnyRemoteCommand(t *testing.T) {
	for _, target := range []string{`me@win.lan:C:\Users\me\.config\lvh-yard`, "me@win.lan:C:/Users/me/lvh-yard", `me@win.lan:d:\yard`, `me@win.lan:..\yard`} {
		yard := commandtest.New(t)
		folder := signedYard(t, yard)
		before := snapshot(t, folder)

		r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", target)

		if r.ExitCode == 0 || !strings.Contains(r.Stderr, "Windows") || len(yard.Process.Commands) != 0 {
			t.Fatalf("%q: %+v %v", target, r, commandLines(yard))
		}
		sameSnapshot(t, before, snapshot(t, folder))
	}
}

func TestUnixSenderDoesNotCallASSHFailureWindows(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	yard.Process.RunCommand = func(name string, args []string, dir string, stdout, stderr io.Writer) (int, error) {
		_, _ = io.WriteString(stderr, "ssh: connect to host me.lan port 22: Connection refused\n")
		return 255, nil
	}

	r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", "me@ws-b.lan")

	if r.ExitCode == 0 || !strings.Contains(r.Stderr, "ssh failed with exit code 255") || strings.Contains(r.Stderr, "Windows") {
		t.Fatalf("a failed connection was misreported: %+v", r)
	}
	if !strings.Contains(r.Stderr, "Connection refused") {
		t.Fatalf("the ssh message was lost:\n%s", r.Stderr)
	}
	if got := commandLines(yard); len(got) != 1 {
		t.Fatalf("went on after a failed connection: %v", got)
	}
}

func TestUnixToUnixTransferProbesThenPreparesThenCopies(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	yard.Process.RunCommand = func(name string, args []string, dir string, stdout, stderr io.Writer) (int, error) {
		if name == "ssh" && args[len(args)-1] == "uname -s" {
			_, _ = io.WriteString(stdout, "Linux\n")
		}
		return 0, nil
	}

	r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", "me@ws-b.lan:/srv/yard")

	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	bundle := filepath.Join(folder, "certificates", "ws-b")
	want := []string{
		"ssh me@ws-b.lan uname -s",
		"ssh me@ws-b.lan umask 077 && mkdir -p '/srv/yard/tls'",
		"scp -r " + filepath.Join(bundle, "consul") + " " + filepath.Join(bundle, "traefik") + " me@ws-b.lan:/srv/yard/tls/",
	}
	if got := commandLines(yard); strings.Join(got, "\n") != strings.Join(want, "\n") {
		t.Fatalf("commands\n%s\nwant\n%s", strings.Join(got, "\n"), strings.Join(want, "\n"))
	}
	if strings.Contains(r.Stdout, "Linux") {
		t.Fatalf("the probe output leaked into the output:\n%s", r.Stdout)
	}
}

func TestInvalidScpTargetsLeaveTheFolderAsItWas(t *testing.T) {
	for _, bad := range []string{"-oProxyCommand=x", "me@host:~/yard", "me@host:it's", "me@host:a b"} {
		yard := commandtest.New(t)
		folder := signedYard(t, yard)
		before := snapshot(t, folder)

		r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder, "--scp", bad)

		if r.ExitCode == 0 || len(yard.Process.Commands) != 0 {
			t.Fatalf("%q: %+v %v", bad, r, commandLines(yard))
		}
		sameSnapshot(t, before, snapshot(t, folder))
	}
}

func TestYardInvitationUnixTextAndWindowsManualWay(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	if r := yard.Run("ws", "tls", "ca", "install", "--dir", folder); r.ExitCode != 0 {
		t.Fatal(r)
	}
	yard.Process.System = "linux"
	unix := yard.Run("ws", "yard", "share", "--dir", folder)
	if unix.ExitCode != 0 {
		t.Fatal(unix)
	}
	if !strings.Contains(unix.Stdout, "      lvh-yard ws tls ca install '<new-workstation>' --scp '<user@host>'\n   With a company CA, ask its issuer for the certificate instead.\n") {
		t.Fatalf("the Unix invitation changed:\n%s", unix.Stdout)
	}
	if strings.Contains(unix.Stdout, "A Windows computer") {
		t.Fatalf("the Unix invitation names Windows:\n%s", unix.Stdout)
	}

	yard.Process.System = "windows"
	windows := yard.Run("ws", "yard", "share", "--dir", folder)
	if windows.ExitCode != 0 {
		t.Fatal(windows)
	}
	for _, want := range []string{
		"ca install '<new-workstation>'\n", "never send ca.key", "ws tls import consul",
		"From a Unix computer to a Unix computer, add --scp", "Windows cannot copy with --scp",
	} {
		if !strings.Contains(windows.Stdout, want) {
			t.Fatalf("the Windows invitation lacks %q:\n%s", want, windows.Stdout)
		}
	}
}

func TestUnixCAInstallOutputKeepsItsWording(t *testing.T) {
	yard := commandtest.New(t)
	folder := signedYard(t, yard)
	yard.Process.System = "linux"
	r := yard.Run("ws", "tls", "ca", "install", "ws-b", "--dir", folder)
	if r.ExitCode != 0 {
		t.Fatal(r)
	}
	if !strings.Contains(r.Stdout, "To do this from here, run this command again with --scp <user@host>, or --scp <user@host>:<its-folder>.\n\nThen, on ws-b, turn on") {
		t.Fatalf("the Unix wording changed:\n%s", r.Stdout)
	}
	for _, unwanted := range []string{"Windows", "Use --scp only"} {
		if strings.Contains(r.Stdout, unwanted) {
			t.Fatalf("the Unix output holds %q:\n%s", unwanted, r.Stdout)
		}
	}
}
