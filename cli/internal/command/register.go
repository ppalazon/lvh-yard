package command

import (
	"fmt"
	"net"
	"strconv"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

func NewRegister(options *Options) *cobra.Command {
	group := &cobra.Command{
		Use:   "register",
		Short: "Registers Instances of a Service.",
		Args:  cobra.NoArgs,
		RunE:  func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	group.AddCommand(newRegisterStatic(options), newRegisterContainer(options), newRegisterCompose(options))
	return group
}

func newRegisterStatic(options *Options) *cobra.Command {
	var host string
	var port int
	var check client.HealthCheck
	var tags, metadata []string
	cmd := &cobra.Command{
		Use:   "static <service>",
		Short: "Registers a Static Instance of a Service.",
		Long:  registerStaticHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			hostGiven, err := askMissingStaticFlags(cmd, options, &port, &host)
			if err != nil {
				return err
			}
			request := client.StaticInstanceRequest{Port: port}
			if hostGiven {
				request.Host = &host
			}
			for _, name := range []string{"check", "path", "interval", "timeout"} {
				if cmd.Flags().Changed(name) {
					request.Check = &check
					break
				}
			}
			if len(tags) > 0 {
				request.Tags = &tags
			}
			if len(metadata) > 0 {
				meta := make(map[string]string, len(metadata))
				for _, item := range metadata {
					key, value, ok := strings.Cut(item, "=")
					if !ok {
						return fmt.Errorf("Give --meta as <key=value>: %s", item)
					}
					meta[key] = value
				}
				request.Meta = &meta
			}
			instance, err := options.connect().Register(args[0], request)
			if err != nil {
				return err
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Registered %s at %s, Instance %s.\n", args[0], instanceAddress(instance), instance.ID)
			return nil
		},
	}
	cmd.Flags().IntVar(&port, "port", 0, "Sets the port of the Instance (default: asked at a terminal).")
	cmd.Flags().StringVar(&host, "host", "", "Sets the Instance address (default: the address of this Workstation).")
	cmd.Flags().StringVar(&check.Type, "check", "", "Sets the Health check: tcp or http.")
	cmd.Flags().StringVar(&check.Path, "path", "", "Sets the path of an HTTP check.")
	cmd.Flags().StringVar(&check.Interval, "interval", "", "Sets the time between checks, such as 5s or 1m.")
	cmd.Flags().StringVar(&check.Timeout, "timeout", "", "Sets the time limit of a check, such as 500ms.")
	cmd.Flags().StringArrayVar(&tags, "tag", nil, "Adds a tag to the Instance. Repeat for more.")
	cmd.Flags().StringArrayVar(&metadata, "meta", nil, "Adds a key=value note. Repeat for more.")
	return cmd
}

func instanceAddress(instance client.Instance) string {
	return net.JoinHostPort(instance.Host, strconv.Itoa(instance.Port))
}

// validPort accepts a TCP port number.
func validPort(answer string) error {
	if number, err := strconv.Atoi(answer); err != nil || number < 1 || number > 65535 {
		return fmt.Errorf("Give a port from 1 to 65535.")
	}
	return nil
}

// askMissingStaticFlags gives the port and the host of a Static Instance. At a terminal, it asks for
// each one that no flag gave. Without a terminal, a missing port is an error and a missing host is
// left to the dashboard. It tells whether host holds an address.
func askMissingStaticFlags(cmd *cobra.Command, options *Options, port *int, host *string) (bool, error) {
	prompt := newPrompter(cmd, options)
	hostGiven := cmd.Flags().Changed("host")
	if !cmd.Flags().Changed("port") {
		if !prompt.terminal {
			return hostGiven, fmt.Errorf("Missing --port. No terminal is open: pass --port")
		}
		answer, err := prompt.text("Port of the app", "", validPort)
		if err != nil {
			return hostGiven, err
		}
		*port, _ = strconv.Atoi(answer)
	}
	if hostGiven || !prompt.terminal {
		return hostGiven, nil
	}
	fmt.Fprintln(cmd.OutOrStdout(), "Use 127.0.0.1 for an app that listens on localhost only.")
	answer, err := prompt.text("Host of the app (Enter for the address of this Workstation)", "", nil)
	if err != nil || answer == "" {
		return false, err
	}
	*host = answer
	return true, nil
}

const registerStaticHelp = `Registers a Static Instance: the host and port of a process that you run.

Does:
  1. Asks for the port and the host at a terminal, when a flag is missing.
  2. Registers the Instance and starts its Health check.

Note:
  - Without --host, the Instance is at the address of this Workstation: 127.0.0.1 until it starts or joins a Yard, then its LAN address.
  - Use --host 127.0.0.1 for an app that listens on localhost only.`
