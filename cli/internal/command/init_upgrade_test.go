package command_test

import (
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
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
				dataInitService("hashicorp/consul:1.0.0") +
				"  other:\n    image: my-own-image:1\nvolumes:\n  consul:\n    image: not-a-service"
			want := "# my own note\nservices: # keep this\n" +
				"  consul: # the registry\n    image:  'hashicorp/consul:2.1.0'  # pinned by me\n    environment:\n      image: leave-this-alone\n" +
				"  traefik:\n    image:\t\"traefik:v3.7.16\" # my proxy\n    command: [custom]\n" +
				"  dashboard:\n    image: ${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:" + tc.tag + "}  # my dashboard\n" +
				dataInitService("hashicorp/consul:2.1.0") +
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
			writeUpgradeFile(t, folder, "lvh-yard.yaml", "# my dashboard settings\nlvh-yard:\n  events:\n    debounce-window: 500ms\n")
			yard := commandtest.New(t)
			yard.Version = tc.version
			newBackupDashboard(t, yard, folder)
			run := yard.Run("ws", "compose", "update", "--dir", folder)
			if run.ExitCode != 0 || run.Stderr != "" {
				t.Fatalf("upgrade: %+v", run)
			}
			if got := readInitFile(t, folder, "compose.yaml"); got != want {
				t.Fatalf("compose.yaml changed beyond its image values:\ngot  %q\nwant %q", got, want)
			}
			if readInitFile(t, folder, ".env") != "YARD_WORKSTATION=mine\n" || readInitFile(t, folder, "route-profiles.yaml") != "# mine\n" {
				t.Fatal("upgrade changed another setup file")
			}
			if readInitFile(t, folder, "lvh-yard.yaml") != "# my dashboard settings\nlvh-yard:\n  events:\n    debounce-window: 500ms\n" {
				t.Fatal("upgrade changed dashboard settings")
			}
		})
	}
}

// dataInitService is the consul-data-init service. The upgrade changes its image when compose.yaml has it.
func dataInitService(image string) string {
	return "  consul-data-init:\n    image: " + image + "\n"
}

func writeUpgradeFile(t *testing.T, folder, name, text string) {
	t.Helper()
	if err := os.WriteFile(filepath.Join(folder, name), []byte(text), 0600); err != nil {
		t.Fatal(err)
	}
}

