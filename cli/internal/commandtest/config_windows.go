package commandtest

import "path/filepath"

// HomeFolder is the Workstation Folder that a command finds when the user's home is home and no
// other setting names a folder. Windows reads LOCALAPPDATA, so UseHome sets it under home.
func HomeFolder(home string) string { return RootFolder(filepath.Join(home, "AppData", "Local")) }

// RootFolder is the Workstation Folder under the config root (LOCALAPPDATA).
func RootFolder(root string) string { return filepath.Join(root, "lvh-yard", "workstation") }

// UseHome makes home the user's home and returns HomeFolder(home).
func UseHome(env map[string]string, home string) string {
	env["HOME"] = home
	env["LOCALAPPDATA"] = filepath.Join(home, "AppData", "Local")
	return HomeFolder(home)
}

// UseConfigRoot sets the config root and returns RootFolder(root).
func UseConfigRoot(env map[string]string, root string) string {
	env["LOCALAPPDATA"] = root
	return RootFolder(root)
}
