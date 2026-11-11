package command

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
)

func protectWorkstationFolder(folder string) error {
	return filepath.WalkDir(folder, func(path string, entry fs.DirEntry, err error) error {
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
		if err := os.Chmod(path, mode); err != nil {
			return fmt.Errorf("could not protect %s: %w", path, err)
		}
		return nil
	})
}
