package command

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
)

func protectWorkstationFolder(folder string) error {
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
			return nil
		}
		mode := os.FileMode(0600)
		if entry.IsDir() {
			mode = 0700
		}
		if err := root.Chmod(name, mode); err != nil {
			return fmt.Errorf("could not protect %s: %w", path, err)
		}
		return nil
	})
}
