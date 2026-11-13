package command

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewProfiles(options *Options) *cobra.Command {
	profiles := &cobra.Command{
		Use:   "profiles",
		Short: "Lists the Route Profiles of this Workstation.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			profiles, err := client.New(options.DashboardAddress()).RouteProfiles()
			if err != nil {
				return err
			}
			if len(profiles) == 0 {
				fmt.Fprintln(cmd.OutOrStdout(), "No Route Profiles.")
			}
			for _, profile := range profiles {
				line := profile.Name + ": "
				if profile.Type != "" {
					line += profile.Type + ", "
				}
				if len(profile.Services) == 0 {
					line += "not used"
				} else {
					line += "used by " + strings.Join(profile.Services, ", ")
				}
				fmt.Fprintln(cmd.OutOrStdout(), line)
			}
			return nil
		},
	}
	profiles.AddCommand(&cobra.Command{
		Use:   "show <name>",
		Short: "Prints a Route Profile in Traefik's file format.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			profile, err := client.New(options.DashboardAddress()).RouteProfile(args[0])
			if err != nil {
				return err
			}
			text, err := writeProfileFile(profile.Name, profile.Definition)
			if err != nil {
				return err
			}
			_, err = cmd.OutOrStdout().Write(text)
			return err
		},
	}, &cobra.Command{
		Use:   "set <file>",
		Short: "Adds or replaces the Route Profiles of a file in Traefik's format.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			var text []byte
			var err error
			if args[0] == "-" {
				text, err = io.ReadAll(options.Process.Input())
				if err != nil {
					return fmt.Errorf("Cannot read the standard input: %w", err)
				}
			} else {
				text, err = os.ReadFile(args[0])
				if err != nil {
					return fmt.Errorf("Cannot read %s: %w", args[0], err)
				}
			}
			set, err := readProfileFile(text)
			if err != nil {
				return err
			}
			if _, err := client.New(options.DashboardAddress()).PutRouteProfiles(set.profiles); err != nil {
				return err
			}
			label := "Saved the Route Profile "
			if len(set.names) > 1 {
				label = "Saved the Route Profiles "
			}
			fmt.Fprintln(cmd.OutOrStdout(), label+strings.Join(set.names, ", ")+".")
			return nil
		},
	}, &cobra.Command{
		Use:   "edit <name>",
		Short: "Opens a Route Profile in $EDITOR, else vi, and saves it.",
		Args:  cobra.ExactArgs(1),
		RunE:  func(cmd *cobra.Command, args []string) error { return editProfile(cmd, options, args[0]) },
	}, &cobra.Command{
		Use:   "remove <name>",
		Short: "Removes a Route Profile.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := client.New(options.DashboardAddress()).RemoveRouteProfile(args[0]); err != nil {
				return err
			}
			fmt.Fprintln(cmd.OutOrStdout(), "Removed the Route Profile "+args[0]+".")
			return nil
		},
	})
	return profiles
}

func editProfile(cmd *cobra.Command, options *Options, name string) error {
	dashboard := client.New(options.DashboardAddress())
	profile, err := dashboard.RouteProfile(name)
	if err != nil {
		return err
	}
	original, err := writeProfileFile(profile.Name, profile.Definition)
	if err != nil {
		return err
	}
	// CreateTemp uses 0600: basicAuth profiles can hold password hashes.
	file, err := os.CreateTemp("", "route-profile-*.yaml")
	if err != nil {
		return fmt.Errorf("Cannot write a temporary file: %w", err)
	}
	path := file.Name()
	_, writeErr := file.Write(original)
	closeErr := file.Close()
	if err := errors.Join(writeErr, closeErr); err != nil {
		_ = os.Remove(path)
		return fmt.Errorf("Cannot write a temporary file: %w", err)
	}
	editor := options.Process.Getenv("EDITOR")
	if strings.TrimSpace(editor) == "" {
		editor = "vi"
	}
	saved, err := editUntilSaved(cmd, options, dashboard, name, path, editor, original)
	if saved {
		if removeErr := os.Remove(path); removeErr != nil && !errors.Is(removeErr, os.ErrNotExist) {
			return errors.Join(err, fmt.Errorf("Cannot remove the temporary Route Profile: %w", removeErr))
		}
		return err
	}
	// The file is the only copy of the user's text, so it stays.
	return errors.Join(err, fmt.Errorf("Did not save the Route Profile %s. Your text is in %s.", name, path))
}

// editUntilSaved reports true when the Route Profile is saved or has no change.
func editUntilSaved(cmd *cobra.Command, options *Options, dashboard *client.Client, name, path, editor string, original []byte) (bool, error) {
	answers := bufio.NewReader(options.Process.Input())
	for {
		if err := options.Process.Edit(editor, path); err != nil {
			return false, fmt.Errorf("Cannot edit the Route Profile with %s: %w", editor, err)
		}
		text, err := os.ReadFile(path)
		if err != nil {
			return false, fmt.Errorf("Cannot read %s: %w", path, err)
		}
		if bytes.Equal(text, original) {
			fmt.Fprintln(cmd.OutOrStdout(), "No change to the Route Profile "+name+".")
			return true, nil
		}
		set, err := readProfileFile(text)
		if err == nil && (len(set.names) != 1 || set.names[0] != name) {
			err = fmt.Errorf("The file must hold only the Route Profile %s. It holds %s.", name, strings.Join(set.names, ", "))
		}
		if err == nil {
			_, err = dashboard.PutRouteProfile(name, set.profiles[name])
		}
		if err == nil {
			fmt.Fprintln(cmd.OutOrStdout(), "Saved the Route Profile "+name+".")
			return true, nil
		}
		fmt.Fprintln(cmd.ErrOrStderr(), err)
		fmt.Fprint(cmd.ErrOrStderr(), "Edit again? [Y/n] ")
		answer, readErr := answers.ReadString('\n')
		if readErr != nil && len(answer) == 0 {
			fmt.Fprintln(cmd.ErrOrStderr())
			return false, nil
		}
		answer = strings.ToLower(strings.TrimSpace(answer))
		if readErr != nil && !errors.Is(readErr, io.EOF) || answer != "" && answer != "y" && answer != "yes" {
			return false, nil
		}
	}
}
