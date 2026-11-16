package command

import (
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

// composeProjectRule is what Compose accepts: lowercase letters, digits, - and _, starting with a letter or a digit.
var composeProjectRule = regexp.MustCompile(`^[a-z0-9][a-z0-9_-]*$`)

func TestValidProjectWord(t *testing.T) {
	for _, test := range []struct{ name, folder, want string }{
		{"plain", "workstation", "workstation"},
		{"uppercase", "My-Yard", "my-yard"},
		{"spaces", "My  Work Station", "my-work-station"},
		{"apostrophe", "O'Brien's yard", "o-brien-s-yard"},
		{"underscore", "my_yard", "my-yard"},
		{"accent", "Café", "caf"},
		{"unicode between ASCII", "yard 日本 two", "yard-two"},
		{"leading separators", "--.yard", "yard"},
		{"trailing separators", "yard. -", "yard"},
		{"digits first", "2nd yard", "2nd-yard"},
		{"all Unicode", "日本語", "lvh-yard"},
		{"only separators", " -_. ", "lvh-yard"},
		{"empty", "", "lvh-yard"},
		{"long", strings.Repeat("a", 39) + " " + strings.Repeat("b", 30), strings.Repeat("a", 39)},
	} {
		t.Run(test.name, func(t *testing.T) {
			if got := validProjectWord(test.folder); got != test.want || !composeProjectRule.MatchString(got) {
				t.Fatalf("validProjectWord(%q) = %q, want %q", test.folder, got, test.want)
			}
		})
	}
}

func TestComposeProjectNameIsValidAndStable(t *testing.T) {
	root := t.TempDir()
	for _, base := range []string{"workstation", "O'Brien café 日本", "日本語", "UPPER Case"} {
		folder := filepath.Join(root, base)
		name := composeProjectName(folder)
		if !composeProjectRule.MatchString(name) {
			t.Errorf("composeProjectName(%q) = %q is not a valid project name", folder, name)
		}
		if again := composeProjectName(folder); again != name {
			t.Errorf("composeProjectName(%q) changed: %q, then %q", folder, name, again)
		}
	}
}

// Compose names the consul-data volume after the project, so two folders must not give one name.
func TestComposeProjectNameDiffersForFoldersWithOneName(t *testing.T) {
	root := t.TempDir()
	for _, base := range []string{"workstation", "日本語"} {
		first, second := composeProjectName(filepath.Join(root, "a", base)), composeProjectName(filepath.Join(root, "b", base))
		if first == second {
			t.Errorf("folders named %q under different parents share the project %q", base, first)
		}
		prefix := validProjectWord(base) + "-"
		if !strings.HasPrefix(first, prefix) || !strings.HasPrefix(second, prefix) {
			t.Errorf("projects %q and %q do not start with the folder name %q", first, second, prefix)
		}
	}
}

func TestComposeProjectNameIgnoresPathCase(t *testing.T) {
	root := t.TempDir()
	if upper, lower := composeProjectName(filepath.Join(root, "Yard")), composeProjectName(filepath.Join(root, "yard")); upper != lower {
		t.Errorf("one Windows folder gives two projects: %q and %q", upper, lower)
	}
}

func TestComposeProjectNameUsesTheAbsolutePath(t *testing.T) {
	t.Chdir(t.TempDir())
	relative, absolute := composeProjectName("yard"), composeProjectName(mustAbs(t, "yard"))
	if relative != absolute {
		t.Errorf("relative folder gives %q, absolute gives %q", relative, absolute)
	}
}

func mustAbs(t *testing.T, path string) string {
	t.Helper()
	absolute, err := filepath.Abs(path)
	if err != nil {
		t.Fatal(err)
	}
	return absolute
}
