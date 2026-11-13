package envfile_test

import (
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
)

const sample = "# Workstation\nYARD_WORKSTATION=ws-a\n#YARD_PEERS=10.0.0.2\nYARD_HTTP_PORT=\r\nYARD_BASE_DOMAIN=lvh.me\n"

func TestGetGivesTheValueOfTheFirstLineOfTheKey(t *testing.T) {
	file := envfile.Parse("A=1\nA=2\nB=x=y\n")
	for key, want := range map[string]string{"A": "1", "B": "x=y", "C": ""} {
		if got := file.Get(key); got != want {
			t.Errorf("Get(%s) = %q; want %q", key, got, want)
		}
	}
}

func TestGetDoesNotTakeACommentedOrLongerKey(t *testing.T) {
	file := envfile.Parse("#A=1\nAB=2\n")
	if got := file.Get("A"); got != "" {
		t.Fatalf("Get(A) = %q", got)
	}
}

func TestSetReplacesALineAndUncommentsIt(t *testing.T) {
	file := envfile.Parse(sample)
	if err := file.Set("YARD_PEERS", "10.0.0.9"); err != nil {
		t.Fatal(err)
	}
	if err := file.Set("YARD_WORKSTATION", "ws-b"); err != nil {
		t.Fatal(err)
	}
	want := "# Workstation\nYARD_WORKSTATION=ws-b\nYARD_PEERS=10.0.0.9\nYARD_HTTP_PORT=\nYARD_BASE_DOMAIN=lvh.me\n"
	if got := file.Text(); got != want {
		t.Fatalf("Text() = %q; want %q", got, want)
	}
}

func TestSetRefusesAKeyThatTheFileLacks(t *testing.T) {
	err := envfile.Parse(sample).Set("YARD_MISSING", "1")
	if err == nil || !strings.Contains(err.Error(), "YARD_MISSING") {
		t.Fatalf("Set error = %v", err)
	}
}

func TestTextKeepsTheLinesAndEndsWithOneNewline(t *testing.T) {
	for _, in := range []string{"A=1\nB=2", "A=1\nB=2\n"} {
		if got := envfile.Parse(in).Text(); got != "A=1\nB=2\n" {
			t.Errorf("Text() of %q = %q", in, got)
		}
	}
}

func TestFillIfEmptyFillsOnlyAnEmptyValueWithSomething(t *testing.T) {
	file := envfile.Parse("A=\nB=keep\nC=  \n")
	for _, call := range [][2]string{{"A", "new"}, {"B", "other"}, {"C", ""}} {
		if err := file.FillIfEmpty(call[0], call[1]); err != nil {
			t.Fatal(err)
		}
	}
	if got, want := file.Text(), "A=new\nB=keep\nC=  \n"; got != want {
		t.Fatalf("Text() = %q; want %q", got, want)
	}
	if err := file.FillIfEmpty("MISSING", "x"); err == nil {
		t.Fatal("a missing key was filled")
	}
}

func TestValueOrDefaultFallsBackToTheTemplate(t *testing.T) {
	file := envfile.Parse("YARD_HTTP_PORT=8080\nYARD_DASHBOARD_PORT=\n")
	if got := file.ValueOrDefault("YARD_HTTP_PORT"); got != "8080" {
		t.Errorf("own value = %q", got)
	}
	if got := file.ValueOrDefault("YARD_DASHBOARD_PORT"); got != "7780" {
		t.Errorf("template value = %q; want 7780", got)
	}
}

func TestSettingIsTheValueOrTheFallbackForNoneOrEmpty(t *testing.T) {
	text := "A=1\r\nB=\nC= \n"
	for key, want := range map[string]string{"A": "1", "B": "fallback", "C": "fallback", "D": "fallback"} {
		if got := envfile.Setting(text, key, "fallback"); got != want {
			t.Errorf("Setting(%s) = %q; want %q", key, got, want)
		}
	}
}

func TestSettingsListsTheActiveLinesInOrder(t *testing.T) {
	got := envfile.Parse("# note\nA=1\n\n#B=2\nnoequals\nC=x=y\n").Settings()
	want := [][2]string{{"A", "1"}, {"C", "x=y"}}
	if len(got) != len(want) || got[0] != want[0] || got[1] != want[1] {
		t.Fatalf("Settings() = %v; want %v", got, want)
	}
}
