package command_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
)

func TestInitUpgradeChangesOnlyImageValues(t *testing.T) {
	for _, tc := range []struct {
		name, version, tag, newline, indent string
	}{
		{"release", "0.8.1", "0.8.1", "\n", "  "},
		{"development CRLF", "dev", "latest", "\r\n", "    "},
	} {
		t.Run(tc.name, func(t *testing.T) {
			folder := t.TempDir()
			before := "# my own note\nservices: # keep this\n" +
				"  consul: # the registry\n    image:  'hashicorp/consul:1.0.0'  # pinned by me\n    environment:\n      image: leave-this-alone\n" +
				"  traefik:\n    image:\t\"traefik:v3.0.0\" # my proxy\n    command: [custom]\n" +
				"  dashboard:\n    image: ${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:0.1.0}  # my dashboard\n" +
				"  other:\n    image: my-own-image:1\nvolumes:\n  consul:\n    image: not-a-service"
			want := "# my own note\nservices: # keep this\n" +
				"  consul: # the registry\n    image:  'hashicorp/consul:2.1.0'  # pinned by me\n    environment:\n      image: leave-this-alone\n" +
				"  traefik:\n    image:\t\"traefik:v3.7.16\" # my proxy\n    command: [custom]\n" +
				"  dashboard:\n    image: ${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:" + tc.tag + "}  # my dashboard\n" +
				"  other:\n    image: my-own-image:1\nvolumes:\n  consul:\n    image: not-a-service"
			if tc.indent != "  " {
				before = strings.ReplaceAll(before, "\n  ", "\n"+tc.indent)
				want = strings.ReplaceAll(want, "\n  ", "\n"+tc.indent)
			}
			before = strings.ReplaceAll(before, "\n", tc.newline)
			want = strings.ReplaceAll(want, "\n", tc.newline)
			writeUpgradeFile(t, folder, "compose.yaml", before)
			writeUpgradeFile(t, folder, ".env", "YARD_WORKSTATION=mine\n")
			writeUpgradeFile(t, folder, "route-profiles.yaml", "# mine\n")
			yard := commandtest.New(t)
			yard.Version = tc.version
			run := yard.Run("init", "--dir", folder, "--upgrade")
			if run.ExitCode != 0 || run.Stderr != "" {
				t.Fatalf("upgrade: %+v", run)
			}
			if got := readInitFile(t, folder, "compose.yaml"); got != want {
				t.Fatalf("compose.yaml changed beyond its image values:\ngot  %q\nwant %q", got, want)
			}
			if readInitFile(t, folder, ".env") != "YARD_WORKSTATION=mine\n" || readInitFile(t, folder, "route-profiles.yaml") != "# mine\n" {
				t.Fatal("upgrade changed another setup file")
			}
			for _, change := range []string{"consul: hashicorp/consul:1.0.0 -> hashicorp/consul:2.1.0", "traefik: traefik:v3.0.0 -> traefik:v3.7.16", "dashboard: ${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:0.1.0} -> ${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:" + tc.tag + "}", "docker compose up -d"} {
				if !strings.Contains(run.Stdout, change) {
					t.Errorf("missing %q in %q", change, run.Stdout)
				}
			}
		})
	}
}

func writeUpgradeFile(t *testing.T, folder, name, text string) {
	t.Helper()
	if err := os.WriteFile(filepath.Join(folder, name), []byte(text), 0600); err != nil {
		t.Fatal(err)
	}
}

func TestInitUpgradeAlreadyCurrentDoesNotWrite(t *testing.T) {
	folder := t.TempDir()
	const text = "services:\n  dashboard:\n    image: '${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:0.8.1}' # keep\n  consul:\n    image: hashicorp/consul:2.1.0\n  traefik:\n    image: \"traefik:v3.7.16\"\n"
	writeUpgradeFile(t, folder, "compose.yaml", text)
	file := filepath.Join(folder, "compose.yaml")
	oldTime := time.Unix(1000000000, 0)
	if err := os.Chtimes(file, oldTime, oldTime); err != nil {
		t.Fatal(err)
	}
	yard := commandtest.New(t)
	yard.Version = "0.8.1"
	run := yard.Run("init", "--dir", folder, "--upgrade")
	if run.ExitCode != 0 || run.Stderr != "" || strings.Count(run.Stdout, "already this version's") != 3 {
		t.Fatalf("already-current upgrade: %+v", run)
	}
	info, err := os.Stat(file)
	if err != nil {
		t.Fatal(err)
	}
	if !info.ModTime().Equal(oldTime) || readInitFile(t, folder, "compose.yaml") != text {
		t.Fatal("already-current upgrade wrote compose.yaml")
	}
}

