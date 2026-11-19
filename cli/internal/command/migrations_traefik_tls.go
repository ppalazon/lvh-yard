package command

import (
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
)

// splitTraefikTLS moves a Workstation Folder from one Traefik certificate that did everything to the three
// parts of Traefik TLS. The old files traefik.crt, traefik.key and ca.crt become https.crt, https.key and
// client-ca.crt. HTTPS used to turn on the check of Client Certificates too, so a folder with HTTPS on keeps
// that check on.
func splitTraefikTLS(folder string, out io.Writer) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	moved := false
	for from, to := range map[string]string{
		"tls/traefik/traefik.crt": "tls/traefik/https.crt",
		"tls/traefik/traefik.key": "tls/traefik/https.key",
		"tls/traefik/ca.crt":      "tls/traefik/client-ca.crt",
	} {
		if err := moveIfFree(root, from, to, &moved); err != nil {
			return err
		}
	}
	signed, err := filepath.Glob(filepath.Join(folder, "ca", "certs", "*", "traefik.*"))
	if err != nil {
		return err
	}
	for _, path := range signed {
		relative, err := filepath.Rel(folder, path)
		if err != nil {
			return err
		}
		to := filepath.Join(filepath.Dir(relative), "https"+filepath.Ext(relative))
		if err := moveIfFree(root, relative, to, &moved); err != nil {
			return err
		}
	}
	if _, err := root.Stat("traefik/dynamic/10-tls.yaml"); err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	if err := httpsPart.writeGenerated(folder); err != nil {
		return err
	}
	if err := clientPart.writeGenerated(folder); err != nil {
		return err
	}
	keptOn, err := keepClientCheckOn(root)
	if err != nil || !keptOn {
		return err
	}
	if moved {
		fmt.Fprintln(out, "Note: Traefik TLS now has three parts: https, client and mtls.\n  - The files in tls/traefik have new names: https.crt, https.key and client-ca.crt.\n  - The check of Client Certificates stays on, because HTTPS was on.\nNext:\n  Run \"lvh-yard ws compose recreate\" to apply it.")
	}
	return nil
}

// keepClientCheckOn turns the check of Client Certificates on in .env. It reports false when there is no .env.
func keepClientCheckOn(root *os.Root) (bool, error) {
	text, err := root.ReadFile(".env")
	if err != nil {
		if os.IsNotExist(err) {
			return false, nil
		}
		return false, err
	}
	env := envfile.Parse(string(text))
	clientPart.setOn(env, true)
	if err := root.WriteFile(".env", []byte(env.Text()), 0600); err != nil {
		return false, err
	}
	return true, nil
}

// moveIfFree renames a file, unless it is missing or the new name is taken.
func moveIfFree(root *os.Root, from, to string, moved *bool) error {
	if _, err := root.Stat(from); err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	if _, err := root.Stat(to); err == nil {
		return nil
	} else if !os.IsNotExist(err) {
		return err
	}
	if err := root.Rename(from, to); err != nil {
		return err
	}
	*moved = true
	return nil
}
