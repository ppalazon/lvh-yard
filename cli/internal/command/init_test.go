package command_test

import (
	"encoding/base64"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
)

func TestInitWritesNewWorkstationFolder(t *testing.T) {
	yard := commandtest.New(t)
	folder := filepath.Join(t.TempDir(), "yard")
	yard.Process.Identity = func(flag string) string {
		if flag == "-u" {
			return "1234"
		}
		return "2345"
	}
	run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me", "--docker-socket", filepath.Join(t.TempDir(), "missing"))
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	env := readInitFile(t, folder, ".env")
	uid, gid := "YARD_UID=1234", "YARD_GID=2345"
	if runtime.GOOS == "windows" {
		// The Windows Compose file fixes the user at 1000:1000, so .env keeps these settings commented out.
		uid, gid = "#YARD_UID=1000", "#YARD_GID=1000"
	}
	for _, setting := range []string{"YARD_WORKSTATION=ws-a", "YARD_BASE_DOMAIN=lvh.me", "YARD_PEERS=", "COMPOSE_PROFILES=dashboard,traefik", "YARD_TRAEFIK_ENABLED=true", uid, gid} {
		if !strings.Contains(env, "\n"+setting+"\n") {
			t.Errorf("missing %s in .env", setting)
		}
	}
	compose := readInitFile(t, folder, "compose.yaml")
	if strings.Contains(compose, "    build: .\n") || !strings.Contains(compose, "ghcr.io/ppalazon/lvh-yard-dashboard:latest}") || !strings.Contains(compose, "./backups:") {
		t.Fatalf("wrong compose template: %s", compose)
	}
	if runtime.GOOS == "windows" {
		// NTFS cannot hold the Consul owner, so Windows uses a Docker volume that a helper service prepares.
		if !strings.Contains(compose, "    user: \"1000:1000\"\n") ||
			!strings.Contains(compose, "      - consul-data:/consul/data\n") ||
			!strings.Contains(compose, "\nvolumes:\n  consul-data:") {
			t.Fatalf("Consul must use user 1000 and the Docker volume: %s", compose)
		}
	} else if !strings.Contains(compose, `user: "${YARD_UID:?set YARD_UID in .env}:${YARD_GID:?set YARD_GID in .env}"`) ||
		!strings.Contains(compose, "      - consul-data:/consul/data\n") ||
		!strings.Contains(compose, "\nvolumes:\n  consul-data:") ||
		strings.Contains(compose, "./consul-data") {
		t.Fatalf("Consul must use the Workstation user and the Docker volume: %s", compose)
	}
	info, err := os.Stat(filepath.Join(folder, "backups"))
	if err != nil || !info.IsDir() {
		t.Fatalf("backups folder: %v", err)
	}
	info, err = os.Stat(filepath.Join(folder, ".env"))
	if err != nil || (commandtest.UnixModes && info.Mode().Perm() != 0600) {
		t.Fatalf(".env permissions: %v, %v", info, err)
	}
	settings := readInitFile(t, folder, "lvh-yard.yaml")
	for line := range strings.SplitSeq(strings.TrimSuffix(settings, "\n"), "\n") {
		if !strings.HasPrefix(line, "#") {
			t.Fatalf("dashboard setting is not commented: %q", line)
		}
	}
	info, err = os.Stat(filepath.Join(folder, "lvh-yard.yaml"))
	if err != nil || (commandtest.UnixModes && info.Mode().Perm() != 0600) {
		t.Fatalf("lvh-yard.yaml permissions: %v, %v", info, err)
	}
	if !strings.Contains(compose, "./lvh-yard.yaml:/app/config/application.yaml:ro") {
		t.Fatalf("dashboard settings are not mounted read-only: %s", compose)
	}
	if _, err := os.Stat(filepath.Join(folder, "route-profiles.yaml")); !os.IsNotExist(err) {
		t.Fatalf("unexpected Route Profiles file: %v", err)
	}
	if runtime.GOOS == "windows" {
		// The Windows Compose file fixes the Docker group at 0, so .env leaves the setting out and there is nothing to warn about.
		if strings.Contains(run.Stderr, "YARD_DOCKER_GID") || !strings.Contains(env, "\n#YARD_DOCKER_GID=\n") {
			t.Fatalf("unexpected Docker group handling: %+v", run)
		}
	} else if !strings.Contains(run.Stderr, "YARD_DOCKER_GID") {
		t.Fatalf("missing Docker group warning: %+v", run)
	}
}