func TestInitUpgradeMissingImageLeavesEveryFileUntouched(t *testing.T) {
	for _, service := range []string{"consul", "traefik", "dashboard"} {
		t.Run(service, func(t *testing.T) {
			folder := t.TempDir()
			const complete = "services:\n  dashboard:\n    image: old-dashboard\n  consul:\n    image: old-consul\n  traefik:\n    image: old-traefik\n"
			before := strings.Replace(complete, "    image: old-"+service+"\n", "    # image: removed\n    environment:\n      image: not-a-service-image\n", 1)
			writeUpgradeFile(t, folder, "compose.yaml", before)
			writeUpgradeFile(t, folder, ".env", "YARD_WORKSTATION=mine\n")
			run := commandtest.New(t).Run("init", "--dir", folder, "--upgrade")
			if run.ExitCode != 1 || !strings.Contains(run.Stderr, filepath.Join(folder, "compose.yaml")+" has no image: line for "+service) || !strings.Contains(run.Stderr, "nothing was changed") {
				t.Fatalf("missing image: %+v", run)
			}
			if run.Stdout != "" || readInitFile(t, folder, "compose.yaml") != before || readInitFile(t, folder, ".env") != "YARD_WORKSTATION=mine\n" {
				t.Fatal("failed upgrade changed files or reported image changes")
			}
		})
	}
}

func TestInitUpgradeMissingComposeSaysToRunInit(t *testing.T) {
	folder := t.TempDir()
	run := commandtest.New(t).Run("init", "--dir", folder, "--upgrade")
	if run.ExitCode != 1 || !strings.Contains(run.Stderr, filepath.Join(folder, "compose.yaml")+" does not exist") || !strings.Contains(run.Stderr, "lvh-yard init") {
		t.Fatalf("missing compose: %+v", run)
	}
	files, err := os.ReadDir(folder)
	if err != nil || len(files) != 0 {
		t.Fatalf("upgrade wrote files: %v, %v", files, err)
	}
}

func TestInitUpgradeRejectsOtherWriteModes(t *testing.T) {
	for _, mode := range []string{"--force", "--reinit"} {
		t.Run(mode, func(t *testing.T) {
			folder := t.TempDir()
			writeUpgradeFile(t, folder, ".env", "YARD_WORKSTATION=mine\n")
			run := commandtest.New(t).Run("init", "--dir", folder, "--upgrade", mode)
			if run.ExitCode != 1 || !strings.Contains(run.Stderr, "upgrade") || !strings.Contains(run.Stderr, strings.TrimPrefix(mode, "--")) {
				t.Fatalf("conflicting modes: %+v", run)
			}
			files, err := os.ReadDir(folder)
			if err != nil || len(files) != 1 || files[0].Name() != ".env" || readInitFile(t, folder, ".env") != "YARD_WORKSTATION=mine\n" {
				t.Fatalf("conflicting modes changed files: %v, %v", files, err)
			}
		})
	}
}

func TestInitUpgradeUsesStandardWorkstationFolder(t *testing.T) {
	folder := t.TempDir()
	writeUpgradeFile(t, folder, "compose.yaml", "services:\n  dashboard:\n    image: old-dashboard\n  consul:\n    image: old-consul\n  traefik:\n    image: old-traefik\n")
	yard := commandtest.New(t)
	yard.Process.Env["YARD_CONFIG_DIR"] = folder
	run := yard.Run("init", "--upgrade")
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("upgrade default Workstation Folder: %+v", run)
	}
	for _, image := range []string{"hashicorp/consul:2.1.0", "traefik:v3.7.16", "${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:latest}"} {
		if !strings.Contains(readInitFile(t, folder, "compose.yaml"), "image: "+image+"\n") {
			t.Errorf("default Workstation Folder is missing image %s", image)
		}
	}
}
