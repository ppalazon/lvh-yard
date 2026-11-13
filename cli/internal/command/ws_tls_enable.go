package command

import (
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
	"github.com/spf13/cobra"
)

// tlsServices are the choices of a command that installs certificates. The names are the
// arguments of ws tls import, ws tls enable and ws tls disable.
type tlsServices struct {
	consul, traefik bool
}

func addTLSServiceFlags(cmd *cobra.Command, services *tlsServices) {
	cmd.Flags().BoolVar(&services.consul, "consul", false, "Installs the Consul certificate and turns on mutual TLS between the Consul servers of the Yard. For another Workstation, it only copies the files.")
	cmd.Flags().BoolVar(&services.traefik, "traefik", false, "Installs the Traefik certificate and turns on HTTPS in Traefik. For another Workstation, it only copies the files.")
}

// choose uses a flag that was given, and asks for the others. Without a terminal, the answer is
// yes. A role that has no certificate is not asked, and it is an error when a flag names it.
// Traefik is not asked while this Workstation has it off, when env is this Workstation's.
func (s *tlsServices) choose(cmd *cobra.Command, p *prompter, env *envfile.File, available []string, forAnother bool) ([]string, error) {
	var chosen []string
	for _, role := range tlsRoles {
		given := cmd.Flags().Changed(role)
		want := s.consul
		question := "Install the Consul certificate and turn on mutual TLS between the Consul servers of the Yard?"
		if role == roleTraefik {
			want = s.traefik
			question = "Install the Traefik certificate and turn on HTTPS in Traefik?"
		}
		if forAnother {
			// Nothing runs on the other Workstation, so this only picks the files to copy.
			question = "Copy the " + roleSettingName(role) + " certificate?"
		}
		have := false
		for _, name := range available {
			have = have || name == role
		}
		switch {
		case !have:
			if given && want {
				return nil, fmt.Errorf("the Yard CA has signed no %s certificate for it: run lvh-yard ws tls ca issue %s", role, role)
			}
			continue
		case role == roleTraefik && env != nil && traefikOff(env):
			if given && want {
				return nil, fmt.Errorf("Traefik is off on this Workstation: turn it on with lvh-yard ws set traefik on")
			}
			continue
		case given:
		case p.terminal:
			var err error
			if want, err = p.yesNo(question, true); err != nil {
				return nil, err
			}
		default:
			want = true
		}
		if want {
			chosen = append(chosen, role)
		}
	}
	return chosen, nil
}

func traefikOff(env *envfile.File) bool {
	return strings.Trim(env.Get("YARD_TRAEFIK_ENABLED"), "\"'") == "false"
}

func writeConsulTLS(folder string) error {
	if err := os.MkdirAll(filepath.Join(folder, "consul.d"), 0700); err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(folder, "consul.d", "10-tls.hcl"), []byte(templates.ConsulTLS()), 0600)
}

func writeTraefikTLS(folder string) error {
	if err := os.MkdirAll(filepath.Join(folder, "traefik", "dynamic"), 0700); err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(folder, "traefik", "dynamic", "10-tls.yaml"), []byte(templates.TraefikTLS()), 0600)
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

func printApplyHint(cmd *cobra.Command, folder string) {
	fmt.Fprintf(cmd.OutOrStdout(), "Apply it with:\n  %s compose recreate\n", wsCommandOf(cmd, folder))
}

const (
	consulTLSOnWarning  = "Every Workstation in the Yard needs Consul TLS. A Workstation without it cannot federate with one that has it."
	consulTLSOffWarning = "Turn it off on every Workstation in the Yard. A Workstation with Consul TLS cannot federate with one without it."
)

// announceRole says what turning a role on did.
func announceRole(cmd *cobra.Command, role string) {
	if role == roleConsul {
		fmt.Fprintln(cmd.OutOrStdout(), "Consul TLS is on. "+consulTLSOnWarning)
		return
	}
	fmt.Fprintln(cmd.OutOrStdout(), "Traefik HTTPS is on. If the Yard CA signed its certificate, import tls/traefik/ca.crt into your browser's trusted certificate authorities.")
}

// finishRoles saves .env when Traefik was turned on, which prints the apply command, and prints
// that command otherwise.
func finishRoles(cmd *cobra.Command, folder string, env *envfile.File, roles []string) error {
	if slices.Contains(roles, roleTraefik) {
		return saveWSEnv(cmd, folder, env)
	}
	if len(roles) > 0 {
		printApplyHint(cmd, folder)
	}
	return nil
}

func newWSTLSEnable(folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "enable <consul|traefik>", Short: "Turns on Consul mutual TLS or Traefik HTTPS with the installed certificate.",
		Args: serviceArgument, ValidArgs: tlsRoles,
		RunE: func(cmd *cobra.Command, args []string) error {
			role := args[0]
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if role == roleTraefik && traefikOff(env) {
				return fmt.Errorf("Traefik is off on this Workstation: turn it on with lvh-yard ws set traefik on")
			}
			if err := checkInstalledRole(folder(), role, env); err != nil {
				return err
			}
			if err := protectWorkstationFolder(folder()); err != nil {
				return err
			}
			if err := turnOnRole(folder(), role, env); err != nil {
				return err
			}
			announceRole(cmd, role)
			if err := finishRoles(cmd, folder(), env, []string{role}); err != nil {
				return err
			}
			return protectWorkstationFolder(folder())
		},
	}
}

func newWSTLSDisable(folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "disable <consul|traefik>", Short: "Turns off Consul mutual TLS or Traefik HTTPS and keeps the certificate.",
		Args: serviceArgument, ValidArgs: tlsRoles,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if args[0] == roleConsul {
				if err := os.Remove(filepath.Join(folder(), "consul.d", "10-tls.hcl")); err != nil && !os.IsNotExist(err) {
					return err
				}
				fmt.Fprintln(cmd.OutOrStdout(), "Consul TLS is off. "+consulTLSOffWarning)
				printApplyHint(cmd, folder())
				return nil
			}
			if err := os.Remove(filepath.Join(folder(), "traefik", "dynamic", "10-tls.yaml")); err != nil && !os.IsNotExist(err) {
				return err
			}
			if err := env.Set("YARD_HTTPS_PORT", ""); err != nil {
				return err
			}
			return saveWSEnv(cmd, folder(), env)
		},
	}
}

func serviceArgument(cmd *cobra.Command, args []string) error {
	if len(args) != 1 || (args[0] != roleConsul && args[0] != roleTraefik) {
		return fmt.Errorf("name one service: consul or traefik")
	}
	return nil
}
