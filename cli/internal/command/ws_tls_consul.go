package command

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
	"github.com/spf13/cobra"
)

func traefikOff(env *envfile.File) bool {
	return strings.Trim(env.Get("YARD_TRAEFIK_ENABLED"), "\"'") == "false"
}

// writeConsulTLS turns Consul TLS on. Only ws yard share and ws yard join do it.
func writeConsulTLS(folder string) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	if err := root.MkdirAll("consul.d", 0700); err != nil {
		return err
	}
	return root.WriteFile(filepath.Join("consul.d", "10-tls.hcl"), []byte(templates.ConsulTLS()), 0600)
}

const consulTLSOnWarning = "Every Workstation in the Yard needs Consul TLS."

// installConsul checks a Consul certificate, copies it to tls/consul/ and turns Consul TLS on.
// The caller saves .env.
func installConsul(cmd *cobra.Command, folder string, env *envfile.File, caPEM, certPEM, keyPEM []byte) error {
	if err := tlsca.CheckConsul(caPEM, certPEM, keyPEM, strings.Trim(env.Get("YARD_WORKSTATION"), "\"'")); err != nil {
		return err
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	if err := writeRole(root, roleDir("", roleConsul), roleConsul, caPEM, certPEM, keyPEM); err != nil {
		return err
	}
	if err := writeConsulTLS(folder); err != nil {
		return err
	}
	fmt.Fprintf(cmd.OutOrStdout(), "Consul certificate installed in %s.\nConsul TLS is on. %s\n", roleDir(folder, roleConsul), consulTLSOnWarning)
	return nil
}