// windowsLANAnswer accepts the offered address at the extra prompt that only Windows shows.
var windowsLANAnswer = func() string {
	if runtime.GOOS == "windows" {
		return "\n"
	}
	return ""
}()

func readInitFile(t *testing.T, folder, name string) string {
	t.Helper()
	text, err := os.ReadFile(filepath.Join(folder, name))
	if err != nil {
		t.Fatal(err)
	}
	return string(text)
}

func TestInitPinsDashboardToBuildVersion(t *testing.T) {
	for _, version := range []string{"dev", "0.2.0", "0.3.0-rc.1"} {
		t.Run(version, func(t *testing.T) {
			yard := commandtest.New(t)
			yard.Version = version
			folder := t.TempDir()
			run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a")
			if run.ExitCode != 0 {
				t.Fatalf("init: %+v", run)
			}
			tag := version
			if tag == "dev" {
				tag = "latest"
			}
			if !strings.Contains(readInitFile(t, folder, "compose.yaml"), "ghcr.io/ppalazon/lvh-yard-dashboard:"+tag+"}") {
				t.Fatalf("dashboard does not use CLI version %s", version)
			}
		})
	}
}

func TestInitPreservesTemplateCommentsAndOrder(t *testing.T) {
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a")
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	// A Windows test binary does not run in the source folder, and Windows has its own template.
	template := templates.Env()
	if runtime.GOOS != "windows" {
		root, err := os.ReadFile(filepath.Join("..", "..", "..", "config", ".env.example"))
		if err != nil {
			t.Fatal(err)
		}
		template = string(root)
	}
	want := strings.Split(template, "\n")
	got := strings.Split(readInitFile(t, folder, ".env"), "\n")
	if len(got) != len(want) {
		t.Fatalf("line count: %d, want %d", len(got), len(want))
	}
	for i, line := range want {
		key, _, setting := strings.Cut(strings.TrimPrefix(line, "#"), "=")
		if setting && !strings.ContainsAny(key, " \t") {
			if !strings.HasPrefix(strings.TrimPrefix(got[i], "#"), key+"=") {
				t.Errorf("setting moved at line %d: %s", i+1, got[i])
			}
		} else if got[i] != line {
			t.Errorf("comment changed at line %d: %q, want %q", i+1, got[i], line)
		}
	}
}

func TestInitFlagsSetWorkstationSettings(t *testing.T) {
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-b", "--base-domain", "localhost")
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	env := readInitFile(t, folder, ".env")
	for _, value := range []string{"YARD_WORKSTATION=ws-b", "YARD_BASE_DOMAIN=localhost", "YARD_PEERS="} {
		if !strings.Contains(env, "\n"+value+"\n") {
			t.Errorf("missing setting %s", value)
		}
	}
}

// A Yard is joined with ws yard join, so ws init has no flag for its Gossip Key or its Peers.
func TestInitHasNoFlagsForTheYard(t *testing.T) {
	for _, flag := range []string{"--peers", "--gossip-key"} {
		folder := filepath.Join(t.TempDir(), "not-created")
		run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", flag, "x")
		if run.ExitCode == 0 || !strings.Contains(run.Stderr, "unknown flag") {
			t.Fatalf("%s: %+v", flag, run)
		}
	}
}

func TestInitGeneratesNewGossipKeys(t *testing.T) {
	yard := commandtest.New(t)
	folder := t.TempDir()
	var previous string
	for _, args := range [][]string{nil, {"--force"}} {
		run := yard.Run(append([]string{"ws", "init", "--dir", folder, "--workstation", "ws-a"}, args...)...)
		if run.ExitCode != 0 {
			t.Fatalf("init: %+v", run)
		}
		env := readInitFile(t, folder, ".env")
		_, after, _ := strings.Cut(env, "\nYARD_GOSSIP_KEY=")
		key, _, _ := strings.Cut(after, "\n")
		decoded, err := base64.StdEncoding.DecodeString(key)
		if err != nil || len(decoded) != 32 || key == previous {
			t.Fatalf("new gossip key %q: %v", key, err)
		}
		previous = key
	}
}

