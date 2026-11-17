package command

import (
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"strings"
)

// organizeCADirectory moves what the Workstation CA signed from ca/certs/<name>/ and ca/clients/ to one
// directory for each group: ca/yard/<workstation>/, ca/service/<name>/ and ca/client/. The CA files stay
// in ca/. A file that already has a place in the new layout stays where it is, and so does a file that
// the migration does not know.
func organizeCADirectory(folder string, out io.Writer) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	moved := false
	names, err := readDirIfExists(root, "ca/certs")
	if err != nil {
		return err
	}
	for _, owner := range names {
		files, err := readDirIfExists(root, path.Join("ca/certs", owner))
		if err != nil {
			return err
		}
		for _, file := range files {
			group := ""
			switch {
			case strings.HasPrefix(file, serviceDir):
				group = serviceDir
			case strings.HasPrefix(file, roleConsul), strings.HasPrefix(file, "https"), strings.HasPrefix(file, "mtls"):
				group = yardDir
			default:
				continue
			}
			if err := moveCAFile(root, path.Join("ca/certs", owner, file), path.Join("ca", group, owner, file), &moved); err != nil {
				return err
			}
		}
		_ = root.Remove(path.Join("ca/certs", owner))
	}
	_ = root.Remove("ca/certs")
	clients, err := readDirIfExists(root, "ca/clients")
	if err != nil {
		return err
	}
	for _, file := range clients {
		if err := moveCAFile(root, path.Join("ca/clients", file), path.Join("ca", clientDir, file), &moved); err != nil {
			return err
		}
	}
	_ = root.Remove("ca/clients")
	if moved {
		fmt.Fprintln(out, "Note: the files of the Workstation CA have new places.\n  - ca/yard/<workstation>/ holds the Consul, HTTPS and mutual TLS certificates.\n  - ca/service/<name>/ holds the certificates of Services.\n  - ca/client/ holds the Client Certificates, as .p12 files.")
	}
	return nil
}

func readDirIfExists(root *os.Root, name string) ([]string, error) {
	entries, err := fs.ReadDir(root.FS(), name)
	if os.IsNotExist(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(entries))
	for _, entry := range entries {
		names = append(names, entry.Name())
	}
	return names, nil
}

func moveCAFile(root *os.Root, from, to string, moved *bool) error {
	info, err := root.Lstat(from)
	if err != nil || !info.Mode().IsRegular() {
		return err
	}
	if _, err := root.Lstat(to); err == nil {
		return nil
	} else if !os.IsNotExist(err) {
		return err
	}
	if err := root.MkdirAll(path.Dir(to), 0700); err != nil {
		return err
	}
	if err := root.Rename(from, to); err != nil {
		return err
	}
	*moved = true
	return nil
}
