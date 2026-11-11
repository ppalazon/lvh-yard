package command_test

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"syscall"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestInitReinitKeepsSettingsAndReplacesExplicitFlags(t *testing.T) {
	const oldKey = "pUqJrVyVRj5jsiYEkM/tFQYfWyJIv4s3XkvDwy7Cu5s="
	const newKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
	folder := t.TempDir()
	oldEnv := "YARD_WORKSTATION=ws-old\nYARD_BASE_DOMAIN=example.test\nYARD_GOSSIP_KEY=" + oldKey + "\nYARD_PEERS=10.0.0.1\nYARD_DASHBOARD_PORT=7790\nYARD_UID=12\nYARD_GID=34\nYARD_DOCKER_GID=56\nCOMPOSE_PROFILES=custom\n#YARD_HTTP_PORT=81\n"
	writeEnv(t, folder, oldEnv)
	if err := os.WriteFile(filepath.Join(folder, "compose.yaml"), []byte("old compose\n"), 0644); err != nil {
		t.Fatal(err)
	}
	yard := commandtest.New(t)
	yard.Version = "0.2.0"
	yard.Process.Terminal = true
	yard.Process.Identity = func(string) string { return "999" }
	run := yard.Run("ws", "init", "--dir", folder, "--reinit", "--peers", "10.0.0.2,10.0.0.3:8302", "--gossip-key", newKey)
	if run.ExitCode != 0 {
		t.Fatalf("reinit: %+v", run)
	}
	env := readInitFile(t, folder, ".env")
	for _, setting := range []string{"YARD_WORKSTATION=ws-old", "YARD_BASE_DOMAIN=example.test", "YARD_GOSSIP_KEY=" + newKey, "YARD_PEERS=10.0.0.2 10.0.0.3:8302", "YARD_DASHBOARD_PORT=7790", "YARD_UID=12", "YARD_GID=34", "YARD_DOCKER_GID=56", "COMPOSE_PROFILES=custom", "YARD_HTTP_PORT=80", "YARD_CONSUL_SERVER_PORT=8300"} {
		if !strings.Contains(env, "\n"+setting+"\n") {
			t.Errorf("missing %s in .env", setting)
		}
	}
	if strings.Contains(run.Stdout, "Workstation name") || strings.Contains(run.Stdout, "Base Domain [") {
		t.Fatalf("reinit asked for old values: %+v", run)
	}
	if !strings.Contains(run.Stdout, "http://127.0.0.1:7790") || !strings.Contains(run.Stdout, newKey) {
		t.Fatalf("missing current settings: %+v", run)
	}
	compose := readInitFile(t, folder, "compose.yaml")
	if !strings.Contains(compose, "lvh-yard-dashboard:0.2.0}") || strings.Contains(compose, "old compose") {
		t.Fatalf("compose was not replaced: %s", compose)
	}
	backups, err := filepath.Glob(filepath.Join(folder, "*.bak"))
	if err != nil || len(backups) != 2 {
		t.Fatalf("backups: %v, %v", backups, err)
	}
	for _, backup := range backups {
		if !regexp.MustCompile(`^(compose\.yaml|\.env)\.\d{8}-\d{6}\.bak$`).MatchString(filepath.Base(backup)) {
			t.Errorf("wrong backup name: %s", backup)
		}
		if !strings.Contains(run.Stdout, backup) {
			t.Errorf("backup not named: %s", backup)
		}
		want := oldEnv
		if strings.HasPrefix(filepath.Base(backup), "compose.yaml.") {
			want = "old compose\n"
		}
		if got := readInitFile(t, folder, filepath.Base(backup)); got != want {
			t.Errorf("backup changed: %q, want %q", got, want)
		}
	}
	info, err := os.Stat(filepath.Join(folder, "backups"))
	if err != nil || !info.IsDir() {
		t.Fatalf("backups folder: %v", err)
	}
	info, err = os.Stat(filepath.Join(folder, ".env"))
	if err != nil || info.Mode().Perm() != 0600 {
		t.Fatalf(".env permissions: %v, %v", info, err)
	}
}

