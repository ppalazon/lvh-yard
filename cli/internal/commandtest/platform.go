package commandtest

import (
	"runtime"
	"testing"
)

// UnixModes is false on Windows. NTFS has access control lists, and the mode bits that Go reports
// there (666, 777) say nothing about who can read a file.
const UnixModes = runtime.GOOS != "windows"

// SkipOnWindows skips a test whose point is Unix only, such as a symbolic link that escapes the
// Workstation Folder. Say in the reason which Windows test covers the same rule.
func SkipOnWindows(t testing.TB, reason string) {
	t.Helper()
	if runtime.GOOS == "windows" {
		t.Skip(reason)
	}
}
