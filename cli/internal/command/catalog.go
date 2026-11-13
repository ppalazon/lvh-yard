package command

import (
	"fmt"
	"io"
	"slices"
	"strconv"
	"unicode/utf8"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewCatalog(options *Options) *cobra.Command {
	var workstation, state string
	var details bool
	cmd := &cobra.Command{
		Use:   "catalog",
		Short: "Lists the Services of every Workstation, one line each.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if state != "" && !slices.Contains([]string{"unpublished", "unavailable", "unhealthy", "degraded", "healthy"}, state) {
				return fmt.Errorf("unknown Service state %q: use unpublished, unavailable, unhealthy, degraded or healthy", state)
			}
			yard, err := options.connect().Yard()
			if err != nil {
				return err
			}
			workstations := make([]client.YardWorkstation, 0, 1+len(yard.Remotes))
			workstations = append(workstations, client.YardWorkstation{Name: yard.Home.Name, Services: yard.Home.Services})
			workstations = append(workstations, yard.Remotes...)
			workstations, err = selectCatalogWorkstations(workstations, workstation)
			if err != nil {
				return err
			}
			printCatalog(cmd.OutOrStdout(), workstations, yard.Home.Name, state, details)
			return nil
		},
	}
	cmd.Flags().StringVar(&workstation, "workstation", "", "Lists only the Services of this Workstation.")
	cmd.Flags().StringVar(&state, "state", "", "Lists only unpublished, unavailable, unhealthy, degraded or healthy Services.")
	cmd.Flags().BoolVar(&details, "details", false, "Shows Instance addresses, Health, IDs and Service Warnings.")
	return cmd
}

func selectCatalogWorkstations(workstations []client.YardWorkstation, name string) ([]client.YardWorkstation, error) {
	if name == "" {
		return workstations, nil
	}
	for i := range workstations {
		if workstations[i].Name == name {
			return workstations[i : i+1], nil
		}
	}
	return nil, fmt.Errorf("The Yard has no Workstation %s.", name)
}

func catalogWidths(workstations []client.YardWorkstation, state string) (nameWidth, stateWidth, hostnameWidth int) {
	nameWidth, stateWidth, hostnameWidth = 1, 1, 1
	for _, ws := range workstations {
		for _, service := range ws.Services {
			if state != "" && service.State != state {
				continue
			}
			nameWidth = max(nameWidth, utf8.RuneCountInString(service.Name))
			stateWidth = max(stateWidth, utf8.RuneCountInString(service.State))
			hostnameWidth = max(hostnameWidth, utf8.RuneCountInString(service.Hostname))
		}
	}
	return nameWidth, stateWidth, hostnameWidth
}

func printCatalog(out io.Writer, workstations []client.YardWorkstation, home, state string, details bool) {
	nameWidth, stateWidth, hostnameWidth := catalogWidths(workstations, state)
	anyUnreachable := false
	for _, ws := range workstations {
		if ws.Unreachable {
			anyUnreachable = true
			fmt.Fprintln(out, ws.Name+": Workstation unreachable")
			continue
		}
		heading := ws.Name
		if ws.Name == home {
			heading += " (this Workstation)"
		}
		printed := false
		for _, service := range ws.Services {
			if state != "" && service.State != state {
				continue
			}
			if !printed {
				fmt.Fprintln(out, heading)
				printed = true
			}
			if details {
				printCatalogDetails(out, &service)
				continue
			}
			fmt.Fprintf(out, "  %-*s  %-*s  %-*s  %s\n", nameWidth, service.Name, stateWidth, service.State, hostnameWidth, service.Hostname, catalogInstances(&service))
		}
		if !printed {
			fmt.Fprintln(out, heading+": no Services")
		}
	}
	if anyUnreachable {
		fmt.Fprintln(out, "To see why, read the log of this Workstation's Consul:\n    lvh-yard ws compose logs consul")
	}
}

func catalogInstances(service *client.Service) string {
	if service.Empty {
		return "Empty"
	}
	if len(service.Instances) == 0 {
		return "No Instances"
	}
	passing := 0
	for _, instance := range service.Instances {
		if instance.Health == "passing" {
			passing++
		}
	}
	return fmt.Sprintf("%d of %d passing", passing, len(service.Instances))
}

func printCatalogDetails(out io.Writer, service *client.Service) {
	hostname := ""
	if service.Hostname != "" {
		hostname = " at " + service.Hostname
	}
	fmt.Fprintf(out, "  %s: %s%s\n", service.Name, service.State, hostname)
	if len(service.Sources) > 0 {
		priority := "off"
		if service.InstancePriority {
			priority = "on"
		}
		fmt.Fprintf(out, "    Instance Priority: %s\n", priority)
		printPrioritySources(out, *service, "      ")
	}
	for _, instance := range service.Instances {
		marks := ""
		if service.Routing != nil && slices.Contains(service.Routing.Instances, instance.ID) {
			marks += "  routed"
		}
		if instance.OutOfRange {
			marks += "  out of range"
		}
		fmt.Fprintf(out, "    %-13s  %-8s  %s%s\n", instance.Host+":"+strconv.Itoa(instance.Port), instance.Health, instance.ID, marks)
	}
	for _, warning := range service.Warnings {
		fmt.Fprintf(out, "    %s: %s\n", warning.Severity, warning.Message)
	}
}
