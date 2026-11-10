package command

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewRemove(options *Options) *cobra.Command {
	var id, host, container, project, composeService string
	var port int
	cmd := &cobra.Command{
		Use:   "remove <service>",
		Short: "Removes an Instance or a Container Binding.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			flags := cmd.Flags()
			service := args[0]
			binding := flags.Changed("container") || flags.Changed("compose-project") || flags.Changed("compose-service")
			if flags.Changed("id") {
				if binding || flags.Changed("port") || flags.Changed("host") {
					return fmt.Errorf("Give --id alone, or select an Instance or Container Binding with the other options.")
				}
				if err := client.New(options.DashboardAddress()).Remove(service, id); err != nil {
					return err
				}
				fmt.Fprintf(cmd.OutOrStdout(), "Removed Instance %s of %s.\n", id, service)
				return nil
			}
			if binding {
				if flags.Changed("host") {
					return fmt.Errorf("A Container Binding has no --host. Give --container, or --compose-project and --compose-service.")
				}
				if flags.Changed("container") && (flags.Changed("compose-project") || flags.Changed("compose-service")) {
					return fmt.Errorf("Give --container, or --compose-project and --compose-service, not both.")
				}
				if !flags.Changed("container") && (!flags.Changed("compose-project") || !flags.Changed("compose-service")) {
					return fmt.Errorf("A Compose service needs both --compose-project and --compose-service.")
				}
			} else if !flags.Changed("port") {
				return fmt.Errorf("Say what to remove from %s: give --id, --port, --container, or --compose-project and --compose-service.", service)
			}
			dashboard := client.New(options.DashboardAddress())
			found, err := dashboard.OwnService(service)
			if err != nil {
				return err
			}
			if binding {
				follows := "container " + container
				if !flags.Changed("container") {
					follows = "Compose service " + composeService + " of project " + project
				}
				var matches []client.ContainerBinding
				for _, candidate := range found.ContainerBindings {
					target := candidate.Container == container
					if !flags.Changed("container") {
						target = candidate.ComposeProject == project && candidate.ComposeService == composeService
					}
					if target && (!flags.Changed("port") || candidate.Port == port) {
						matches = append(matches, candidate)
					}
				}
				if len(matches) == 0 {
					suffix := ""
					if flags.Changed("port") {
						suffix = ", port " + strconv.Itoa(port)
					}
					return fmt.Errorf("%s has no Container Binding to %s%s.", service, follows, suffix)
				}
				if len(matches) > 1 {
					ports := make([]string, len(matches))
					for i, match := range matches {
						ports[i] = strconv.Itoa(match.Port)
					}
					return fmt.Errorf("%s is bound to %s on more than one port (%s): give --port.", service, follows, strings.Join(ports, ", "))
				}
				selected := matches[0]
				if err := dashboard.UnbindContainer(service, selected.ID); err != nil {
					return err
				}
				fmt.Fprintf(cmd.OutOrStdout(), "Removed the Container Binding of %s to %s, port %d.\n", service, follows, selected.Port)
				return nil
			}
			var matches []client.Instance
			for _, instance := range found.Instances {
				if instance.Port == port && (!flags.Changed("host") || instance.Host == host) {
					matches = append(matches, instance)
				}
			}
			if len(matches) == 0 {
				address := "port " + strconv.Itoa(port)
				if flags.Changed("host") {
					address = instanceAddress(client.Instance{Host: host, Port: port})
				}
				return fmt.Errorf("%s has no Instance at %s.", service, address)
			}
			if len(matches) > 1 {
				hosts := make([]string, len(matches))
				for i, match := range matches {
					hosts[i] = match.Host
				}
				return fmt.Errorf("%s has Instances at port %d on more than one host (%s): give --host.", service, port, strings.Join(hosts, ", "))
			}
			selected := matches[0]
			if err := dashboard.Remove(service, selected.ID); err != nil {
				return err
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Removed %s at %s.\n", service, instanceAddress(selected))
			return nil
		},
	}
	cmd.Flags().StringVar(&id, "id", "", "The Instance's ID.")
	cmd.Flags().IntVar(&port, "port", 0, "The Instance's port or the Binding's Container Port.")
	cmd.Flags().StringVar(&host, "host", "", "The Instance's address when several share the port.")
	cmd.Flags().StringVar(&container, "container", "", "Removes this container's Container Binding.")
	cmd.Flags().StringVar(&project, "compose-project", "", "The project of the Compose service to unbind.")
	cmd.Flags().StringVar(&composeService, "compose-service", "", "The Compose service to unbind from --compose-project.")
	return cmd
}
