package command

import (
	"fmt"
	"io"
	"net"
	"slices"
	"strconv"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewPriority(options *Options) *cobra.Command {
	var on, off bool
	cmd := &cobra.Command{
		Use:   "priority <service> [<id>...]",
		Short: "Shows or sets the order of a Service's Instance Sources.",
		Args:  cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			c := options.connect()
			request := client.InstancePriorityRequest{}
			if len(args) > 1 {
				order := args[1:]
				request.Order = &order
			}
			if cmd.Flags().Changed("on") || cmd.Flags().Changed("off") {
				request.On = &on
			}
			if request.Order != nil || request.On != nil {
				if _, err := c.SetInstancePriority(args[0], request); err != nil {
					return err
				}
			}
			found, err := c.OwnService(args[0])
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			if found.InstancePriority {
				fmt.Fprintf(out, "Instance Priority of %s: on. The Route uses the first Instance Source with a passing Instance.\n", found.Name)
			} else {
				fmt.Fprintf(out, "Instance Priority of %s: off. Every passing Instance of every Instance Source shares the Route.\n", found.Name)
			}
			if len(found.Sources) == 0 {
				fmt.Fprintln(out, "  No Instance Sources yet.")
			}
			printPrioritySources(out, found, "  ")
			return nil
		},
	}
	cmd.Flags().BoolVar(&on, "on", false, "Uses the first Instance Source with a passing Instance.")
	cmd.Flags().BoolVar(&off, "off", false, "Uses every passing Instance and keeps the Instance Source order.")
	cmd.MarkFlagsMutuallyExclusive("on", "off")
	return cmd
}

func printPrioritySources(out io.Writer, found client.Service, indent string) {
	for i, source := range found.Sources {
		used := ""
		if found.Routing != nil && source.ID == found.Routing.Source {
			used = "  (in use)"
		}
		fmt.Fprintf(out, "%s%d. %s  %s%s\n", indent, i+1, source.ID, describePrioritySource(found, source), used)
	}
}

func describePrioritySource(found client.Service, source client.InstanceSource) string {
	switch source.Kind {
	case "bound":
		for _, binding := range found.ContainerBindings {
			if binding.ID == source.ID {
				follows := "container " + binding.Container
				if binding.Container == "" {
					follows = "Compose service " + binding.ComposeService + " of project " + binding.ComposeProject
				}
				return fmt.Sprintf("Container Binding to %s, port %d", follows, binding.Port)
			}
		}
		return "Container Binding"
	case "alias":
		workstation, service, _ := strings.Cut(source.ID, "/")
		return "Alias to " + service + " on " + workstation
	case "static", "foreign":
		address := ""
		for _, instance := range found.Instances {
			if slices.Contains(source.Instances, instance.ID) {
				address = net.JoinHostPort(instance.Host, strconv.Itoa(instance.Port))
				break
			}
		}
		if source.Kind == "static" {
			return "Static Instance " + address
		}
		return "Foreign Instance " + address
	default:
		// A newer dashboard can add another Instance Source kind.
		return source.Kind
	}
}
