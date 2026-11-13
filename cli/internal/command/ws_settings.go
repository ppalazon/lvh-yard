package command

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"unicode"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/spf13/cobra"
)

const renameWorkstationHint = "To change the Workstation name, export a Backup, run lvh-yard ws init under the new name, and import the Backup"

func newWSShow(folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "show", Short: "Prints this Workstation's settings with the Gossip Key hidden.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			for _, setting := range env.Settings() {
				key, value := setting[0], setting[1]
				if key == "YARD_GOSSIP_KEY" {
					value = "[hidden]"
				}
				fmt.Fprintf(cmd.OutOrStdout(), "%s=%s\n", key, value)
			}
			return nil
		},
	}
}

func newWSSet(folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "set <name> <value>", Short: "Changes one Workstation setting without restarting services.", Args: cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			name, value := args[0], args[1]
			if name == "workstation" || name == "workstation-name" || name == "YARD_WORKSTATION" {
				return fmt.Errorf("%s", renameWorkstationHint)
			}
			key, ok := settings.EnvKey(name)
			if !ok {
				return fmt.Errorf("unknown setting %q: use base-domain, peers, bind-address, traefik-bind-address, dashboard-bind-address, http-port, https-port, traefik-admin-port, dashboard-port, consul-http-port, consul-dns-port, consul-grpc-tls-port, consul-server-port, consul-serf-lan-port, consul-serf-wan-port, out-of-range, stack-traces or traefik", name)
			}
			checked, err := settings.Check(name, value)
			if err != nil {
				return err
			}
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if err := env.Set(key, checked); err != nil {
				return err
			}
			if name == "traefik" {
				profiles := strings.FieldsFunc(strings.Trim(env.Get("COMPOSE_PROFILES"), "\"'"), func(r rune) bool { return r == ',' || unicode.IsSpace(r) })
				kept := make([]string, 0, len(profiles)+1)
				for _, profile := range profiles {
					if profile != "traefik" {
						kept = append(kept, profile)
					}
				}
				if checked == "true" {
					kept = append(kept, "traefik")
				}
				if err := env.Set("COMPOSE_PROFILES", strings.Join(kept, ",")); err != nil {
					return err
				}
			}
			if err := saveWSEnv(cmd, folder(), env); err != nil {
				return err
			}
			if name == "traefik" && checked == "false" {
				fmt.Fprintln(cmd.OutOrStdout(), "If this Workstation has traefik-admin, run lvh-yard remove traefik-admin.")
			}
			return nil
		},
	}
}

func readWSEnv(folder string) (*envfile.File, error) {
	path := filepath.Join(folder, ".env")
	text, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("could not read %s: %w", path, err)
	}
	return envfile.Parse(string(text)), nil
}

func saveWSEnv(cmd *cobra.Command, folder string, env *envfile.File) error {
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(folder, ".env"), []byte(env.Text()), 0600); err != nil {
		return err
	}
	fmt.Fprintln(cmd.OutOrStdout(), "Saved .env. Apply the settings:")
	quotedFolder := quoteShell(folder)
	if strings.Trim(env.Get("YARD_TRAEFIK_ENABLED"), "\"'") == "false" {
		fmt.Fprintf(cmd.OutOrStdout(), "  docker compose --project-directory %s --profile traefik stop traefik\n", quotedFolder)
	}
	fmt.Fprintf(cmd.OutOrStdout(), "  %s compose recreate\n", wsCommandOf(cmd, folder))
	return nil
}
