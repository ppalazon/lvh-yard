package templates_test

import (
	"maps"
	"os"
	"regexp"
	"slices"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

// settingKeys gives each setting that a .env template names, and whether the template comments it out.
func settingKeys(t *testing.T, name string) map[string]bool {
	t.Helper()
	text, err := os.ReadFile(name)
	if err != nil {
		t.Fatal(err)
	}
	keys := map[string]bool{}
	for line := range strings.SplitSeq(string(text), "\n") {
		commented := strings.HasPrefix(line, "#")
		key, _, setting := strings.Cut(strings.TrimPrefix(line, "#"), "=")
		if setting && regexp.MustCompile(`^[A-Z][A-Z0-9_]*$`).MatchString(key) {
			keys[key] = commented
		}
	}
	return keys
}

// A $$ before the brace is Compose's escape for the shell, not a variable of Compose.
var variableReference = regexp.MustCompile(`(?:^|[^$])\$\{([A-Z][A-Z0-9_]*)([^}]*)\}`)

// composeVariables gives each variable that a Compose file reads, with its forms: the text after the name.
func composeVariables(t *testing.T, name string) map[string][]string {
	t.Helper()
	text, err := os.ReadFile(name)
	if err != nil {
		t.Fatal(err)
	}
	variables := map[string][]string{}
	for _, match := range variableReference.FindAllStringSubmatch(string(text), -1) {
		variable, form := match[1], match[2]
		if !slices.Contains(variables[variable], form) {
			variables[variable] = append(variables[variable], form)
		}
	}
	return variables
}

func TestWindowsEnvTemplateKeepsTheUnixSettings(t *testing.T) {
	unix, windows := settingKeys(t, ".env.example"), settingKeys(t, ".env.windows.example")
	// Only Windows has these. The Windows Compose file does not read the three user settings, so they stay commented out.
	windowsOnly := []string{"YARD_INSTANCE_HOST"}
	for key := range unix {
		if _, found := windows[key]; !found {
			t.Errorf("the Windows .env template lacks %s", key)
		}
	}
	for key := range windows {
		if _, found := unix[key]; !found && !slices.Contains(windowsOnly, key) {
			t.Errorf("the Windows .env template has %s, which only Windows has", key)
		}
	}
	for _, key := range []string{"YARD_INSTANCE_HOST"} {
		if commented, found := windows[key]; !found || commented {
			t.Errorf("the Windows .env template must set %s", key)
		}
	}
	for _, key := range []string{"YARD_UID", "YARD_GID", "YARD_DOCKER_GID"} {
		if commented, found := windows[key]; !found || !commented {
			t.Errorf("the Windows .env template must leave %s commented out", key)
		}
	}
}

func TestComposeVariablesHaveEnvTemplateSettings(t *testing.T) {
	// The image has a default only for a build of the project, and .env does not set it.
	notInEnv := []string{"YARD_DASHBOARD_IMAGE"}
	for _, pair := range [][2]string{{"compose.yaml", ".env.example"}, {"compose.windows.yaml", ".env.windows.example"}} {
		keys := settingKeys(t, pair[1])
		for variable := range composeVariables(t, pair[0]) {
			if _, found := keys[variable]; !found && !slices.Contains(notInEnv, variable) {
				t.Errorf("%s reads %s, but %s does not name it", pair[0], variable, pair[1])
			}
		}
	}
}

type composeService struct {
	Image       string            `yaml:"image"`
	NetworkMode string            `yaml:"network_mode"`
	Restart     string            `yaml:"restart"`
	Profiles    []string          `yaml:"profiles"`
	Environment map[string]string `yaml:"environment"`
}

func readComposeServices(t *testing.T, name string) map[string]composeService {
	t.Helper()
	source, err := os.ReadFile(name)
	if err != nil {
		t.Fatal(err)
	}
	var file struct {
		Services map[string]composeService `yaml:"services"`
	}
	if err := yaml.Unmarshal(source, &file); err != nil {
		t.Fatal(err)
	}
	return file.Services
}

// The Windows Compose file is a copy of compose.yaml with the changes that Docker Desktop needs. A setting
// that the two share must stay the same, unless this test names the difference and gives its reason.
func TestWindowsComposeKeepsTheSharedKeysOfUnixCompose(t *testing.T) {
	unix, windows := readComposeServices(t, "compose.yaml"), readComposeServices(t, "compose.windows.yaml")
	differentValues := map[string]string{
		"consul.YARD_BIND_ADDRESS": "Windows needs the LAN address to advertise. Unix lets Consul choose.",
	}
	windowsOnlyEnvironment := map[string]string{
		"dashboard.YARD_IPV4_ONLY":        "Java sockets on Docker Desktop are IPv4 only.",
		"dashboard.YARD_INSTANCE_HOST":    "The Instance host is separate from the Consul advertise address.",
		"traefik.YARD_DASHBOARD_PORT":     "The staging script reads the dashboard address itself.",
		"traefik.YARD_HTTP_PORT":          "The staging script reads the entry point port itself.",
		"traefik.YARD_TRAEFIK_ADMIN_PORT": "The staging script reads the admin port itself.",
	}
	for _, name := range slices.Sorted(maps.Keys(unix)) {
		t.Run(name, func(t *testing.T) {
			u := unix[name]
			w, found := windows[name]
			if !found {
				t.Fatalf("compose.windows.yaml has no %s service", name)
			}
			for field, pair := range map[string][2]string{
				"image":        {u.Image, w.Image},
				"network_mode": {u.NetworkMode, w.NetworkMode},
				"restart":      {u.Restart, w.Restart},
				"profiles":     {strings.Join(u.Profiles, ","), strings.Join(w.Profiles, ",")},
			} {
				if pair[0] != pair[1] {
					t.Errorf("%s %s: Unix %q, Windows %q", name, field, pair[0], pair[1])
				}
			}
			for key, value := range u.Environment {
				got, found := w.Environment[key]
				switch {
				case !found:
					t.Errorf("%s environment lacks %s", name, key)
				case got != value && differentValues[name+"."+key] == "":
					t.Errorf("%s environment %s: Unix %q, Windows %q", name, key, value, got)
				}
			}
			for key := range w.Environment {
				_, shared := u.Environment[key]
				if !shared && windowsOnlyEnvironment[name+"."+key] == "" {
					t.Errorf("%s environment has %s, which only Windows has: say why in this test", name, key)
				}
			}
		})
	}
}

// Every variable of compose.yaml that the Windows file also reads keeps its form, so a default does not drift.
func TestWindowsComposeKeepsTheVariableDefaultsOfUnixCompose(t *testing.T) {
	unix, windows := composeVariables(t, "compose.yaml"), composeVariables(t, "compose.windows.yaml")
	different := map[string]string{
		"YARD_BIND_ADDRESS": "Windows requires the LAN address.",
	}
	for variable, forms := range unix {
		other, found := windows[variable]
		if !found || different[variable] != "" {
			continue
		}
		for _, form := range forms {
			if !slices.Contains(other, form) {
				t.Errorf("%s: Unix has ${%s%s}, Windows has %v", variable, variable, form, other)
			}
		}
	}
}