func TestInitRefusesOverwriteBeforeWritingAnything(t *testing.T) {
	for _, existing := range []string{"compose.yaml", ".env", "lvh-yard.yaml"} {
		t.Run(existing, func(t *testing.T) {
			folder := t.TempDir()
			if err := os.WriteFile(filepath.Join(folder, existing), []byte("mine\n"), 0644); err != nil {
				t.Fatal(err)
			}
			run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a")
			if run.ExitCode != 1 || !strings.Contains(run.Stderr, "--force") {
				t.Fatalf("overwrite refused: %+v", run)
			}
			if got := readInitFile(t, folder, existing); got != "mine\n" {
				t.Fatalf("overwritten file: %q", got)
			}
			entries, err := os.ReadDir(folder)
			if err != nil || len(entries) != 1 {
				t.Fatalf("partial writes: %v, %v", entries, err)
			}
		})
	}
}

func TestInitForceReplacesFilesAndProtectsExistingEnv(t *testing.T) {
	folder := t.TempDir()
	for _, name := range []string{"compose.yaml", ".env"} {
		if err := os.WriteFile(filepath.Join(folder, name), []byte("mine\n"), 0644); err != nil {
			t.Fatal(err)
		}
	}
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--force")
	if run.ExitCode != 0 || !strings.Contains(readInitFile(t, folder, ".env"), "YARD_WORKSTATION=ws-a\n") || !strings.Contains(readInitFile(t, folder, "compose.yaml"), "services:") {
		t.Fatalf("forced init: %+v", run)
	}
	info, err := os.Stat(filepath.Join(folder, ".env"))
	if err != nil || (commandtest.UnixModes && info.Mode().Perm() != 0600) {
		t.Fatalf("forced .env permissions: %v, %v", info, err)
	}
}

func TestInitInputErrorsWriteNothing(t *testing.T) {
	for _, args := range [][]string{
		{},
		{"--workstation", "WS_A"},
		{"--workstation", ""},
		{"--workstation", "-bad"},
		{"--workstation", strings.Repeat("a", 64)},
		{"--workstation", "ws-a", "--base-domain", "Not A Domain"},
		{"--workstation", "ws-a", "--base-domain", ""},
		{"--force", "--upgrade"},
		{"--force", "--unknown"},
		{"--upgrade", "--unknown"},
	} {
		t.Run(strings.Join(args, " "), func(t *testing.T) {
			folder := filepath.Join(t.TempDir(), "not-created")
			run := commandtest.New(t).Run(append([]string{"ws", "init", "--dir", folder}, args...)...)
			if run.ExitCode != 1 || run.Stderr == "" {
				t.Fatalf("invalid input: %+v", run)
			}
			if _, err := os.Stat(folder); !os.IsNotExist(err) {
				t.Fatalf("init wrote before validation: %v", err)
			}
		})
	}
}

func TestInitUsesWorkstationFolderLookup(t *testing.T) {
	for _, setting := range []string{"HOME", "config root", "YARD_CONFIG_DIR", "dir"} {
		t.Run(setting, func(t *testing.T) {
			yard := commandtest.New(t)
			base := t.TempDir()
			want := commandtest.UseHome(yard.Process.Env, base)
			args := []string{"ws", "init", "--workstation", "ws-a"}
			switch setting {
			case "config root":
				want = commandtest.UseConfigRoot(yard.Process.Env, base)
			case "YARD_CONFIG_DIR":
				yard.Process.Env[setting] = base
				want = base
			case "dir":
				yard.Process.Env["YARD_CONFIG_DIR"] = filepath.Join(base, "ignored")
				args = append(args, "--dir", base)
				want = base
			}
			run := yard.Run(args...)
			if run.ExitCode != 0 {
				t.Fatalf("folder lookup: %+v", run)
			}
			readInitFile(t, want, "compose.yaml")
			readInitFile(t, want, ".env")
		})
	}
}

