package command

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewRemove(options *Options) *cobra.Command {
	var selection removeSelection
	cmd := &cobra.Command{
		Use:   "remove <service>",
		Short: "Removes an Instance or a Container Binding.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			service := args[0]
			flags := cmd.Flags()
			selection.idGiven = flags.Changed("id")
			selection.hostGiven = flags.Changed("host")
			selection.portGiven = flags.Changed("port")
			selection.containerGiven = flags.Changed("container")
			selection.projectGiven = flags.Changed("compose-project")
			selection.composeServiceGiven = flags.Changed("compose-service")
			if err := selection.check(service); err != nil {
				return err
			}
			dashboard := options.connect()
			if selection.idGiven {
				if err := dashboard.Remove(service, selection.id); err != nil {
					return err
				}
				fmt.Fprintf(cmd.OutOrStdout(), "Removed Instance %s of %s.\n", selection.id, service)
				return nil
			}
			found, err := dashboard.OwnService(service)
			if err != nil {
				return err
			}
			if selection.binding() {
				binding, err := selection.bindingOf(service, found.ContainerBindings)
				if err != nil {
					return err
				}
				if err := dashboard.UnbindContainer(service, binding.ID); err != nil {
					return err
				}
				fmt.Fprintf(cmd.OutOrStdout(), "Removed the Container Binding of %s to %s, port %d.\n", service, selection.follows(), binding.Port)
				return nil
			}
			instance, err := selection.instanceOf(service, found.Instances)
			if err != nil {
				return err
			}
			if err := dashboard.Remove(service, instance.ID); err != nil {
				return err
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Removed %s at %s.\n", service, instanceAddress(instance))
			return nil
		},
	}
	cmd.Flags().StringVar(&selection.id, "id", "", "The Instance's ID.")
	cmd.Flags().IntVar(&selection.port, "port", 0, "The Instance's port or the Binding's Container Port.")
	cmd.Flags().StringVar(&selection.host, "host", "", "The Instance's address when several share the port.")
	cmd.Flags().StringVar(&selection.container, "container", "", "Removes this container's Container Binding.")
	cmd.Flags().StringVar(&selection.project, "compose-project", "", "The project of the Compose service to unbind.")
	cmd.Flags().StringVar(&selection.composeService, "compose-service", "", "The Compose service to unbind from --compose-project.")
	return cmd
}

// removeSelection is what the flags of remove say to remove: an Instance by its ID, an Instance by
// port and host, or a Container Binding by container or Compose service and port.
type removeSelection struct {
	id, host, container, project, composeService string
	port                                         int

	idGiven, hostGiven, portGiven                     bool
	containerGiven, projectGiven, composeServiceGiven bool
}

func (s removeSelection) binding() bool {
	return s.containerGiven || s.projectGiven || s.composeServiceGiven
}

// check refuses flags that do not name one thing to remove.
func (s removeSelection) check(service string) error {
	if s.idGiven {
		if s.binding() || s.portGiven || s.hostGiven {
			return fmt.Errorf("Give --id alone, or select an Instance or Container Binding with the other options.")
		}
		return nil
	}
	if !s.binding() {
		if !s.portGiven {
			return fmt.Errorf("Say what to remove from %s: give --id, --port, --container, or --compose-project and --compose-service.", service)
		}
		return nil
	}
	if s.hostGiven {
		return fmt.Errorf("A Container Binding has no --host. Give --container, or --compose-project and --compose-service.")
	}
	if s.containerGiven && (s.projectGiven || s.composeServiceGiven) {
		return fmt.Errorf("Give --container, or --compose-project and --compose-service, not both.")
	}
	if !s.containerGiven && (!s.projectGiven || !s.composeServiceGiven) {
		return fmt.Errorf("A Compose service needs both --compose-project and --compose-service.")
	}
	return nil
}

// follows says what a Container Binding follows, as the messages name it.
func (s removeSelection) follows() string {
	if s.containerGiven {
		return "container " + s.container
	}
	return "Compose service " + s.composeService + " of project " + s.project
}

// bindingOf is the one Container Binding that the flags name, or an error that says why not.
func (s removeSelection) bindingOf(service string, candidates []client.ContainerBinding) (client.ContainerBinding, error) {
	var matches []client.ContainerBinding
	for _, candidate := range candidates {
		target := candidate.Container == s.container
		if !s.containerGiven {
			target = candidate.ComposeProject == s.project && candidate.ComposeService == s.composeService
		}
		if target && (!s.portGiven || candidate.Port == s.port) {
			matches = append(matches, candidate)
		}
	}
	switch len(matches) {
	case 0:
		suffix := ""
		if s.portGiven {
			suffix = ", port " + strconv.Itoa(s.port)
		}
		return client.ContainerBinding{}, fmt.Errorf("%s has no Container Binding to %s%s.", service, s.follows(), suffix)
	case 1:
		return matches[0], nil
	}
	ports := make([]string, len(matches))
	for i, match := range matches {
		ports[i] = strconv.Itoa(match.Port)
	}
	return client.ContainerBinding{}, fmt.Errorf("%s is bound to %s on more than one port (%s): give --port.", service, s.follows(), strings.Join(ports, ", "))
}

// instanceOf is the one Instance at the port, and at the host when it was given, or an error that
// says why not.
func (s removeSelection) instanceOf(service string, instances []client.Instance) (client.Instance, error) {
	var matches []client.Instance
	for _, instance := range instances {
		if instance.Port == s.port && (!s.hostGiven || instance.Host == s.host) {
			matches = append(matches, instance)
		}
	}
	switch len(matches) {
	case 0:
		address := "port " + strconv.Itoa(s.port)
		if s.hostGiven {
			address = instanceAddress(client.Instance{Host: s.host, Port: s.port})
		}
		return client.Instance{}, fmt.Errorf("%s has no Instance at %s.", service, address)
	case 1:
		return matches[0], nil
	}
	hosts := make([]string, len(matches))
	for i, match := range matches {
		hosts[i] = match.Host
	}
	return client.Instance{}, fmt.Errorf("%s has Instances at port %d on more than one host (%s): give --host.", service, s.port, strings.Join(hosts, ", "))
}