func TestInitUpgradeAlreadyCurrentDoesNotWriteCompose(t *testing.T) {
	folder := t.TempDir()
	text := "services:\n  dashboard:\n    image: '${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:0.8.1}' # keep\n  consul:\n    image: hashicorp/consul:2.1.0\n  traefik:\n    image: \"traefik:v3.7.16\"\n" + dataInitService("hashicorp/consul:2.1.0")
	writeUpgradeFile(t, folder, "compose.yaml", text)
	file := filepath.Join(folder, "compose.yaml")
	oldTime := time.Unix(1000000000, 0)
	if err := os.Chtimes(file, oldTime, oldTime); err != nil {
		t.Fatal(err)
	}
	yard := commandtest.New(t)
	yard.Version = "0.8.1"
	newBackupDashboard(t, yard, folder)
	run := yard.Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || run.Stderr != "" {
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
			run := commandtest.New(t).Run("ws", "compose", "update", "--dir", folder)
			if run.ExitCode != 1 || !strings.Contains(run.Stderr, filepath.Join(folder, "compose.yaml")+" has no image: line for "+service) || !strings.Contains(run.Stderr, "Nothing was changed") {
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
	run := commandtest.New(t).Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 1 || !strings.Contains(run.Stderr, filepath.Join(folder, "compose.yaml")+" does not exist") || !strings.Contains(run.Stderr, "lvh-yard ws init") {
		t.Fatalf("missing compose: %+v", run)
	}
	files, err := os.ReadDir(folder)
	if err != nil || len(files) != 0 {
		t.Fatalf("upgrade wrote files: %v, %v", files, err)
	}
}

func TestInitUpgradeRejectsOtherWriteModes(t *testing.T) {
	for _, mode := range []string{"--force", "--unknown"} {
		t.Run(mode, func(t *testing.T) {
			folder := t.TempDir()
			writeUpgradeFile(t, folder, ".env", "YARD_WORKSTATION=mine\n")
			run := commandtest.New(t).Run("ws", "compose", "update", "--dir", folder, mode)
			if run.ExitCode != 1 || !strings.Contains(run.Stderr, "unknown flag") || !strings.Contains(run.Stderr, strings.TrimPrefix(mode, "--")) {
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
	writeUpgradeFile(t, folder, "compose.yaml", "services:\n  dashboard:\n    image: old-dashboard\n  consul:\n    image: old-consul\n  traefik:\n    image: old-traefik\n"+dataInitService("old-consul"))
	yard := commandtest.New(t)
	yard.Process.Env["YARD_CONFIG_DIR"] = folder
	newBackupDashboard(t, yard, folder)
	run := yard.Run("ws", "compose", "update")
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("upgrade default Workstation Folder: %+v", run)
	}
	for _, image := range []string{"hashicorp/consul:2.1.0", "traefik:v3.7.16", "${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:latest}"} {
		if !strings.Contains(readInitFile(t, folder, "compose.yaml"), "image: "+image+"\n") {
			t.Errorf("default Workstation Folder is missing image %s", image)
		}
	}
}

// A compose.yaml that init wrote must be one that upgrade accepts, on every operating system.
func TestInitUpgradeAcceptsTheComposeThatInitWrote(t *testing.T) {
	folder := t.TempDir()
	yard := commandtest.New(t)
	yard.Version = "0.8.1"
	newBackupDashboard(t, yard, folder)
	if run := yard.Run("ws", "init", "--dir", folder, "--workstation", "ws-a", "--base-domain", "lvh.me"); run.ExitCode != 0 {
		t.Fatalf("init: %+v", run)
	}
	yard.Version = "0.8.2"
	run := yard.Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || run.Stderr != "" {
		t.Fatalf("upgrade of a generated compose.yaml: %+v", run)
	}
	if !strings.Contains(readInitFile(t, folder, "compose.yaml"), "lvh-yard-dashboard:0.8.2}") {
		t.Fatal("upgrade did not change the dashboard image")
	}
}

// backupDashboard stands in for the running dashboard of a Workstation that is being updated. It gives rawBackup, and
// remembers what compose.yaml held when the Backup was asked for.
type backupDashboard struct {
	// status is the HTTP status of the Backup. Zero means 200.
	status atomic.Int32
	asked  atomic.Int32
	mu     sync.Mutex
	// composeAtBackup is compose.yaml at the last request, to show that the Backup comes before the changes.
	composeAtBackup string
}

func newBackupDashboard(t *testing.T, yard *commandtest.Harness, folder string) *backupDashboard {
	t.Helper()
	d := &backupDashboard{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		d.asked.Add(1)
		data, _ := os.ReadFile(filepath.Join(folder, "compose.yaml"))
		d.mu.Lock()
		d.composeAtBackup = string(data)
		d.mu.Unlock()
		if status := int(d.status.Load()); status != 0 {
			http.Error(w, "down", status)
			return
		}
		_, _ = io.WriteString(w, rawBackup)
	}))
	t.Cleanup(server.Close)
	yard.Process.Env["YARD_DASHBOARD_URL"] = server.URL
	return d
}

const oldComposeForBackup = "services:\n  dashboard:\n    image: old-dashboard\n  consul:\n    image: old-consul\n  traefik:\n    image: old-traefik\n"

func TestComposeUpdateWritesABackupBeforeItChangesAnyFile(t *testing.T) {
	folder := t.TempDir()
	writeUpgradeFile(t, folder, "compose.yaml", oldComposeForBackup)
	writeUpgradeFile(t, folder, ".env", "YARD_WORKSTATION=ws-a\n")
	yard := commandtest.New(t)
	dashboard := newBackupDashboard(t, yard, folder)
	run := yard.Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || run.Stderr != "" || !strings.Contains(run.Stdout, "Backup before the update: "+filepath.Join(folder, "backups", "ws-a-")) {
		t.Fatalf("update: %+v", run)
	}
	if dashboard.composeAtBackup != oldComposeForBackup {
		t.Fatalf("compose.yaml was already changed when the Backup was taken: %q", dashboard.composeAtBackup)
	}
	files, _ := filepath.Glob(filepath.Join(folder, "backups", "ws-a-*-before-update.yaml"))
	if len(files) != 1 {
		t.Fatalf("Backups: %v", files)
	}
	data, err := os.ReadFile(files[0])
	if err != nil || string(data) != rawBackup {
		t.Fatalf("Backup = %q, %v", data, err)
	}
	if info, err := os.Stat(files[0]); err != nil || (commandtest.UnixModes && info.Mode().Perm() != 0o600) {
		t.Fatalf("Backup mode: %v, %v", info, err)
	}
}

// .env belongs to the user: a Workstation name with a path in it must not decide where the Backup goes.
func TestComposeUpdateBackupNameNeverHoldsAPath(t *testing.T) {
	folder := t.TempDir()
	writeUpgradeFile(t, folder, "compose.yaml", oldComposeForBackup)
	writeUpgradeFile(t, folder, ".env", "YARD_WORKSTATION=../../escape\n")
	yard := commandtest.New(t)
	newBackupDashboard(t, yard, folder)
	if run := yard.Run("ws", "compose", "update", "--dir", folder); run.ExitCode != 0 {
		t.Fatalf("update: %+v", run)
	}
	if files, _ := filepath.Glob(filepath.Join(folder, "backups", "workstation-*-before-update.yaml")); len(files) != 1 {
		t.Fatalf("Backups in the Backups folder: %v", files)
	}
	if outside, _ := filepath.Glob(filepath.Join(folder, "..", "*escape*")); len(outside) != 0 {
		t.Fatalf("a Backup was written outside the Workstation Folder: %v", outside)
	}
}

func TestComposeUpdateGoesOnWhenTheDashboardCannotGiveABackup(t *testing.T) {
	folder := t.TempDir()
	writeUpgradeFile(t, folder, "compose.yaml", oldComposeForBackup)
	yard := commandtest.New(t)
	dashboard := newBackupDashboard(t, yard, folder)
	dashboard.status.Store(http.StatusServiceUnavailable)
	run := yard.Run("ws", "compose", "update", "--dir", folder)
	if run.ExitCode != 0 || !strings.Contains(run.Stderr, "no Backup was written before the update") {
		t.Fatalf("update: %+v", run)
	}
	if !strings.Contains(readInitFile(t, folder, "compose.yaml"), "image: hashicorp/consul:") {
		t.Fatal("the update did not finish")
	}
	if files, _ := filepath.Glob(filepath.Join(folder, "backups", "*")); len(files) != 0 {
		t.Fatalf("files in backups: %v", files)
	}
}

// A refused update changes nothing, so it asks for no Backup and leaves no Backups folder.
func TestComposeUpdateThatRefusesWritesNoBackup(t *testing.T) {
	folder := t.TempDir()
	writeUpgradeFile(t, folder, "compose.yaml", "services:\n  consul:\n    image: old-consul\n")
	yard := commandtest.New(t)
	dashboard := newBackupDashboard(t, yard, folder)
	if run := yard.Run("ws", "compose", "update", "--dir", folder); run.ExitCode != 1 {
		t.Fatalf("update: %+v", run)
	}
	if dashboard.asked.Load() != 0 {
		t.Fatal("a refused update asked the dashboard for a Backup")
	}
	if _, err := os.Stat(filepath.Join(folder, "backups")); !os.IsNotExist(err) {
		t.Fatalf("backups folder: %v", err)
	}
}
