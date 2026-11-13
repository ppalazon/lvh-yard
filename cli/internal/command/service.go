package command

import (
	"fmt"

	"github.com/spf13/cobra"
)

func NewService(options *Options) *cobra.Command {
	group := &cobra.Command{
		Use:   "service",
		Short: "Creates or deletes a Service of this Workstation.",
		Args:  cobra.NoArgs,
		RunE:  func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	group.AddCommand(&cobra.Command{
		Use:   "create <service>",
		Short: "Creates an empty, Unpublished Service unless it exists.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			created, err := options.connect().CreateService(args[0])
			if err != nil {
				return err
			}
			if created {
				fmt.Fprintf(cmd.OutOrStdout(), "Created %s. It is empty and Unpublished: attach Instances with lvh-yard register.\n", args[0])
			} else {
				fmt.Fprintf(cmd.OutOrStdout(), "%s exists already.\n", args[0])
			}
			return nil
		},
	})
	group.AddCommand(&cobra.Command{
		Use:   "delete <service>",
		Short: "Deletes a Service with its Publication and Instance Sources.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			existed, err := options.connect().DeleteService(args[0])
			if err != nil {
				return err
			}
			if !existed {
				fmt.Fprintf(cmd.OutOrStdout(), "%s does not exist already. Nothing to delete.\n", args[0])
				return nil
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Deleted %s with its Publication, Static Instances and Container Bindings. Its containers keep running.\n", args[0])
			return nil
		},
	})
	return group
}
