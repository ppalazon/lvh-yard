package command

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
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

func writeTraefikTLS(folder string) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	if err := root.MkdirAll(filepath.Join("traefik", "dynamic"), 0700); err != nil {
		return err
	}
	return root.WriteFile(filepath.Join("traefik", "dynamic", "10-tls.yaml"), []byte(templates.TraefikTLS()), 0600)
}

// turnOnRole writes the generated option file of a role. Traefik keeps an HTTPS port that was set
// before.
func turnOnRole(folder, role string, env *envfile.File) error {
	if role == roleConsul {
		return writeConsulTLS(folder)
	}
	if err := writeTraefikTLS(folder); err != nil {
		return err
	}
	if env.Get("YARD_HTTPS_PORT") == "" {
		return env.Set("YARD_HTTPS_PORT", "443")
	}
	return nil
}

const consulTLSOnWarning = "Every Workstation in the Yard needs Consul TLS."

// announceRole says what turning a role on did.
func announceRole(cmd *cobra.Command, role string) {
	if role == roleConsul {
		fmt.Fprintln(cmd.OutOrStdout(), "Consul TLS is on. "+consulTLSOnWarning)
		return
	}
	fmt.Fprintln(cmd.OutOrStdout(), "Traefik HTTPS is on.\nBrowsers: import tls/traefik/ca.crt as a trusted authority when the Workstation CA signed the certificate.")
}

func newWSTLSEnable(folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "enable", Short: "Turns on Traefik HTTPS.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if traefikOff(env) {
				return fmt.Errorf("Traefik is off on this Workstation. Turn it on with \"lvh-yard ws set traefik on\"")
			}
			if err := checkInstalledRole(folder(), roleTraefik, env); err != nil {
				return err
			}
			if err := protectWorkstationFolder(folder()); err != nil {
				return err
			}
			if err := turnOnRole(folder(), roleTraefik, env); err != nil {
				return err
			}
			announceRole(cmd, roleTraefik)
			if err := saveWSEnv(cmd, folder(), env); err != nil {
				return err
			}
			return protectWorkstationFolder(folder())
		},
	}
}

func newWSTLSDisable(folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "disable", Short: "Turns off Traefik HTTPS.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			root, err := os.OpenRoot(folder())
			if err != nil {
				return err
			}
			defer func() { _ = root.Close() }()
			if err := root.Remove(filepath.Join("traefik", "dynamic", "10-tls.yaml")); err != nil && !os.IsNotExist(err) {
				return err
			}
			if err := env.Set("YARD_HTTPS_PORT", ""); err != nil {
				return err
			}
			return saveWSEnv(cmd, folder(), env)
		},
	}
}
