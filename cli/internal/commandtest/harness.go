package commandtest

import (
	"bytes"
	"fmt"
	"io"
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
	Version string
	Groups  []func(*command.Options) *cobra.Command
}

func New(t testing.TB) *Harness {
	t.Helper()
	return &Harness{
		Process: &Process{Env: map[string]string{"HOME": t.TempDir()}},
	}
}

func (h *Harness) Run(args ...string) Result {
	var stdout, stderr bytes.Buffer
	h.Process.input = strings.NewReader(h.Process.Stdin)
	root := command.NewRoot(command.Options{
		Version: h.Version,
		Process: h.Process,
		Out:     &stdout,
		Err:     &stderr,
	}, h.Groups...)
	exitCode := command.Run(root, args)
	return Result{Stdout: stdout.String(), Stderr: stderr.String(), ExitCode: exitCode}
}

type Process struct {
	Env      map[string]string
	Stdin    string
	Terminal bool
	Editor   func(editor, path string) error
	Identity func(flag string) string

	input io.Reader
}

func (p *Process) Getenv(key string) string {
	return p.Env[key]
}

func (p *Process) Input() io.Reader {
	return p.input
}

func (p *Process) IsTerminal() bool {
	return p.Terminal
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