func TestInitReinitKeepsOldSettingsWithoutFlags(t *testing.T) {
	const key = "pUqJrVyVRj5jsiYEkM/tFQYfWyJIv4s3XkvDwy7Cu5s="
	folder := t.TempDir()
	settings := []string{
		"YARD_WORKSTATION=ws-b",
		"YARD_BASE_DOMAIN=localhost",
		"YARD_GOSSIP_KEY=" + key,
		"YARD_PEERS=10.0.0.2 10.0.0.3:8302",
		"YARD_BIND_ADDRESS=10.0.0.9",
		"YARD_OUT_OF_RANGE=",
		"YARD_TRAEFIK_BIND_ADDRESS=0.0.0.0",
		"YARD_DASHBOARD_BIND_ADDRESS=0.0.0.0",
		"YARD_STACK_TRACES=false",
		"YARD_HTTP_PORT=81",
		"YARD_TRAEFIK_ADMIN_PORT=8081",
		"YARD_DASHBOARD_PORT=7790",
		"YARD_CONSUL_HTTP_PORT=8501",
		"YARD_CONSUL_DNS_PORT=8601",
		"YARD_CONSUL_GRPC_TLS_PORT=8504",
		"YARD_CONSUL_SERVER_PORT=8400",
		"YARD_CONSUL_SERF_LAN_PORT=8401",
		"YARD_CONSUL_SERF_WAN_PORT=8402",
	}
	writeEnv(t, folder, strings.Join(settings, "\n")+"\nYARD_WORKSTATION=ignored\nYARD_GONE=1\nYARD_ALSO_GONE=a=b c\n")
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--reinit")
	if run.ExitCode != 0 {
		t.Fatalf("reinit: %+v", run)
	}
	env := readInitFile(t, folder, ".env")
	for _, setting := range settings {
		if !strings.Contains(env, "\n"+setting+"\n") {
			t.Errorf("old setting lost: %s", setting)
		}
	}
	if !strings.HasSuffix(env, "\n# No longer settings of lvh-yard, kept from the old .env:\n#YARD_GONE=1\n#YARD_ALSO_GONE=a=b c\n") {
		t.Fatalf("old unknown settings lost: %s", env)
	}
	if !strings.Contains(run.Stdout, key) || !strings.Contains(run.Stdout, "http://127.0.0.1:7790") || !strings.Contains(run.Stdout, "TCP port 8400 and TCP and UDP port 8402") {
		t.Fatalf("old settings absent from next steps: %+v", run)
	}
}

func TestInitReinitFillsOldEmptyDiscoverySettings(t *testing.T) {
	folder := t.TempDir()
	socket := filepath.Join(t.TempDir(), "docker.sock")
	if err := os.WriteFile(socket, nil, 0600); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(socket)
	if err != nil {
		t.Fatal(err)
	}
	group := info.Sys().(*syscall.Stat_t).Gid
	writeEnv(t, folder, "YARD_WORKSTATION=ws-a\nYARD_UID=\nYARD_GID= \nYARD_DOCKER_GID=\nCOMPOSE_PROFILES=\nYARD_DASHBOARD_BIND_ADDRESS= \nYARD_DASHBOARD_PORT= \n")
	yard := commandtest.New(t)
	yard.Process.Identity = func(flag string) string {
		if flag == "-u" {
			return "1234"
		}
		return "2345"
	}
	run := yard.Run("ws", "init", "--dir", folder, "--reinit", "--docker-socket", socket)
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("reinit: %+v", run)
	}
	env := readInitFile(t, folder, ".env")
	for _, setting := range []string{"YARD_UID=1234", "YARD_GID=2345", fmt.Sprintf("YARD_DOCKER_GID=%d", group), "COMPOSE_PROFILES=dashboard"} {
		if !strings.Contains(env, "\n"+setting+"\n") {
			t.Errorf("missing discovered %s", setting)
		}
	}
	if !strings.Contains(run.Stdout, "http://127.0.0.1:7780") {
		t.Fatalf("blank settings do not use defaults: %+v", run)
	}
}

func TestInitReinitRejectsBadFlagsBeforeBackingUp(t *testing.T) {
	for _, flags := range [][]string{
		{"--workstation", "Bad_Name"},
		{"--workstation", ""},
		{"--base-domain", "Bad Domain"},
		{"--base-domain", ""},
		{"--gossip-key", "bad"},
		{"--gossip-key", ""},
		{"--force"},
		{"--upgrade"},
		{"--unknown"},
		{"--base-domain"},
	} {
		t.Run(strings.Join(flags, " "), func(t *testing.T) {
			folder := t.TempDir()
			const oldEnv = "YARD_WORKSTATION=ws-a\n"
			writeEnv(t, folder, oldEnv)
			if err := os.WriteFile(filepath.Join(folder, "compose.yaml"), []byte("old compose\n"), 0644); err != nil {
				t.Fatal(err)
			}
			args := append([]string{"ws", "init", "--dir", folder, "--reinit"}, flags...)
			run := commandtest.New(t).Run(args...)
			if run.ExitCode != 1 {
				t.Fatalf("bad flags accepted: %+v", run)
			}
			assertReinitUnchanged(t, folder, oldEnv)
		})
	}
}

