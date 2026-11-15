package command

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"runtime"
)

// protectWorkstationFolder checks the folder's storage, then makes the whole folder private. A caller
// that protects the folder more than once checks the storage first, and calls protectWorkstationTree.
func protectWorkstationFolder(folder string) error {
	if err := validateWorkstationStorage(folder); err != nil {
		return err
	}
	return protectWorkstationTree(folder)
}

func protectWorkstationTree(folder string) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return fmt.Errorf("could not read %s: %w", folder, err)
	}
	defer func() { _ = root.Close() }()
	return fs.WalkDir(root.FS(), ".", func(name string, entry fs.DirEntry, err error) error {
		path := filepath.Join(folder, name)
		if err != nil {
			return fmt.Errorf("could not read %s: %w", path, err)
		}
		// Do not change permissions outside the Workstation Folder through a link.
		if entry.Type()&os.ModeSymlink != 0 {
			if runtime.GOOS == "windows" {
				return fmt.Errorf("cannot protect %s: remove the link or junction from the Workstation Folder", path)
			}
			return nil
		}
		mode := os.FileMode(0600)
		if entry.IsDir() {
			mode = 0700
		}
		if err := protectRootEntry(root, name, mode); err != nil {
			return fmt.Errorf("could not protect %s: %w", path, err)
		}
		return nil
	})
}
