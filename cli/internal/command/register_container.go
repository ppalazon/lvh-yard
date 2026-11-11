package command

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func newRegisterContainer(options *Options) *cobra.Command {
	var service string
	var port int
	cmd := &cobra.Command{
		Use:   "container <container>",
		Short: "Binds a named container to an existing Service.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			c := client.New(options.DashboardAddress())
			container := args[0]
			name := service
			if !cmd.Flags().Changed("service") {
				name = cleanedContainerService(container)
			}
			if !cmd.Flags().Changed("port") {
				containers, err := c.Containers()
				if err != nil {
					return err
				}
				var ports []string
				for _, listed := range containers {
					if listed.Name == container {
						ports = containerPortNumbers(listed.Ports)
						break
					}
				}
				if len(ports) == 0 {
					return fmt.Errorf("No port is known for the container %s: give --port.", container)
				}
				if len(ports) != 1 {
					return fmt.Errorf("The container %s has more than one port: give --port with one of %s.", container, strings.Join(ports, ", "))
				}
				port, _ = strconv.Atoi(ports[0])
			}
			if _, err := c.BindContainer(name, container, port); err != nil {
				return err
			}
			registered, err := c.OwnService(name)
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			fmt.Fprintf(out, "Registered %s as %s", container, registered.Name)
			if registered.Publication == nil {
				fmt.Fprintf(out, ". %s is not published.", registered.Name)
			} else {
				fmt.Fprintf(out, ", published at %s.", registered.Hostname)
			}
			if len(registered.Instances) == 0 {
				fmt.Fprintf(out, " No container of that name runs now: %s gets its Bound Instance once one starts.", registered.Name)
			}
			fmt.Fprintln(out)
			for _, warning := range registered.Warnings {
				fmt.Fprintf(cmd.ErrOrStderr(), "%s: %s\n", warning.Severity, warning.Message)
			}
			return nil
		},
	}
	cmd.Flags().IntVar(&port, "port", 0, "The Container Port; needed when no single port is known.")
	cmd.Flags().StringVar(&service, "service", "", "The Service's name; defaults to the cleaned container name.")
	return cmd
}

func cleanedContainerService(name string) string {
	var cleaned strings.Builder
	for _, character := range strings.ToLower(name) {
		if character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '-' {
			cleaned.WriteRune(character)
		} else {
			cleaned.WriteByte('-')
		}
		if cleaned.Len() == 63 {
			break
		}
	}
	return strings.Trim(cleaned.String(), "-")
}

func newRegisterCompose(options *Options) *cobra.Command {
	var service string
	var port int
	cmd := &cobra.Command{
		Use:   "compose <project>/<service>",
		Short: "Binds a Compose service to an existing Service.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			parts := strings.Split(args[0], "/")
			if len(parts) != 2 || strings.TrimSpace(parts[0]) == "" || strings.TrimSpace(parts[1]) == "" {
				return fmt.Errorf("Give <project>/<service>, such as shop/web, not %s.", args[0])
			}
			name := service
			if !cmd.Flags().Changed("service") {
				name = cleanedContainerService(parts[1])
			}
			binding, err := client.New(options.DashboardAddress()).BindCompose(name, parts[0], parts[1], port)
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			fmt.Fprintf(out, "Bound %s to Compose service %s of project %s, port %d. ", name, binding.ComposeService, binding.ComposeProject, binding.Port)
			switch count := len(binding.Instances); count {
			case 0:
				fmt.Fprintf(out, "None of its containers runs now: %s gets a Bound Instance for each one that starts.\n", name)
			case 1:
				fmt.Fprintf(out, "1 of its containers runs now, so %s has 1 Bound Instance.\n", name)
			default:
				fmt.Fprintf(out, "%d of its containers run now, so %s has %d Bound Instances.\n", count, name, count)
			}
			return nil
		},
	}
	cmd.Flags().IntVar(&port, "port", 0, "The Container Port in each container.")
	_ = cmd.MarkFlagRequired("port")
	cmd.Flags().StringVar(&service, "service", "", "The Service's name; defaults to the cleaned Compose service name.")
	return cmd
}
