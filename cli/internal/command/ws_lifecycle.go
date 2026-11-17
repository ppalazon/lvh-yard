package command

import (
	"errors"
	"fmt"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
)

type processExit struct {
	code int
}

func (e *processExit) Error() string {
	return fmt.Sprintf("docker compose exited with code %d", e.code)
}

// composeRecreate is the docker compose command of ws compose recreate.
var composeRecreate = []string{"up", "-d", "--force-recreate"}

// newWSLifecycle returns ws compose, which holds the commands that run docker compose on this
// Workstation's services or change compose.yaml.
func newWSLifecycle(options *Options, folder func() string) []*cobra.Command {
	compose := &cobra.Command{
		Use:   "compose",
		Short: "Runs the Compose services of this Workstation.",
		Args:  cobra.NoArgs,
		RunE:  func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	for _, action := range []struct {
		name  string
		short string
		args  []string
	}{
		{"start", "Starts the Compose services.", []string{"up", "-d"}},
		{"stop", "Stops the Compose services.", []string{"stop"}},
		{"restart", "Restarts the Compose services.", []string{"restart"}},
		{"recreate", "Recreates the services to apply new settings.", composeRecreate},
	} {
		compose.AddCommand(&cobra.Command{
			Use:   action.name,
			Short: action.short,
			Args:  cobra.NoArgs,
			RunE: func(cmd *cobra.Command, args []string) error {
				return runWSCompose(options, cmd, folder(), action.args)
			},
		})
	}
	var volumes bool
	down := &cobra.Command{
		Use:   "down",
		Short: "Removes the containers of this Workstation.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			compose := []string{"down"}
			if volumes {
				compose = append(compose, "--volumes")
			}
			return runWSCompose(options, cmd, folder(), compose)
		},
	}
	down.Flags().BoolVarP(&volumes, "volumes", "v", false, "Removes the data volumes too. Consul loses its data.")
	compose.AddCommand(down)
	compose.AddCommand(&cobra.Command{
		Use:   "upgrade",
		Short: "Updates the images in compose.yaml.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return upgradeInit(options, folder(), wsCommandOf(cmd, folder()), cmd.OutOrStdout())
		},
	})
	var follow bool
	logs := &cobra.Command{
		Use:       "logs [consul|traefik|dashboard]",
		Short:     "Shows the logs of the Compose services.",
		ValidArgs: []string{"consul", "traefik", "dashboard"},
		Args: func(cmd *cobra.Command, args []string) error {
			if len(args) == 0 {
				return nil
			}
			if len(args) == 1 && (args[0] == "consul" || args[0] == "traefik" || args[0] == "dashboard") {
				return nil
			}
			return errors.New("choose one service: consul, traefik or dashboard")
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			compose := []string{"logs"}
			if follow {
				compose = append(compose, "-f")
			}
			return runWSCompose(options, cmd, folder(), append(compose, args...))
		},
	}
	logs.Flags().BoolVarP(&follow, "follow", "f", false, "Follows the logs.")
	compose.AddCommand(logs)
	return []*cobra.Command{compose}
}

func runWSCompose(options *Options, cmd *cobra.Command, folder string, args []string) error {
	dir, err := filepath.Abs(folder)
	if err != nil {
		return err
	}
	compose := append([]string{"compose", "--project-directory", dir}, args...)
	fmt.Fprintf(cmd.OutOrStdout(), "docker compose --project-directory %s %s\n", quoteShell(dir), strings.Join(args, " "))
	code, err := options.Process.Run("docker", compose, dir, cmd.OutOrStdout(), cmd.ErrOrStderr())
	if errors.Is(err, exec.ErrNotFound) {
		return errors.New("docker is not on the PATH. Install Docker with Compose")
	}
	if err != nil {
		return err
	}
	if code != 0 {
		return &processExit{code: code}
	}
	return nil
}

// wsCommandOf is the ws command to print in an instruction. It names the folder only when the
// command that is running got --dir, because the other commands find the default folder alone.
func wsCommandOf(cmd *cobra.Command, folder string) string {
	if flag := cmd.Flag("dir"); flag != nil && flag.Changed {
		return "lvh-yard ws --dir " + quoteShell(folder)
	}
	return "lvh-yard ws"
}
