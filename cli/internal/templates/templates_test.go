package templates_test

import (
	"bytes"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/templates"
)

func TestCommittedTemplatesMatchRepositoryBytes(t *testing.T) {
	for _, name := range []string{"compose.yaml", "compose.windows.yaml", "traefik-windows.sh", ".env.example", "lvh-yard.yaml", "00-lvh-yard.hcl", "10-tls.hcl", "10-tls.yaml"} {
		t.Run(name, func(t *testing.T) {
			root, err := os.ReadFile(filepath.Join("..", "..", "..", "config", name))
			if err != nil {
				t.Fatal(err)
			}
			embedded, err := os.ReadFile(name)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(root, embedded) {
				t.Fatalf("%s differs from config/; run mise run cli:templates", name)
			}
		})
	}
}

func TestComposeSelectsPlatformAndVersion(t *testing.T) {
	name := "compose.yaml"
	if runtime.GOOS == "windows" {
		name = "compose.windows.yaml"
	}
	source, err := os.ReadFile(name)
	if err != nil {
		t.Fatal(err)
	}
	for _, version := range []string{"", "dev", "1.2.3"} {
		t.Run(version, func(t *testing.T) {
			got, err := templates.Compose(version)
			if err != nil {
				t.Fatal(err)
			}
			want := string(source)
			if version != "" && version != "dev" {
				want = strings.ReplaceAll(want, "ghcr.io/ppalazon/lvh-yard-dashboard:latest}", "ghcr.io/ppalazon/lvh-yard-dashboard:"+version+"}")
			}
			if got != want {
				t.Fatal("Compose changed more than the dashboard image version or selected the wrong platform")
			}
		})
	}
}

func TestEnvSelectsPlatformDefaults(t *testing.T) {
	source, err := os.ReadFile(".env.example")
	if err != nil {
		t.Fatal(err)
	}
	got := templates.Env()
	if runtime.GOOS != "windows" {
		if got != string(source) {
			t.Fatal("Unix environment defaults changed")
		}
		return
	}
	for _, setting := range []string{"YARD_UID=1000", "YARD_GID=1000", "YARD_DOCKER_GID=0", "YARD_BIND_ADDRESS="} {
		found := false
		for line := range strings.SplitSeq(got, "\n") {
			if line == setting {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("Windows environment lacks %s", setting)
		}
	}
}

func TestWindowsTraefikReturnsOwnedScript(t *testing.T) {
	source, err := os.ReadFile("traefik-windows.sh")
	if err != nil {
		t.Fatal(err)
	}
	if templates.WindowsTraefik() != string(source) {
		t.Fatal("WindowsTraefik does not return the owned reload script")
	}
}
