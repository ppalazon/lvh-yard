package command

import (
	"fmt"
	"io"
	"slices"
	"sort"
	"strconv"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewContainers(options *Options) *cobra.Command {
	var details bool
	state, followed := "running", "no"
	cmd := &cobra.Command{
		Use:   "containers",
		Short: "Lists the running containers that no Service follows, with Compose services grouped.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if !slices.Contains(containerStates, state) {
				return fmt.Errorf("unknown state %q: use running, stopped or all", state)
			}
			if !slices.Contains(containerFollowed, followed) {
				return fmt.Errorf("unknown value %q for --followed: use no, yes or all", followed)
			}
			all, err := options.connect().Containers()
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			if len(all) == 0 {
				fmt.Fprintln(out, "No containers on this Workstation.")
				return nil
			}
			containers := filterContainers(all, state, followed)
			if len(containers) == 0 {
				fmt.Fprintln(out, "No containers match. Use --state all and --followed all to list every container.")
				return nil
			}
			projects := make(map[string]map[string][]client.ListedContainer)
			var others []client.ListedContainer
			for _, container := range containers {
				if container.ComposeProject == "" || container.ComposeService == "" {
					others = append(others, container)
					continue
				}
				if projects[container.ComposeProject] == nil {
					projects[container.ComposeProject] = make(map[string][]client.ListedContainer)
				}
				services := projects[container.ComposeProject]
				services[container.ComposeService] = append(services[container.ComposeService], container)
			}
			projectNames := make([]string, 0, len(projects))
			for project := range projects {
				projectNames = append(projectNames, project)
			}
			sort.Strings(projectNames)
			for _, project := range projectNames {
				fmt.Fprintln(out, "Compose project "+project)
				services := projects[project]
				serviceNames := make([]string, 0, len(services))
				for service := range services {
					serviceNames = append(serviceNames, service)
				}
				sort.Strings(serviceNames)
				for _, service := range serviceNames {
					printContainerService(out, service, services[service], details)
				}
			}
			if len(others) != 0 {
				fmt.Fprintln(out, "Other containers")
				sort.Slice(others, func(i, j int) bool {
					if others[i].Service != others[j].Service {
						return others[i].Service < others[j].Service
					}
					return others[i].Name < others[j].Name
				})
				for _, container := range others {
					printContainerService(out, container.Service, []client.ListedContainer{container}, details)
				}
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&state, "state", state, "Lists only running or stopped containers, or all.")
	cmd.Flags().StringVar(&followed, "followed", followed, "Lists only containers that no Container Binding follows (no), only those it follows (yes), or all.")
	cmd.Flags().BoolVar(&details, "details", false, "Shows container names and Docker port mappings.")
	return cmd
}

var (
	containerStates   = []string{"running", "stopped", "all"}
	containerFollowed = []string{"no", "yes", "all"}
)

// A Compose service is followed when any of its containers is, so every container of it stays or goes together.
func filterContainers(containers []client.ListedContainer, state, followed string) []client.ListedContainer {
	composeFollowed := make(map[string]bool)
	for _, container := range containers {
		if container.ComposeProject != "" && container.ComposeService != "" && len(container.FollowedBy) != 0 {
			composeFollowed[container.ComposeProject+"/"+container.ComposeService] = true
		}
	}
	var shown []client.ListedContainer
	for _, container := range containers {
		if state != "all" && (state == "running") != container.Running {
			continue
		}
		isFollowed := len(container.FollowedBy) != 0
		if container.ComposeProject != "" && container.ComposeService != "" {
			isFollowed = composeFollowed[container.ComposeProject+"/"+container.ComposeService]
		}
		if followed != "all" && (followed == "yes") != isFollowed {
			continue
		}
		shown = append(shown, container)
	}
	return shown
}

func printContainerService(out io.Writer, service string, containers []client.ListedContainer, details bool) {
	sort.Slice(containers, func(i, j int) bool { return containers[i].Name < containers[j].Name })
	var ports []client.ContainerPort
	var unfollowed []string
	running := 0
	for _, container := range containers {
		ports = append(ports, container.Ports...)
		if container.Running {
			running++
			if len(container.FollowedBy) == 0 {
				unfollowed = append(unfollowed, container.Name)
			}
		}
	}
	noun := "container"
	if len(containers) != 1 {
		noun = "containers"
	}
	portNames := "no ports"
	if numbers := containerPortNumbers(ports); len(numbers) != 0 {
		portNames = strings.Join(numbers, ", ")
	}
	fmt.Fprintf(out, "  %s: %d %s; %s", service, len(containers), noun, portNames)
	if registration := containers[0].Service; registration != service {
		fmt.Fprint(out, "; registers as "+registration)
	}
	if running != len(containers) {
		fmt.Fprintf(out, "; %d running, %d stopped", running, len(containers)-running)
	}
	if len(unfollowed) != 0 {
		fmt.Fprint(out, "; Unfollowed: "+strings.Join(unfollowed, ", "))
	}
	fmt.Fprintln(out)
	if details {
		for _, container := range containers {
			state := "stopped"
			if container.Running {
				state = "running"
				if len(container.FollowedBy) == 0 {
					state += "; Unfollowed"
				}
			}
			fmt.Fprintf(out, "    %s: %s; %s", container.Name, containerPortMappings(container.Ports), state)
			if container.Health != "" {
				fmt.Fprint(out, "; "+container.Health)
			}
			fmt.Fprintln(out)
		}
	}
}

func containerPortNumbers(ports []client.ContainerPort) []string {
	unique := make(map[int]bool, len(ports))
	for _, port := range ports {
		unique[port.ContainerPort] = true
	}
	numbers := make([]int, 0, len(unique))
	for number := range unique {
		numbers = append(numbers, number)
	}
	sort.Ints(numbers)
	result := make([]string, len(numbers))
	for i, number := range numbers {
		result[i] = strconv.Itoa(number)
	}
	return result
}

func containerPortMappings(ports []client.ContainerPort) string {
	if len(ports) == 0 {
		return "no ports"
	}
	mappings := make([]client.ContainerPort, 0, len(ports))
	seen := make(map[string]bool, len(ports))
	for _, port := range ports {
		hostPort := ""
		if port.HostPort != nil {
			hostPort = strconv.Itoa(*port.HostPort)
		}
		key := strconv.Itoa(port.ContainerPort) + "/" + port.HostIP + "/" + hostPort
		if !seen[key] {
			mappings = append(mappings, port)
			seen[key] = true
		}
	}
	sort.Slice(mappings, func(i, j int) bool {
		a, b := mappings[i], mappings[j]
		if a.ContainerPort != b.ContainerPort {
			return a.ContainerPort < b.ContainerPort
		}
		if a.HostIP != b.HostIP {
			return a.HostIP < b.HostIP
		}
		if a.HostPort == nil || b.HostPort == nil {
			return a.HostPort == nil && b.HostPort != nil
		}
		return *a.HostPort < *b.HostPort
	})
	result := make([]string, 0, len(mappings))
	for _, port := range mappings {
		containerPort := strconv.Itoa(port.ContainerPort)
		if port.HostPort == nil {
			result = append(result, containerPort+" not published")
		} else if port.HostIP == "" {
			result = append(result, strconv.Itoa(*port.HostPort)+"->"+containerPort+" (bind address unknown)")
		} else {
			host := port.HostIP
			if strings.Contains(host, ":") {
				host = "[" + host + "]"
			}
			result = append(result, host+":"+strconv.Itoa(*port.HostPort)+"->"+containerPort)
		}
	}
	return strings.Join(result, ", ")
}
