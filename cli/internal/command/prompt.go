package command

import (
	"bufio"
	"fmt"
	"io"
	"strings"

	"github.com/spf13/cobra"
)

// prompter asks the questions of a command that has no flag for an answer. Without a terminal it
// asks nothing, and the command uses its defaults.
type prompter struct {
	out      io.Writer
	in       *bufio.Reader
	terminal bool
}

func newPrompter(cmd *cobra.Command, options *Options) *prompter {
	return &prompter{out: cmd.OutOrStdout(), in: bufio.NewReader(options.Process.Input()), terminal: options.Process.IsTerminal()}
}

// text asks until check accepts the answer. An empty answer is the default.
func (p *prompter) text(question, fallback string, check func(string) error) (string, error) {
	for {
		offered := ""
		if fallback != "" {
			offered = " [" + fallback + "]"
		}
		fmt.Fprint(p.out, question+offered+": ")
		line, err := p.in.ReadString('\n')
		if err != nil && (err != io.EOF || len(line) == 0) {
			fmt.Fprintln(p.out)
			return "", fmt.Errorf("the input ended before %q was answered", question)
		}
		answer := strings.TrimSpace(line)
		if answer == "" {
			answer = fallback
		}
		if check == nil {
			return answer, nil
		}
		if err := check(answer); err != nil {
			fmt.Fprintln(p.out, err)
			continue
		}
		return answer, nil
	}
}

func (p *prompter) yesNo(question string, fallback bool) (bool, error) {
	offered := "[Y/n]"
	if !fallback {
		offered = "[y/N]"
	}
	for {
		answer, err := p.text(question+" "+offered, "", nil)
		if err != nil {
			return false, err
		}
		switch strings.ToLower(answer) {
		case "":
			return fallback, nil
		case "y", "yes":
			return true, nil
		case "n", "no":
			return false, nil
		}
		fmt.Fprintln(p.out, "Answer y or n.")
	}
}
