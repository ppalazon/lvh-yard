package command

import (
	"fmt"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewAlias(options *Options) *cobra.Command {
	group := &cobra.Command{
		Use:   "alias",
		Short: "Manages the Aliases that give a Service Remote Instances.",
		Args:  cobra.NoArgs,
		RunE:  func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	var workstation, remoteService string
	add := &cobra.Command{
		Use:   "add <service>",
		Short: "Adds an Alias and creates a missing Service with Local Scope.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			create := true
			added, err := client.New(options.DashboardAddress()).AddAlias(args[0], client.AliasRequest{
				Workstation: workstation,
				Service:     remoteService,
				Create:      &create,
			})
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			remote := added.Service + " on " + added.Workstation
			switch {
			case !added.AliasCreated:
				fmt.Fprintf(out, "Service %s has the Alias to %s already.\n", args[0], remote)
			case added.ServiceCreated:
				fmt.Fprintf(out, "Created Service %s with Local Scope and added the Alias to %s.\n", args[0], remote)
			default:
				fmt.Fprintf(out, "Added the Alias to %s to the existing Service %s.\n", remote, args[0])
			}
			if added.Reason != "" {
				fmt.Fprintln(cmd.ErrOrStderr(), "Warning: "+added.Reason)
			}
			return nil
		},
	}
	add.Flags().StringVar(&workstation, "workstation", "", "The Home Workstation of the Remote Service.")
	add.Flags().StringVar(&remoteService, "service", "", "The name of the Remote Service.")
	_ = add.MarkFlagRequired("workstation")
	_ = add.MarkFlagRequired("service")
	list := &cobra.Command{
		Use:   "list <service>",
		Short: "Lists the Aliases of a Service in order, with their state.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			aliases, err := client.New(options.DashboardAddress()).Aliases(args[0])
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			if len(aliases) == 0 {
				fmt.Fprintf(out, "%s has no Aliases.\n", args[0])
			}
			for _, alias := range aliases {
				fmt.Fprintf(out, "%s on %s: %s\n", alias.Service, alias.Workstation, alias.State)
				if alias.Reason != "" {
					fmt.Fprintln(out, "  "+alias.Reason)
				}
			}
			return nil
		},
	}
	var removeWorkstation, removeService string
	remove := &cobra.Command{
		Use:   "remove <service>",
		Short: "Removes one Alias of a Service.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := client.New(options.DashboardAddress()).RemoveAlias(args[0], removeWorkstation, removeService); err != nil {
				return err
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Removed the Alias to %s on %s from %s.\n", removeService, removeWorkstation, args[0])
			return nil
		},
	}
	remove.Flags().StringVar(&removeWorkstation, "workstation", "", "The Home Workstation of the Remote Service.")
	remove.Flags().StringVar(&removeService, "service", "", "The name of the Remote Service.")
	_ = remove.MarkFlagRequired("workstation")
	_ = remove.MarkFlagRequired("service")
	group.AddCommand(add, list, remove)
	return group
}
