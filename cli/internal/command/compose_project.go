package command

import (
	"crypto/sha256"
	"encoding/hex"
	"path/filepath"
	"strings"
)

const (
	// projectNameFallback is the readable part of a project name when the folder name has no valid character.
	projectNameFallback = "lvh-yard"
	projectNameMaxLen   = 40
)

// composeProjectName is the Compose project name of a Workstation Folder. Compose accepts only lowercase letters,
// digits, - and _, and the name must start with a letter or a digit. Compose names the consul-data volume after the
// project, so two folders must never give one name. The name is the folder name, made valid, and a short code of the
// whole path: folders with one name under different parents give different projects. The path is lowercased for the
// code because Windows paths are not case sensitive, so C:\Work and c:\work are one folder.
func composeProjectName(folder string) string {
	path, err := filepath.Abs(folder)
	if err != nil {
		path = folder
	}
	path = filepath.Clean(path)
	code := sha256.Sum256([]byte(strings.ToLower(path)))
	return validProjectWord(filepath.Base(path)) + "-" + hex.EncodeToString(code[:4])
}

// validProjectWord lowercases the text, writes - for each run of characters that Compose refuses, and keeps it short.
// It gives projectNameFallback when no valid character remains.
func validProjectWord(text string) string {
	var word strings.Builder
	for _, r := range strings.ToLower(text) {
		switch {
		case (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9'):
			word.WriteRune(r)
		case word.Len() > 0 && !strings.HasSuffix(word.String(), "-"):
			// Every other character, a space and _ too, is one -. A name cannot start with a separator.
			word.WriteByte('-')
		}
	}
	valid := strings.TrimRight(word.String(), "-")
	if len(valid) > projectNameMaxLen {
		valid = strings.TrimRight(valid[:projectNameMaxLen], "-")
	}
	if valid == "" {
		return projectNameFallback
	}
	return valid
}
