package templates_test

import (
	"os"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

// Each role mounts only its own certificate folder, read-only. Nothing mounts ca/, which holds the CA key.
func TestComposeMountsKeepCertificateRolesApart(t *testing.T) {
	want := map[string]string{
		"consul":    "./tls/consul:/consul/tls:ro",
		"traefik":   "./tls/traefik:",
		"dashboard": "",
	}
	for _, name := range []string{"compose.yaml", "compose.windows.yaml"} {
		t.Run(name, func(t *testing.T) {
			source, err := os.ReadFile(name)
			if err != nil {
				t.Fatal(err)
			}
			var file struct {
				Services map[string]struct {
					Volumes []string `yaml:"volumes"`
				} `yaml:"services"`
			}
			if err := yaml.Unmarshal(source, &file); err != nil {
				t.Fatal(err)
			}
			for service, expected := range want {
				config, found := file.Services[service]
				if !found {
					t.Fatalf("%s has no %s service", name, service)
				}
				var certificates []string
				for _, volume := range config.Volumes {
					folder, _, _ := strings.Cut(volume, ":")
					if folder == "./ca" || strings.HasPrefix(folder, "./ca/") {
						t.Errorf("%s mounts the CA folder: %s", service, volume)
					}
					if folder == "./tls" || strings.HasPrefix(folder, "./tls/") {
						certificates = append(certificates, volume)
					}
				}
				switch {
				case expected == "" && len(certificates) != 0:
					t.Errorf("%s mounts certificates: %v", service, certificates)
				case expected != "" && (len(certificates) != 1 || !strings.HasPrefix(certificates[0], expected) || !strings.HasSuffix(certificates[0], ":ro")):
					t.Errorf("%s certificate mounts = %v, want one read-only mount of %s", service, certificates, strings.TrimSuffix(expected, ":"))
				}
			}
		})
	}
}
