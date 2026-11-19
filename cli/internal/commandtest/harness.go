package commandtest

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/command"
	"github.com/spf13/cobra"
)

type Result struct {
	Stdout   string
	Stderr   string
	ExitCode int
}

type Harness struct {
	Process *Process
	// Dashboard stands in for the dashboard. With none, commands use the --dashboard address.
	Dashboard command.Dashboard
	Version   string
	Groups    []func(*command.Options) *cobra.Command
	// Releases is the address of a fake GitHub releases server for update. With none, update would reach GitHub.
	Releases string
	// Executable is the file that update replaces.
	Executable string
	// ConsulHTTP is the client that ws yard peers asks Consul with. With none, it uses the real one.
	ConsulHTTP *http.Client
}

func New(t testing.TB) *Harness {
	t.Helper()
	env := map[string]string{}
	UseHome(env, t.TempDir())
	return &Harness{Process: &Process{Env: env, LANIP: defaultLANIP, System: "linux"}}
}

func (h *Harness) Run(args ...string) Result {
	var stdout, stderr bytes.Buffer
	h.Process.input = strings.NewReader(h.Process.Stdin)
	options := command.Options{
		Version: h.Version,
		Process: h.Process,
		Out:     &stdout,
		Err:     &stderr,
	}
	if h.Dashboard != nil {
		options.Dashboard = func(string) command.Dashboard { return h.Dashboard }
	}
	options.Releases = h.Releases
	options.ConsulHTTP = h.ConsulHTTP
	if h.Executable != "" {
		options.Executable = func() (string, error) { return h.Executable, nil }
	}
	root := command.NewRoot(options, h.Groups...)
	exitCode := command.Run(root, args)
	return Result{Stdout: stdout.String(), Stderr: stderr.String(), ExitCode: exitCode}
}

type Process struct {
	Env      map[string]string
	Stdin    string
	Terminal bool
	// SecretReads counts the lines that were read without being shown.
	SecretReads int
	LANIP       string
	LANError    error
	Editor      func(editor, path string) error
	Identity    func(flag string) string
	Commands    []Command
	// System is the operating system that the CLI reports. New sets linux, so a test does not
	// depend on the computer that runs it. Set windows to test the native Windows CLI.
	System     string
	RunCommand func(name string, args []string, dir string, stdout, stderr io.Writer) (int, error)

	input io.Reader
}

func (p *Process) Getenv(key string) string {
	return p.Env[key]
}

func (p *Process) OS() string {
	return p.System
}

func (p *Process) Input() io.Reader {
	return p.input
}

func (p *Process) IsTerminal() bool {
	return p.Terminal
}

// ReadSecret reads the next line of Stdin and counts it. A test shows nothing either way.
func (p *Process) ReadSecret(readLine func() (string, error)) (string, error) {
	p.SecretReads++
	return readLine()
}

func (p *Process) LANAddress() (string, error) {
	return p.LANIP, p.LANError
}

func (p *Process) Edit(editor, path string) error {
	if p.Editor == nil {
		return fmt.Errorf("unexpected editor call: %s %s", editor, path)
	}
	return p.Editor(editor, path)
}

func (p *Process) ID(flag string) string {
	if p.Identity == nil {
		return ""
	}
	return p.Identity(flag)
}

type Command struct {
	Name string
	Args []string
	Dir  string
}

func (p *Process) Run(name string, args []string, dir string, stdout, stderr io.Writer) (int, error) {
	p.Commands = append(p.Commands, Command{Name: name, Args: append([]string(nil), args...), Dir: dir})
	if p.RunCommand != nil {
		return p.RunCommand(name, args, dir, stdout, stderr)
	}
	return 0, nil
}
