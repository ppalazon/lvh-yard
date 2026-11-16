//go:build !windows

package command

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
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

// lanAddressRequired is false: Consul finds its own address, so ws init asks for one only with --address.
const lanAddressRequired = false

// platformWorkstationFiles are the files that only this platform's ws init writes. Unix has none.
func platformWorkstationFiles() []workstationFile { return nil }

// platformUpgradeServices are the services whose image a ws init upgrade changes only on this platform.
var platformUpgradeServices []string

// protectNewFolder does nothing: on Unix, MkdirAll makes a new folder 0700, and ws init must leave the modes of an
// existing folder alone until it writes.
func protectNewFolder(string) error { return nil }

// linkInFolderError is nil: Unix leaves a link in the Workstation Folder alone.
func linkInFolderError(string) error { return nil }

// fillPlatformEnv gives the identity of this computer to the user and group settings of .env, which the Compose file
// reads.
func fillPlatformEnv(env *envfile.File, options *Options, _, dockerSocket string) error {
	for _, found := range [][2]string{
		{"YARD_UID", options.Process.ID("-u")},
		{"YARD_GID", options.Process.ID("-g")},
		{"YARD_DOCKER_GID", dockerGroup(dockerSocket)},
	} {
		if err := env.FillIfEmpty(found[0], found[1]); err != nil {
			return err
		}
	}
	return nil
}

func dockerGroup(socket string) string {
	info, err := os.Stat(socket)
	if err != nil {
		return ""
	}
	return socketGroup(info)
}

// dockerGroupWarning is a warning when the dashboard has no Docker group to join, or an empty text.
func dockerGroupWarning(env *envfile.File, dockerSocket string) string {
	if strings.TrimSpace(env.Get("YARD_DOCKER_GID")) != "" {
		return ""
	}
	return fmt.Sprintf("Warning: no Docker socket at %s, so YARD_DOCKER_GID in .env is empty. Set it to the group of /var/run/docker.sock, or the dashboard will not start.", dockerSocket)
}
