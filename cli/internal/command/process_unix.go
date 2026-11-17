//go:build !windows

package command

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
	"syscall"
)

const (
	// defaultEditor opens when EDITOR is empty.
	defaultEditor = "vi"
	// editorVariable is how a Unix shell writes the EDITOR variable.
	editorVariable = "$EDITOR"
)

// quoteShell writes a value for the shell of this computer.
func quoteShell(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "'\"'\"'") + "'"
}

func (systemProcess) IsTerminal() bool {
	check := exec.Command("sh", "-c", "test -t 0")
	check.Stdin = os.Stdin
	return check.Run() == nil
}

func (systemProcess) Edit(editor, path string) error {
	// #nosec G204 -- EDITOR is the user's command; the file path stays a separate argument.
	edit := exec.Command("sh", "-c", editor+" \"$1\"", "sh", path)
	edit.Stdin = os.Stdin
	edit.Stdout = os.Stdout
	edit.Stderr = os.Stderr
	return edit.Run()
}

func (systemProcess) ID(flag string) string {
	// #nosec G204 -- Initialization passes only the fixed id flags -u and -g.
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

// socketGroup is the group ID that owns the Docker socket, or an empty string.
func socketGroup(info os.FileInfo) string {
	if stat, ok := info.Sys().(*syscall.Stat_t); ok {
		return fmt.Sprint(stat.Gid)
	}
	return ""
}
