package command

import (
	"fmt"
	"io"
	"slices"
	"strconv"
	"strings"
	"unicode/utf8"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewCatalog(options *Options) *cobra.Command {
	var workstation, state, scope string
	var details bool
	cmd := &cobra.Command{
		Use:   "catalog",
		Short: "Lists the Services of every Workstation.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if state != "" && !slices.Contains([]string{"unpublished", "unavailable", "unhealthy", "degraded", "healthy"}, state) {
				return fmt.Errorf("unknown Service state %q: use unpublished, unavailable, unhealthy, degraded or healthy", state)
			}
			if scope != "" && !slices.Contains(catalogScopes, scope) {
				return fmt.Errorf("unknown Scope %q. Use in-range, every, local or yard.", scope)
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
			printCatalog(cmd.OutOrStdout(), workstations, catalogFilter{home: yard.Home.Name, state: state, scope: scope}, details)
			return nil
		},
	}
	cmd.Flags().StringVar(&workstation, "workstation", "", "Lists only the Services of this Workstation.")
	cmd.Flags().StringVar(&state, "state", "", "Lists only Services in this state, such as healthy.")
	cmd.Flags().StringVar(&scope, "scope", "", "Filters by Scope: in-range, every, local or yard (default: in-range).")
	cmd.Flags().BoolVar(&details, "details", false, "Shows Instance addresses, Health, IDs and Warnings.")
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
	return nil, fmt.Errorf("The Yard has no Workstation %s. List them with \"lvh-yard catalog\".", name)
}

var catalogScopes = []string{"in-range", "every", "local", "yard"}

// catalogFilter is the filter of the dashboard's Catalog page.
type catalogFilter struct {
	home, state, scope string
}

// shows follows the Scope selector of the dashboard. A Service of the home Workstation is always in
// range. A Remote Service is in range when it has a Route here. Local and yard hide a Service
// that is not published.
func (f catalogFilter) shows(workstation string, service *client.Service) bool {
	if f.state != "" && service.State != f.state {
		return false
	}
	switch f.scope {
	case "every":
		return true
	case "local", "yard":
		return service.Publication != nil && service.Publication.Scope == f.scope
	default:
		return workstation == f.home || service.Routing != nil
	}
}

// catalogScope is the Scope of the Publication, or empty for a Service that is not published.
func catalogScope(service *client.Service) string {
	if service.Publication == nil {
		return ""
	}
	return service.Publication.Scope
}

func catalogWidths(workstations []client.YardWorkstation, filter catalogFilter) (nameWidth, stateWidth, scopeWidth, hostnameWidth int) {
	nameWidth, stateWidth, scopeWidth, hostnameWidth = 1, 1, 1, 1
	for _, ws := range workstations {
		for _, service := range ws.Services {
			if !filter.shows(ws.Name, &service) {
				continue
			}
			nameWidth = max(nameWidth, utf8.RuneCountInString(service.Name))
			stateWidth = max(stateWidth, utf8.RuneCountInString(service.State))
			scopeWidth = max(scopeWidth, utf8.RuneCountInString(catalogScope(&service)))
			hostnameWidth = max(hostnameWidth, utf8.RuneCountInString(service.Hostname))
		}
	}
	return nameWidth, stateWidth, scopeWidth, hostnameWidth
}

func printCatalog(out io.Writer, workstations []client.YardWorkstation, filter catalogFilter, details bool) {
	nameWidth, stateWidth, scopeWidth, hostnameWidth := catalogWidths(workstations, filter)
	anyUnreachable := false
	for _, ws := range workstations {
		if ws.Unreachable {
			anyUnreachable = true
			fmt.Fprintln(out, ws.Name+": Workstation unreachable")
			continue
		}
		heading := ws.Name
		if ws.Name == filter.home {
			heading += " (this Workstation)"
		}
		printed := false
		for _, service := range ws.Services {
			if !filter.shows(ws.Name, &service) {
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
			line := fmt.Sprintf("  %-*s  %-*s  %-*s  %-*s  %s", nameWidth, service.Name, stateWidth, service.State, scopeWidth, catalogScope(&service), hostnameWidth, service.Hostname, catalogInstances(&service))
			if badges := catalogSources(&service); badges != "" {
				line += "  " + badges
			}
			fmt.Fprintln(out, line)
		}
		if !printed {
			fmt.Fprintln(out, heading+": no Services")
		}
	}
	if anyUnreachable {
		fmt.Fprintln(out, "To see why, run:\n  lvh-yard ws compose logs consul")
	}
}

// catalogInstances counts the passing Instances. When the Route uses one Instance Source, it counts
// only the Instances of that source, because the others wait.
func catalogInstances(service *client.Service) string {
	if service.Empty {
		return "Empty"
	}
	if len(service.Instances) == 0 {
		return "No Instances"
	}
	instances := service.Instances
	if used := routedSourceInstances(service); used != nil {
		instances = used
	}
	passing := 0
	for _, instance := range instances {
		if instance.Health == "passing" {
			passing++
		}
	}
	return fmt.Sprintf("%d/%d", passing, len(instances))
}

// routedSourceInstances returns the Instances of the source that the Route uses, or nil when no
// one source wins or the Service does not list its Instances.
func routedSourceInstances(service *client.Service) []client.Instance {
	if service.Routing == nil || service.Routing.Source == "" {
		return nil
	}
	var used []client.Instance
	for _, source := range service.Sources {
		if source.ID != service.Routing.Source {
			continue
		}
		for _, instance := range service.Instances {
			if slices.Contains(source.Instances, instance.ID) {
				used = append(used, instance)
			}
		}
	}
	return used
}

// catalogSources shows the kinds of Instance Source that the Route uses now, such as "[compose]".
// Without a Route, it shows none.
func catalogSources(service *client.Service) string {
	routing := service.Routing
	if routing == nil {
		return ""
	}
	var badges []string
	for _, source := range service.Sources {
		if routing.Source != "" {
			if source.ID != routing.Source {
				continue
			}
		} else if !slices.ContainsFunc(source.Instances, func(id string) bool { return slices.Contains(routing.Instances, id) }) {
			continue
		}
		badge := "[" + catalogSourceType(service, source) + "]"
		if !slices.Contains(badges, badge) {
			badges = append(badges, badge)
		}
	}
	return strings.Join(badges, " ")
}

func catalogSourceType(service *client.Service, source client.InstanceSource) string {
	if source.Kind != "bound" {
		// A newer dashboard can add another Instance Source kind.
		return source.Kind
	}
	for _, binding := range service.ContainerBindings {
		if binding.ID == source.ID {
			if binding.Container != "" {
				return "docker"
			}
			return "compose"
		}
	}
	return "binding"
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
