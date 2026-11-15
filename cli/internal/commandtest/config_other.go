//go:build !windows

package commandtest

import "path/filepath"

// HomeFolder is the Workstation Folder that a command finds when the user's home is home and no
// other setting names a folder.
func HomeFolder(home string) string { return filepath.Join(home, ".config", "lvh-yard") }

// RootFolder is the Workstation Folder under the config root (XDG_CONFIG_HOME).
func RootFolder(root string) string { return filepath.Join(root, "lvh-yard") }

// UseHome makes home the user's home and returns HomeFolder(home).
func UseHome(env map[string]string, home string) string {
	env["HOME"] = home
	return HomeFolder(home)
}

// UseConfigRoot sets the config root and returns RootFolder(root).
func UseConfigRoot(env map[string]string, root string) string {
	env["XDG_CONFIG_HOME"] = root
	return RootFolder(root)
}
