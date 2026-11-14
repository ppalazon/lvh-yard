//go:build !windows

package command

import (
	"os"
	"path/filepath"
	"strings"
)

func defaultWorkstationFolder(process Process) string {
	if folder := process.Getenv("XDG_CONFIG_HOME"); strings.TrimSpace(folder) != "" {
		return filepath.Join(folder, "lvh-yard")
	}
	home := process.Getenv("HOME")
	if home == "" {
		home, _ = os.UserHomeDir()
	}
	return filepath.Join(home, ".config", "lvh-yard")
}

func validateWorkstationStorage(string) error           { return nil }
func validateOutputPath(string) error                   { return nil }
func validateOutputName(string) error                   { return nil }
func validateNativeListener(string, string) error       { return nil }
func protectFile(file *os.File, mode os.FileMode) error { return file.Chmod(mode) }
func protectRootEntry(root *os.Root, name string, mode os.FileMode) error {
	return root.Chmod(name, mode)
}
