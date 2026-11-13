// Package envfile reads and changes the text of a Workstation's .env file. It keeps every other
// line, and so every comment, as it is.
package envfile

import (
	"fmt"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/templates"
)

type File struct {
	lines []string
}

func Parse(text string) *File {
	return &File{lines: strings.Split(strings.TrimSuffix(strings.ReplaceAll(text, "\r\n", "\n"), "\n"), "\n")}
}

// Get is the value after the first "key=", or an empty text when no line sets the key.
func (f *File) Get(key string) string {
	for _, line := range f.lines {
		if value, ok := strings.CutPrefix(line, key+"="); ok {
			return value
		}
	}
	return ""
}

// Set replaces the line of the key, which may be commented out. It fails when the file has no such
// line, so a setting that the template lacks is never added by accident.
func (f *File) Set(key, value string) error {
	for i, line := range f.lines {
		if strings.HasPrefix(strings.TrimPrefix(line, "#"), key+"=") {
			f.lines[i] = key + "=" + value
			return nil
		}
	}
	return fmt.Errorf(".env has no setting %s", key)
}

// FillIfEmpty sets the key to found, unless found is empty or the key already has a value.
func (f *File) FillIfEmpty(key, found string) error {
	if strings.TrimSpace(f.Get(key)) == "" && found != "" {
		return f.Set(key, found)
	}
	return nil
}

// Settings are the key and value of each line that sets one, in file order. A comment and a line
// without "=" are left out.
func (f *File) Settings() [][2]string {
	var settings [][2]string
	for _, line := range f.lines {
		key, value, ok := strings.Cut(line, "=")
		if ok && !strings.HasPrefix(key, "#") {
			settings = append(settings, [2]string{key, value})
		}
	}
	return settings
}

func (f *File) Text() string {
	return strings.Join(f.lines, "\n") + "\n"
}

// ValueOrDefault is the value of the key, or the value of the template when the file leaves it
// empty.
func (f *File) ValueOrDefault(key string) string {
	if value := f.Get(key); strings.TrimSpace(value) != "" {
		return value
	}
	return Setting(templates.Env(), key, "")
}

// Setting is the value of the key in text, or fallback when no line sets it or its value is empty.
func Setting(text, key, fallback string) string {
	for line := range strings.SplitSeq(text, "\n") {
		line = strings.TrimSuffix(line, "\r")
		if value, ok := strings.CutPrefix(line, key+"="); ok {
			if strings.TrimSpace(value) != "" {
				return value
			}
			return fallback
		}
	}
	return fallback
}
