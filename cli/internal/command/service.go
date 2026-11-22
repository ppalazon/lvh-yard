package command

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"
)

func NewService(options *Options) *cobra.Command {
	group := &cobra.Command{
		Use:   "service",
		Short: "Creates or deletes a Service.",
		Args:  cobra.NoArgs,
		RunE:  func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	group.AddCommand(&cobra.Command{
		Use:   "create <service>",
		Short: "Creates an empty, Unpublished Service.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			created, err := options.connect().CreateService(args[0])
			if err != nil {
				return err
			}
			if created {
				fmt.Fprintf(cmd.OutOrStdout(), "Created %s. It is empty and Unpublished.\n%s", args[0], instanceSuggestions(args[0]))
			} else {
				fmt.Fprintf(cmd.OutOrStdout(), "%s exists already.\n", args[0])
			}
			return nil
		},
	})
	group.AddCommand(&cobra.Command{
		Use:   "delete <service>",
		Short: "Deletes a Service and its Instance Sources.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			existed, err := options.connect().DeleteService(args[0])
			if err != nil {
				return err
			}
			if !existed {
				fmt.Fprintf(cmd.OutOrStdout(), "%s does not exist. Nothing to delete.\n", args[0])
				return nil
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Deleted %s with its Publication, Static Instances and Container Bindings. Its containers keep running.\n", args[0])
			return nil
		},
	})
	return group
}

// instanceSuggestions lists a command for each Instance Source that a new, empty Service can use.
func instanceSuggestions(service string) string {
	var list strings.Builder
	list.WriteString("Next, add Instances. Pick the Instance Source that fits:\n")
	for _, suggestion := range [][2]string{
		{"Static Instance", "lvh-yard register static " + service + " --port <port>"},
		{"Container Binding", "lvh-yard register container <container> --service " + service},
		{"Container Binding, Compose", "lvh-yard register compose <project>/<service> --port <port> --service " + service},
		{"Alias", "lvh-yard alias add " + service + " --workstation <workstation> --service <service>"},
	} {
		fmt.Fprintf(&list, "  %-28s%s\n", suggestion[0], suggestion[1])
	}
	return list.String()
}