func TestInitPromptsOnlyForWorkstationAndBaseDomain(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.Terminal = true
	yard.Process.Stdin = "ws-a\nexample.test\n" + windowsLANAnswer
	folder := t.TempDir()
	run := yard.Run("ws", "init", "--dir", folder)
	if run.ExitCode != 0 || !strings.Contains(run.Stdout, "Workstation name") || !strings.Contains(run.Stdout, "Base Domain [lvh.me]: ") || strings.Contains(run.Stdout, "Peers") {
		t.Fatalf("prompts: %+v", run)
	}
	env := readInitFile(t, folder, ".env")
	for _, setting := range []string{"YARD_WORKSTATION=ws-a", "YARD_BASE_DOMAIN=example.test", "YARD_PEERS="} {
		if !strings.Contains(env, "\n"+setting+"\n") {
			t.Errorf("missing setting %s", setting)
		}
	}
}

func TestInitPromptDefaultsUseHostnameAndBaseDomain(t *testing.T) {
	for _, host := range []struct{ given, want string }{
		{"Pablo_Laptop.local\n", "pablo-laptop"},
		{"-dev_box-", "dev-box"},
		{strings.Repeat("a", 62) + "-b", strings.Repeat("a", 62)},
	} {
		t.Run(host.given, func(t *testing.T) {
			yard := commandtest.New(t)
			yard.Process.Stdin = "\n\n" + windowsLANAnswer
			folder := t.TempDir()
			run := yard.Run("ws", "init", "--dir", folder, "--terminal", "--hostname", host.given)
			if run.ExitCode != 0 || !strings.Contains(run.Stdout, "["+host.want+"]: ") {
				t.Fatalf("hostname default: %+v", run)
			}
			env := readInitFile(t, folder, ".env")
			if !strings.Contains(env, "\nYARD_WORKSTATION="+host.want+"\n") || !strings.Contains(env, "\nYARD_BASE_DOMAIN=lvh.me\n") {
				t.Fatalf("wrong default settings: %s", env)
			}
		})
	}
}

func TestInitPromptsAgainAfterInvalidAnswers(t *testing.T) {
	yard := commandtest.New(t)
	yard.Process.Terminal = true
	yard.Process.Stdin = "WS_A\n\nws-a\nNot A Domain\nlocalhost\n" + windowsLANAnswer
	folder := t.TempDir()
	run := yard.Run("ws", "init", "--dir", folder, "--hostname", "_")
	if run.ExitCode != 0 || !strings.Contains(run.Stdout, "WS_A is not a Workstation name") || !strings.Contains(run.Stdout, "A Workstation needs a name") || !strings.Contains(run.Stdout, "Not A Domain is not a domain name") {
		t.Fatalf("invalid answers: %+v", run)
	}
	if !strings.Contains(readInitFile(t, folder, ".env"), "\nYARD_BASE_DOMAIN=localhost\n") {
		t.Fatal("did not use corrected domain")
	}
}

func TestInitEarlyInputEndFailsBeforeWrites(t *testing.T) {
	for _, input := range []string{"", "ws-a\n", "WS_A\n", "ws-a\nNot A Domain\n"} {
		t.Run(input, func(t *testing.T) {
			yard := commandtest.New(t)
			yard.Process.Terminal = true
			yard.Process.Stdin = input
			folder := filepath.Join(t.TempDir(), "not-created")
			run := yard.Run("ws", "init", "--dir", folder, "--hostname", "_")
			if run.ExitCode != 1 || !strings.Contains(run.Stderr, "input ended") {
				t.Fatalf("early input end: %+v", run)
			}
			if _, err := os.Stat(folder); !os.IsNotExist(err) {
				t.Fatalf("wrote before input ended: %v", err)
			}
		})
	}
}

func TestInitDoesNotPromptForFlagValuesOrWithoutTerminal(t *testing.T) {
	for _, terminal := range []bool{true, false} {
		yard := commandtest.New(t)
		yard.Process.Terminal = terminal
		args := []string{"ws", "init", "--dir", t.TempDir(), "--workstation", "ws-a"}
		if runtime.GOOS == "windows" {
			// With a terminal, Windows asks for the LAN address unless a flag gives it.
			args = append(args, "--address", "192.168.1.20")
		}
		if terminal {
			args = append(args, "--base-domain", "lvh.me")
		}
		run := yard.Run(args...)
		if run.ExitCode != 0 || strings.Contains(run.Stdout, "Workstation name") || strings.Contains(run.Stdout, "Base domain") {
			t.Fatalf("unexpected prompts: %+v", run)
		}
	}
}
