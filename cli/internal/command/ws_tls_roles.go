package command

import (
	"os"
	"path/filepath"
	"strings"
)

// Consul has its own certificate, from the main CA of the Yard, and its own folder: tls/consul. Traefik
// has its own folder too, tls/traefik, with one set of files for each of its three parts (see
// ws_tls_traefik.go). Compose mounts each folder only in its own container.
const roleConsul = "consul"

// roleDir is where Compose mounts the files of one role: tls/consul or tls/traefik.
func roleDir(folder, role string) string {
	return filepath.Join(folder, "tls", role)
}

// roleFiles are the three files of the Consul role in its folder: the CA to check peers with, the
// certificate and the key.
func roleFiles(role string) [3]string {
	return [3]string{"ca.crt", role + ".crt", role + ".key"}
}

func writeRole(root *os.Root, dir, role string, caPEM, certPEM, keyPEM []byte) error {
	if err := root.MkdirAll(dir, 0700); err != nil {
		return err
	}
	for i, data := range [3][]byte{caPEM, certPEM, keyPEM} {
		if err := root.WriteFile(filepath.Join(dir, roleFiles(role)[i]), data, 0600); err != nil {
			return err
		}
	}
	return nil
}

// Where this Workstation's CA keeps what it signed, relative to the Workstation Folder. The CA is
// ca/ca.crt and ca/ca.key. The rest sits in one directory for each group.
const (
	// yardDir holds ca/yard/<workstation>/: the Consul, HTTPS and mutual TLS certificates of this
	// Workstation and of the others in the Yard.
	yardDir = "yard"
	// clientDir holds ca/client/<name>.p12: the Client Certificates of people.
	clientDir = "client"
	// serviceDir holds ca/service/<name>/: the certificates of Services.
	serviceDir = "service"
)

// issuedPath is where this Workstation's CA keeps what it signed under a name: a Workstation or a
// Service. The kind is consul, https, mtls or service, with -fullchain after service for the chain.
func issuedPath(folder, name, kind, extension string) string {
	group := yardDir
	if strings.HasPrefix(kind, "service") {
		group = serviceDir
	}
	return filepath.Join(folder, "ca", group, name, kind+extension)
}

// readSignedTLSRole reads what this Workstation's CA signed under a name. A certificate that was
// never signed is an error that satisfies os.IsNotExist.
func readSignedTLSRole(folder, name, kind string) (certPEM, keyPEM []byte, err error) {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return nil, nil, err
	}
	defer func() { _ = root.Close() }()
	if certPEM, err = root.ReadFile(issuedPath("", name, kind, ".crt")); err != nil {
		return nil, nil, err
	}
	if keyPEM, err = root.ReadFile(issuedPath("", name, kind, ".key")); err != nil {
		return nil, nil, err
	}
	return certPEM, keyPEM, nil
}
