package command

import (
	"io"
	"os"
	"os/exec"
	"strings"
)

type Process interface {
	Getenv(key string) string
	Input() io.Reader
	IsTerminal() bool
	Edit(editor, path string) error
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

func (systemProcess) Input() io.Reader {
	return os.Stdin
}

func (systemProcess) IsTerminal() bool {
	check := exec.Command("sh", "-c", "test -t 0")
	check.Stdin = os.Stdin
	return check.Run() == nil
}

func (systemProcess) Edit(editor, path string) error {
	// The shell reads editor arguments; the file path stays a separate argument.
	edit := exec.Command("sh", "-c", editor+" \"$1\"", "sh", path)
	edit.Stdin = os.Stdin
	edit.Stdout = os.Stdout
	edit.Stderr = os.Stderr
	return edit.Run()
}

func (systemProcess) ID(flag string) string {
	printed, err := exec.Command("id", flag).Output()
	if err != nil {
		return ""
	}
	value := strings.TrimSpace(string(printed))
	for _, digit := range value {
		if digit < '0' || digit > '9' {
			return ""
		}
	}
	return value
}
