package command_test

import (
	"encoding/base64"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
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
	for _, setting := range []string{"YARD_WORKSTATION=ws-a", "YARD_BASE_DOMAIN=lvh.me", "YARD_PEERS=", "COMPOSE_PROFILES=dashboard,traefik", "YARD_TRAEFIK_ENABLED=true", "YARD_UID=1234", "YARD_GID=2345"} {
		if !strings.Contains(env, "\n"+setting+"\n") {
			t.Errorf("missing %s in .env", setting)
		}
	}
	compose := readInitFile(t, folder, "compose.yaml")
	if strings.Contains(compose, "    build: .\n") || !strings.Contains(compose, "ghcr.io/ppalazon/lvh-yard-dashboard:latest}") || !strings.Contains(compose, "./backups:") {
		t.Fatalf("wrong compose template: %s", compose)
	}
	info, err := os.Stat(filepath.Join(folder, "backups"))
	if err != nil || !info.IsDir() {
		t.Fatalf("backups folder: %v", err)
	}
	info, err = os.Stat(filepath.Join(folder, ".env"))
	if err != nil || info.Mode().Perm() != 0600 {
		t.Fatalf(".env permissions: %v, %v", info, err)
	}
	if _, err := os.Stat(filepath.Join(folder, "route-profiles.yaml")); !os.IsNotExist(err) {
		t.Fatalf("unexpected Route Profiles file: %v", err)
	}
	if !strings.Contains(run.Stderr, "YARD_DOCKER_GID") {
		t.Fatalf("missing Docker group warning: %+v", run)
	}
}

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
	root, err := os.ReadFile(filepath.Join("..", "..", "..", "config", ".env.example"))
	if err != nil {
		t.Fatal(err)
	}
	want := strings.Split(string(root), "\n")
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
	const key = "pUqJrVyVRj5jsiYEkM/tFQYfWyJIv4s3XkvDwy7Cu5s="
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-b", "--base-domain", "localhost", "--peers", "192.168.1.10,192.168.1.11:8302", "--peers", "10.0.0.2 10.0.0.3", "--gossip-key", key)
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	env := readInitFile(t, folder, ".env")
	for _, value := range []string{"YARD_WORKSTATION=ws-b", "YARD_BASE_DOMAIN=localhost", "YARD_PEERS=192.168.1.10 192.168.1.11:8302 10.0.0.2 10.0.0.3", "YARD_GOSSIP_KEY=" + key} {
		if !strings.Contains(env, "\n"+value+"\n") {
			t.Errorf("missing setting %s", value)
		}
	}
}

func TestInitGeneratesAndSharesNewGossipKey(t *testing.T) {
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
		if !strings.Contains(run.Stdout, "The Yard's Gossip Key: "+key) || !strings.Contains(run.Stdout, "Give this key to every other Workstation") {
			t.Fatalf("missing key instructions: %+v", run)
		}
		previous = key
	}
}

func TestInitRefusesOverwriteBeforeWritingAnything(t *testing.T) {
	for _, existing := range []string{"compose.yaml", ".env"} {
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
	if err != nil || info.Mode().Perm() != 0600 {
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
		{"--workstation", "ws-a", "--gossip-key", "secret"},
		{"--workstation", "ws-a", "--gossip-key", ""},
		{"--force", "--upgrade"},
		{"--force", "--reinit"},
		{"--upgrade", "--reinit"},
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

func TestInitAcceptsAllConsulGossipKeyLengths(t *testing.T) {
	for _, length := range []int{16, 24, 32} {
		key := base64.StdEncoding.EncodeToString(make([]byte, length))
		run := commandtest.New(t).Run("ws", "init", "--dir", t.TempDir(), "--workstation", "ws-a", "--gossip-key", key)
		if run.ExitCode != 0 {
			t.Fatalf("%d-byte key: %+v", length, run)
		}
	}
}

func TestInitFindsDockerSocketGroup(t *testing.T) {
	socket := filepath.Join(t.TempDir(), "docker.sock")
	if err := os.WriteFile(socket, nil, 0600); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(socket)
	if err != nil {
		t.Fatal(err)
	}
	stat := info.Sys().(*syscall.Stat_t)
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--docker-socket", socket)
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("Docker group: %+v", run)
	}
	want := "YARD_DOCKER_GID=" + strconv.FormatUint(uint64(stat.Gid), 10)
	if !strings.Contains(readInitFile(t, folder, ".env"), "\n"+want+"\n") {
		t.Fatalf("missing %s", want)
	}
}

func TestInitUsesWorkstationFolderLookup(t *testing.T) {
	for _, setting := range []string{"HOME", "XDG_CONFIG_HOME", "YARD_CONFIG_DIR", "dir"} {
		t.Run(setting, func(t *testing.T) {
			yard := commandtest.New(t)
			base := t.TempDir()
			yard.Process.Env["HOME"] = base
			want := filepath.Join(base, ".config", "lvh-yard")
			args := []string{"ws", "init", "--workstation", "ws-a"}
			if setting == "XDG_CONFIG_HOME" {
				yard.Process.Env[setting] = base
				want = filepath.Join(base, "lvh-yard")
			} else if setting == "YARD_CONFIG_DIR" {
				yard.Process.Env[setting] = base
				want = base
			} else if setting == "dir" {
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
	yard.Process.Stdin = "ws-a\nexample.test\n"
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
			yard.Process.Stdin = "\n\n"
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
	yard.Process.Stdin = "WS_A\n\nws-a\nNot A Domain\nlocalhost\n"
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
		if terminal {
			args = append(args, "--base-domain", "lvh.me")
		}
		run := yard.Run(args...)
		if run.ExitCode != 0 || strings.Contains(run.Stdout, "Workstation name") || strings.Contains(run.Stdout, "Base domain") {
			t.Fatalf("unexpected prompts: %+v", run)
		}
	}
}

func TestInitPrintsNextSteps(t *testing.T) {
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a")
	if run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	for _, next := range []string{
		"Open TCP port 8300 and TCP and UDP port 8302",
		"cd " + folder + " && docker compose up -d",
		"http://127.0.0.1:7780",
		"lvh-yard status",
		"lvh-yard ws join --peers <ip> --gossip-key <key>",
	} {
		if !strings.Contains(run.Stdout, next) {
			t.Errorf("missing next step %q", next)
		}
	}
}

func TestInitAcceptsGossipKeyWithoutBase64Padding(t *testing.T) {
	const key = "pUqJrVyVRj5jsiYEkM/tFQYfWyJIv4s3XkvDwy7Cu5s"
	folder := t.TempDir()
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--gossip-key", key)
	if run.ExitCode != 0 || !strings.Contains(readInitFile(t, folder, ".env"), "\nYARD_GOSSIP_KEY="+key+"\n") {
		t.Fatalf("unpadded gossip key: %+v", run)
	}
}
