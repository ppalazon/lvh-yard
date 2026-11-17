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
	out        io.Writer
	in         *bufio.Reader
	terminal   bool
	readSecret func(readLine func() (string, error)) (string, error)
}

func newPrompter(cmd *cobra.Command, options *Options) *prompter {
	return &prompter{out: cmd.OutOrStdout(), in: bufio.NewReader(options.Process.Input()), terminal: options.Process.IsTerminal(), readSecret: options.Process.ReadSecret}
}

// readLine reads one line, with its line break.
func (p *prompter) readLine(question string) (string, error) {
	line, err := p.in.ReadString('\n')
	if err != nil && (err != io.EOF || len(line) == 0) {
		fmt.Fprintln(p.out)
		return "", fmt.Errorf("the input ended before the answer to %q", question)
	}
	return line, nil
}

// secret asks for a password without showing what is typed. Unlike text, it keeps the spaces of
// the answer. An empty answer stays empty.
func (p *prompter) secret(question string) (string, error) {
	fmt.Fprint(p.out, question+": ")
	answer, err := p.readSecret(func() (string, error) {
		line, err := p.readLine(question)
		return strings.TrimRight(line, "\r\n"), err
	})
	// The terminal does not show the line break that ends the answer.
	fmt.Fprintln(p.out)
	return answer, err
}

// newSecret asks for a password to set. A second question checks a password that is not empty, and
// both are asked again after a mismatch.
func (p *prompter) newSecret(question string) (string, error) {
	for {
		first, err := p.secret(question)
		if err != nil || first == "" {
			return first, err
		}
		again, err := p.secret("Repeat the password")
		if err != nil {
			return "", err
		}
		if again == first {
			return first, nil
		}
		fmt.Fprintln(p.out, "The passwords differ. Try again.")
	}
}

// text asks until check accepts the answer. An empty answer is the default.
func (p *prompter) text(question, fallback string, check func(string) error) (string, error) {
	for {
		offered := ""
		if fallback != "" {
			offered = " [" + fallback + "]"
		}
		fmt.Fprint(p.out, question+offered+": ")
		line, err := p.readLine(question)
		if err != nil {
			return "", err
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
