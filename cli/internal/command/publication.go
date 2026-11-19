package command

import (
	"fmt"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

// publishFlags holds the values of the flags of "publish".
type publishFlags struct {
	scope, accessLog, mutualTLS string
	profiles                    []string
	noProfiles                  bool
}

func NewPublish(options *Options) *cobra.Command {
	var flags publishFlags
	cmd := &cobra.Command{
		Use:   "publish <service>",
		Short: "Publishes a Service or changes its Publication.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			request, err := flags.request(cmd)
			if err != nil {
				return err
			}
			published, err := options.connect().Publish(args[0], request)
			if err != nil {
				return err
			}
			printPublished(cmd, args[0], published)
			return nil
		},
	}
	cmd.Flags().StringVar(&flags.scope, "scope", "", "Sets the Scope: local or yard (default: the current Scope).")
	cmd.Flags().StringVar(&flags.accessLog, "access-log", "", "Sets the request log of the Route: on or off.")
	cmd.Flags().StringVar(&flags.mutualTLS, "mutual-tls", "", "Sets mutual TLS from Traefik to the Instances: on or off.")
	cmd.Flags().StringArrayVar(&flags.profiles, "profile", nil, "Applies a Route Profile. Repeat it to set the order.")
	cmd.Flags().BoolVar(&flags.noProfiles, "no-profiles", false, "Removes all Route Profiles.")
	cmd.MarkFlagsMutuallyExclusive("profile", "no-profiles")
	return cmd
}

// request builds the Publication request from the flags that the user set.
func (f *publishFlags) request(cmd *cobra.Command) (client.PublicationRequest, error) {
	request := client.PublicationRequest{}
	if cmd.Flags().Changed("scope") {
		if f.scope != "local" && f.scope != "yard" {
			return request, fmt.Errorf("invalid value for --scope: %s is not local or yard", f.scope)
		}
		request.Scope = &f.scope
	}
	if cmd.Flags().Changed("access-log") {
		if f.accessLog != "on" && f.accessLog != "off" {
			return request, fmt.Errorf("invalid value for --access-log: %s is not on or off", f.accessLog)
		}
		log := f.accessLog == "on"
		request.AccessLog = &log
	}
	if cmd.Flags().Changed("mutual-tls") {
		if f.mutualTLS != "on" && f.mutualTLS != "off" {
			return request, fmt.Errorf("invalid value for --mutual-tls: %s is not on or off", f.mutualTLS)
		}
		mutual := f.mutualTLS == "on"
		request.MutualTLS = &mutual
	}
	if cmd.Flags().Changed("profile") {
		request.Profiles = &f.profiles
	} else if f.noProfiles {
		empty := []string{}
		request.Profiles = &empty
	}
	return request, nil
}

func printPublished(cmd *cobra.Command, name string, published client.Service) {
	chosen := published.Publication.Scope
	out := cmd.OutOrStdout()
	fmt.Fprintf(out, "Published %s at %s.\n", name, published.Hostname)
	fmt.Fprintf(out, "  Scope: %s\n", strings.ToUpper(chosen[:1])+chosen[1:])
	if chosen == "yard" {
		// Each Workstation uses its own Base Domain.
		fmt.Fprintf(out, "  Other Workstations: %s.%s.<base-domain>\n", name, published.Workstation)
	}
	fmt.Fprintf(out, "  State: %s\n", published.State)
	if len(published.Profiles) != 0 {
		fmt.Fprintf(out, "  Route Profiles: %s\n", strings.Join(published.Profiles, ", "))
	}
	if published.Publication.AccessLog {
		fmt.Fprintln(out, "  Access log: on")
	}
	if published.Publication.MutualTLS {
		fmt.Fprintln(out, "  Mutual TLS: on")
	}
	for _, warning := range published.Warnings {
		fmt.Fprintln(cmd.ErrOrStderr(), warningLine(warning))
	}
}

func NewUnpublish(options *Options) *cobra.Command {
	return &cobra.Command{
		Use:   "unpublish <service>",
		Short: "Removes the Publication of a Service.",
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