func TestInitReinitRejectsInvalidOldSettingsBeforeBackingUp(t *testing.T) {
	for _, oldEnv := range []string{
		"YARD_WORKSTATION=Bad_Name\n",
		"YARD_WORKSTATION=ws-a\nYARD_BASE_DOMAIN=Bad Domain\n",
		"YARD_WORKSTATION=ws-a\nYARD_GOSSIP_KEY=bad\n",
	} {
		t.Run(oldEnv, func(t *testing.T) {
			folder := t.TempDir()
			writeEnv(t, folder, oldEnv)
			if err := os.WriteFile(filepath.Join(folder, "compose.yaml"), []byte("old compose\n"), 0644); err != nil {
				t.Fatal(err)
			}
			run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--reinit")
			if run.ExitCode != 1 {
				t.Fatalf("bad settings accepted: %+v", run)
			}
			assertReinitUnchanged(t, folder, oldEnv)
		})
	}
}

func assertReinitUnchanged(t *testing.T, folder, oldEnv string) {
	t.Helper()
	if got := readInitFile(t, folder, ".env"); got != oldEnv {
		t.Errorf("old .env changed: %q", got)
	}
	if got := readInitFile(t, folder, "compose.yaml"); got != "old compose\n" {
		t.Errorf("old compose changed: %q", got)
	}
	entries, err := os.ReadDir(folder)
	if err != nil || len(entries) != 2 {
		t.Fatalf("failed reinit wrote files: %v, %v", entries, err)
	}
}

func TestInitReinitWithoutOldEnvWritesNothing(t *testing.T) {
	folder := t.TempDir()
	if err := os.WriteFile(filepath.Join(folder, "compose.yaml"), []byte("old compose\n"), 0644); err != nil {
		t.Fatal(err)
	}
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--reinit", "--workstation", "ws-a")
	if run.ExitCode != 1 || !strings.Contains(run.Stderr, "No "+filepath.Join(folder, ".env")+" to carry values from") || !strings.Contains(run.Stderr, "without --reinit") {
		t.Fatalf("missing .env not explained: %+v", run)
	}
	entries, err := os.ReadDir(folder)
	if err != nil || len(entries) != 1 || entries[0].Name() != "compose.yaml" {
		t.Fatalf("missing .env wrote files: %v, %v", entries, err)
	}
	if got := readInitFile(t, folder, "compose.yaml"); got != "old compose\n" {
		t.Fatalf("old compose changed: %q", got)
	}
}

func TestInitReinitReplacesNamesOnlyWhenExplicit(t *testing.T) {
	folder := t.TempDir()
	writeEnv(t, folder, "YARD_WORKSTATION=ws-a\nYARD_BASE_DOMAIN=lvh.me\nYARD_PEERS=10.0.0.1\n")
	run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--reinit", "--workstation", "ws-b", "--base-domain", "localhost", "--peers", "")
	if run.ExitCode != 0 {
		t.Fatalf("reinit: %+v", run)
	}
	env := readInitFile(t, folder, ".env")
	for _, setting := range []string{"YARD_WORKSTATION=ws-b", "YARD_BASE_DOMAIN=localhost", "YARD_PEERS="} {
		if !strings.Contains(env, "\n"+setting+"\n") {
			t.Errorf("flag did not replace old value: %s", setting)
		}
	}
}

func TestInitReinitLeavesOtherFilesAndBackupsUntouched(t *testing.T) {
	for _, profiles := range []bool{false, true} {
		t.Run(fmt.Sprint(profiles), func(t *testing.T) {
			folder := t.TempDir()
			writeEnv(t, folder, "YARD_WORKSTATION=ws-a\n")
			if err := os.Mkdir(filepath.Join(folder, "backups"), 0755); err != nil {
				t.Fatal(err)
			}
			if err := os.WriteFile(filepath.Join(folder, "backups", "saved.yaml"), []byte("version: 2\n"), 0600); err != nil {
				t.Fatal(err)
			}
			if profiles {
				if err := os.WriteFile(filepath.Join(folder, "route-profiles.yaml"), []byte("mine: {}\n"), 0600); err != nil {
					t.Fatal(err)
				}
			}
			run := commandtest.New(t).Run("ws", "init", "--dir", folder, "--reinit")
			if run.ExitCode != 0 {
				t.Fatalf("reinit: %+v", run)
			}
			if got := readInitFile(t, folder, filepath.Join("backups", "saved.yaml")); got != "version: 2\n" {
				t.Fatalf("Backup changed: %q", got)
			}
			if profiles {
				if got := readInitFile(t, folder, "route-profiles.yaml"); got != "mine: {}\n" {
					t.Fatalf("Route Profiles changed: %q", got)
				}
			} else if _, err := os.Stat(filepath.Join(folder, "route-profiles.yaml")); !os.IsNotExist(err) {
				t.Fatalf("Route Profiles created: %v", err)
			}
			if matches, err := filepath.Glob(filepath.Join(folder, "route-profiles.yaml*.bak")); err != nil || len(matches) != 0 {
				t.Fatalf("Route Profiles backed up: %v, %v", matches, err)
			}
		})
	}
}
