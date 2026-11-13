package command

import (
	"fmt"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewPublish(options *Options) *cobra.Command {
	var scope, accessLog string
	var profiles []string
	var noProfiles bool
	cmd := &cobra.Command{
		Use:   "publish <service>",
		Short: "Publishes a Service or changes its Publication.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			request := client.PublicationRequest{}
			if cmd.Flags().Changed("scope") {
				if scope != "local" && scope != "yard" {
					return fmt.Errorf("invalid value for --scope: %s is not local or yard", scope)
				}
				request.Scope = &scope
			}
			if cmd.Flags().Changed("access-log") {
				if accessLog != "on" && accessLog != "off" {
					return fmt.Errorf("invalid value for --access-log: %s is not on or off", accessLog)
				}
				log := accessLog == "on"
				request.AccessLog = &log
			}
			if cmd.Flags().Changed("profile") {
				request.Profiles = &profiles
			} else if noProfiles {
				empty := []string{}
				request.Profiles = &empty
			}
			published, err := options.connect().Publish(args[0], request)
			if err != nil {
				return err
			}
			chosen := published.Publication.Scope
			out := cmd.OutOrStdout()
			fmt.Fprintf(out, "Published %s at %s, with %s Scope", args[0], published.Hostname, strings.ToUpper(chosen[:1])+chosen[1:])
			if chosen == "yard" {
				// Each Workstation uses its own Base Domain.
				fmt.Fprintf(out, ". Other Workstations reach it at %s.%s.<base-domain>", args[0], published.Workstation)
			}
			fmt.Fprintf(out, ". It is %s.", published.State)
			if len(published.Profiles) != 0 {
				fmt.Fprintf(out, " Route Profiles: %s.", strings.Join(published.Profiles, ", "))
			}
			if published.Publication.AccessLog {
				fmt.Fprint(out, " Access log: on.")
			}
			fmt.Fprintln(out)
			for _, warning := range published.Warnings {
				fmt.Fprintf(cmd.ErrOrStderr(), "%s: %s\n", warning.Severity, warning.Message)
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&scope, "scope", "", "Uses Local or Yard Scope; an existing Publication keeps its Scope by default.")
	cmd.Flags().StringVar(&accessLog, "access-log", "", "Turns the Route's request log on or off.")
	cmd.Flags().StringArrayVar(&profiles, "profile", nil, "Applies a Route Profile; repeat it to set their order.")
	cmd.Flags().BoolVar(&noProfiles, "no-profiles", false, "Removes all Route Profiles.")
	cmd.MarkFlagsMutuallyExclusive("profile", "no-profiles")
	return cmd
}

func NewUnpublish(options *Options) *cobra.Command {
	return &cobra.Command{
		Use:   "unpublish <service>",
		Short: "Removes a Service's Publication and keeps its Instances.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := options.connect().Unpublish(args[0]); err != nil {
				return err
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Unpublished %s. Its Instances stay registered.\n", args[0])
			return nil
		},
	}
}
