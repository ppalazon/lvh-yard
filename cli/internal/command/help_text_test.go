package command_test

import (
	"io"
	"regexp"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/ppalazon/lvh-yard/cli/internal/commandtest"
	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
)

// The help text rules of CLAUDE.md, "CLI text".
const (
	maxShort = 50
	maxUsage = 70
)

var (
	longLabels  = []string{"Before:", "Does:", "Next:", "Note:"}
	labelInText = regexp.MustCompile(`^(Before|Does|Next|Note|Notes|Example|Examples|Usage):`)
	miseInText  = regexp.MustCompile(`\bmise\b`)
)

func visitCommands(cmd *cobra.Command, visit func(path string, cmd *cobra.Command)) {
	for _, child := range cmd.Commands() {
		if child.Hidden || child.Name() == "help" {
			continue
		}
		visit(child.CommandPath(), child)
		visitCommands(child, visit)
	}
}

// longProblems gives what is wrong with the labels of a Long text.
func longProblems(long string) []string {
	var problems []string
	next := 0
	for line := range strings.SplitSeq(long, "\n") {
		if !labelInText.MatchString(line) {
			continue
		}
		index := -1
		for i, label := range longLabels {
			if line == label {
				index = i
			}
		}
		switch {
		case index < 0:
			problems = append(problems, "the label line must be exactly one of "+strings.Join(longLabels, " ")+": "+line)
		case index < next:
			problems = append(problems, "the labels must come in the order "+strings.Join(longLabels, " ")+": "+line)
		default:
			next = index + 1
		}
	}
	return problems
}

func TestLongProblemsTellsGoodLabelsFromBad(t *testing.T) {
	if got := longProblems("Does a thing.\n\nDoes:\n  1. One\nNext:\n  lvh-yard status\nNote:\n  - A fact"); len(got) != 0 {
		t.Errorf("good labels gave %v", got)
	}
	if got := longProblems("Next:\n  x\nBefore:\n  y"); len(got) != 1 {
		t.Errorf("labels out of order gave %v", got)
	}
	if got := longProblems("Note: a fact on the label line"); len(got) != 1 {
		t.Errorf("a label with text gave %v", got)
	}
}

func TestHelpTextFollowsTheCLITextRules(t *testing.T) {
	root := command.NewRoot(command.Options{Process: &commandtest.Process{}, Out: io.Discard, Err: io.Discard})
	visitCommands(root, func(path string, cmd *cobra.Command) {
		problem := func(format string, args ...any) { t.Helper(); t.Errorf(path+": "+format, args...) }
		if len(cmd.Short) > maxShort {
			problem("Short has %d characters, at most %d: %q", len(cmd.Short), maxShort, cmd.Short)
		}
		if !strings.HasSuffix(cmd.Short, ".") {
			problem("Short must end with a period: %q", cmd.Short)
		}
		if first, _, _ := strings.Cut(cmd.Short, " "); !strings.HasSuffix(first, "s") {
			problem("Short must be in the third person, such as \"Lists ...\": %q", cmd.Short)
		}
		for _, text := range longProblems(cmd.Long) {
			problem("Long: %s", text)
		}
		if miseInText.MatchString(cmd.Short + "\n" + cmd.Long) {
			problem("help text names mise, which Workstations do not have")
		}
		cmd.LocalFlags().VisitAll(func(flag *pflag.Flag) {
			if len(flag.Usage) > maxUsage {
				problem("--%s usage has %d characters, at most %d: %q", flag.Name, len(flag.Usage), maxUsage, flag.Usage)
			}
			if miseInText.MatchString(flag.Usage) {
				problem("--%s usage names mise, which Workstations do not have", flag.Name)
			}
		})
	})
}
