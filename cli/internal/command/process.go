package command

import (
	"errors"
	"io"
	"os"
	"os/exec"
	"runtime"

	"golang.org/x/term"
)

// Process holds what a command asks of the computer. The parts that differ between systems are in
// process_unix.go and process_windows.go.
type Process interface {
	Getenv(key string) string
	Input() io.Reader
	IsTerminal() bool
	// ReadSecret reads one line without showing it. Without a terminal there is nothing to hide,
	// so it gives the line that readLine reads.
	ReadSecret(readLine func() (string, error)) (string, error)
	LANAddress() (string, error)
	Edit(editor, path string) error
	Run(name string, args []string, dir string, stdout, stderr io.Writer) (int, error)
	// OS is the Go name of the operating system that runs the CLI, such as linux or windows.
	OS() string
	// ID returns a number, or an empty string when id fails or prints other text.
	ID(flag string) string
}

func SystemProcess() Process {
	return systemProcess{}
}

type systemProcess struct{}

func (systemProcess) Getenv(key string) string {
	return os.Getenv(key)
}

func (systemProcess) OS() string {
	return runtime.GOOS
}

func (systemProcess) Input() io.Reader {
	return os.Stdin
}

func (systemProcess) ReadSecret(readLine func() (string, error)) (string, error) {
	fd := int(os.Stdin.Fd())
	if !term.IsTerminal(fd) {
		return readLine()
	}
	secret, err := term.ReadPassword(fd)
	return string(secret), err
}

func (systemProcess) Run(name string, args []string, dir string, stdout, stderr io.Writer) (int, error) {
	// #nosec G204 -- CLI actions select docker, ssh or scp; arguments are passed without a shell.
	command := exec.Command(name, args...)
	command.Dir = dir
	command.Stdin = os.Stdin
	command.Stdout = stdout
	command.Stderr = stderr
	err := command.Run()
	if exit, ok := errors.AsType[*exec.ExitError](err); ok {
		return exit.ExitCode(), nil
	}
	return 0, err
}
