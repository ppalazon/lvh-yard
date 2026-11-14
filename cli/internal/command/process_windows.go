package command

import (
	"os"
	"os/exec"
	"strings"
	"syscall"
)

const (
	// defaultEditor opens when EDITOR is empty. Every Windows 11 has Notepad.
	defaultEditor = "notepad"
	// editorVariable is how PowerShell writes the EDITOR variable.
	editorVariable = "$env:EDITOR"
)

// quoteShell writes a value for PowerShell on this computer. PowerShell reads the typographic
// quotes ‘ ’ ‚ ‛ as a single quote too, so each of them is written twice.
func quoteShell(value string) string {
	var quoted strings.Builder
	quoted.WriteByte('\'')
	for _, r := range value {
		if strings.ContainsRune("'\u2018\u2019\u201a\u201b", r) {
			quoted.WriteRune(r)
		}
		quoted.WriteRune(r)
	}
	quoted.WriteByte('\'')
	return quoted.String()
}

// IsTerminal reports whether the input is a console. A pipe, a file and NUL have no console mode.
func (systemProcess) IsTerminal() bool {
	var mode uint32
	return syscall.GetConsoleMode(syscall.Handle(os.Stdin.Fd()), &mode) == nil
}

func (systemProcess) Edit(editor, path string) error {
	name, args := editorCommand(editor)
	// #nosec G204 -- EDITOR is the user's command; the file path stays a separate argument.
	edit := exec.Command(name, append(args, path)...)
	edit.Stdin = os.Stdin
	edit.Stdout = os.Stdout
	edit.Stderr = os.Stderr
	return edit.Run()
}

// editorCommand splits EDITOR into the executable and its arguments. A value that names an
// executable as a whole is that executable, so a path with spaces needs no quotes. In any other
// value, spaces separate the words, and double quotes keep a path with spaces in one word.
func editorCommand(editor string) (string, []string) {
	whole := strings.Trim(strings.TrimSpace(editor), `"`)
	if _, err := exec.LookPath(whole); err == nil {
		return whole, nil
	}
	var words []string
	var word strings.Builder
	quoted, started := false, false
	for _, r := range editor {
		switch {
		case r == '"':
			quoted, started = !quoted, true
		case (r == ' ' || r == '\t') && !quoted:
			if started {
				words = append(words, word.String())
				word.Reset()
				started = false
			}
		default:
			word.WriteRune(r)
			started = true
		}
	}
	if started {
		words = append(words, word.String())
	}
	if len(words) == 0 {
		return editor, nil
	}
	return words[0], words[1:]
}

// ID returns an empty string: Windows has no numeric user and group IDs.
func (systemProcess) ID(flag string) string {
	return ""
}

// socketGroup returns an empty string: a Windows file has no group ID.
func socketGroup(info os.FileInfo) string {
	return ""
}
