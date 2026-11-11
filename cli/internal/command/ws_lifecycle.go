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

func newWSLifecycle(options *Options, folder func() string) []*cobra.Command {
	var commands []*cobra.Command
	for _, action := range []struct {
		name  string
		short string
		args  []string
	}{
		{"start", "Starts this Workstation's services.", []string{"up", "-d"}},
		{"stop", "Stops this Workstation's services.", []string{"stop"}},
		{"restart", "Restarts this Workstation's services.", []string{"restart"}},
	} {
		commands = append(commands, &cobra.Command{
			Use:   action.name,
			Short: action.short,
			Args:  cobra.NoArgs,
			RunE: func(cmd *cobra.Command, args []string) error {
				return runWSCompose(options, cmd, folder(), action.args)
			},
		})
	}
	var follow bool
	logs := &cobra.Command{
		Use:       "logs [consul|traefik|dashboard]",
		Short:     "Shows this Workstation's service logs.",
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
	logs.Flags().BoolVarP(&follow, "follow", "f", false, "Follows the service logs.")
	return append(commands, logs)
}

func runWSCompose(options *Options, cmd *cobra.Command, folder string, args []string) error {
	dir, err := filepath.Abs(folder)
	if err != nil {
		return err
	}
	compose := append([]string{"compose", "--project-directory", dir}, args...)
	quotedDir := "'" + strings.ReplaceAll(dir, "'", "'\"'\"'") + "'"
	fmt.Fprintf(cmd.OutOrStdout(), "docker compose --project-directory %s %s\n", quotedDir, strings.Join(args, " "))
	code, err := options.Process.Run("docker", compose, dir, cmd.OutOrStdout(), cmd.ErrOrStderr())
	if errors.Is(err, exec.ErrNotFound) {
		return errors.New("docker is not on the PATH; install Docker with Compose")
	}
	if err != nil {
		return err
	}
	if code != 0 {
		return &processExit{code: code}
	}
	return nil
}
