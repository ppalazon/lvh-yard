package command

import (
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
)

const (
	caKeyPasswordFlag     = "ca-key-password"
	caKeyPasswordFileFlag = "ca-key-password-file"
)

// Usage text of the two flags that open ca.key. The names avoid "password": gosec G101 reads such a
// constant as a hardcoded credential.
const (
	caKeyFlagUsage     = "Sets the ca.key password (default: ask at a terminal)."
	caKeyFileFlagUsage = "Reads the ca.key password from a file, or from - (standard input)."
)

// addCAKeyPasswordFlags adds the two ways to give the password of ca.key without a question. The
// file keeps the password out of the process list, so a password manager can pipe it in:
// pass show lvh-yard/ca | lvh-yard ws tls ca create --ca-key-password-file -
func addCAKeyPasswordFlags(flags *pflag.FlagSet, passwordUsage, fileUsage string) {
	flags.String(caKeyPasswordFlag, "", passwordUsage)
	flags.String(caKeyPasswordFileFlag, "", fileUsage)
}

// givenCAKeyPassword reads the password from a flag. given is false when no flag was used. Every
// command that calls readCA must call addCAKeyPasswordFlags: a missing flag is a nil pointer here.
func givenCAKeyPassword(cmd *cobra.Command, p *prompter) (password string, given bool, err error) {
	direct, file := cmd.Flags().Lookup(caKeyPasswordFlag), cmd.Flags().Lookup(caKeyPasswordFileFlag)
	switch {
	case direct.Changed && file.Changed:
		return "", false, fmt.Errorf("use only one of --%s and --%s", caKeyPasswordFlag, caKeyPasswordFileFlag)
	case direct.Changed:
		return direct.Value.String(), true, nil
	case file.Changed:
		password, err = readCAKeyPasswordFile(p, file.Value.String())
		return password, true, err
	}
	return "", false, nil
}

// readCAKeyPasswordFile reads a password from a file, or from the standard input for -. One
// final line break is not part of the password, because a password manager prints one.
func readCAKeyPasswordFile(p *prompter, path string) (string, error) {
	var data []byte
	var err error
	if path == "-" {
		// Through the prompter's reader, which may already hold some of the input.
		data, err = io.ReadAll(p.in)
		path = "the standard input"
	} else if err = checkPrivateInput(path); err == nil {
		// #nosec G304 -- The user selects the file with --ca-key-password-file.
		data, err = os.ReadFile(path)
	}
	if err != nil {
		return "", fmt.Errorf("Cannot read the CA key password from %s: %w", path, err)
	}
	password := strings.TrimSuffix(strings.TrimSuffix(string(data), "\n"), "\r")
	if password == "" {
		return "", fmt.Errorf("%s holds no password", path)
	}
	return password, nil
}

// askCAKeyUnlock gives the password of a CA key: from a flag, or the answer to a question.
func askCAKeyUnlock(cmd *cobra.Command, p *prompter) (string, error) {
	if password, given, err := givenCAKeyPassword(cmd, p); given || err != nil {
		return password, err
	}
	if !p.terminal {
		return "", fmt.Errorf("the CA key has a password. Give it with --%s or --%s", caKeyPasswordFlag, caKeyPasswordFileFlag)
	}
	for {
		password, err := p.secret("Password of the CA key")
		if err != nil || password != "" {
			return password, err
		}
		fmt.Fprintln(p.out, "Give the password of the CA key.")
	}
}

// askCAKeyPassword gives the password for a new CA key: from a flag, or the answer to a
// question that is asked twice. Empty, the default, leaves the key without one.
func askCAKeyPassword(cmd *cobra.Command, p *prompter) (string, error) {
	if password, given, err := givenCAKeyPassword(cmd, p); given || err != nil {
		return password, err
	}
	if !p.terminal {
		return "", nil
	}
	return p.newSecret("Password of the CA key (empty for none)")
}
