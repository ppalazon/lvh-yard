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
		Short: "Registers a Static Instance or binds a container or Compose service.",
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
		Short: "Registers a Static Instance of an existing Service.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			request := client.StaticInstanceRequest{Port: port}
			if cmd.Flags().Changed("host") {
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
	cmd.Flags().IntVar(&port, "port", 0, "The port the Instance listens on.")
	_ = cmd.MarkFlagRequired("port")
	cmd.Flags().StringVar(&host, "host", "", "The Instance's address; defaults to this Workstation's LAN address.")
	cmd.Flags().StringVar(&check.Type, "check", "", "Checks Health with tcp or http.")
	cmd.Flags().StringVar(&check.Path, "path", "", "The path an HTTP check gets.")
	cmd.Flags().StringVar(&check.Interval, "interval", "", "Time between checks, such as 5s or 1m.")
	cmd.Flags().StringVar(&check.Timeout, "timeout", "", "Time to wait for a check, such as 500ms.")
	cmd.Flags().StringArrayVar(&tags, "tag", nil, "Labels the Instance; repeat for more.")
	cmd.Flags().StringArrayVar(&metadata, "meta", nil, "Adds a key=value note; repeat for more.")
	return cmd
}

func instanceAddress(instance client.Instance) string {
	return net.JoinHostPort(instance.Host, strconv.Itoa(instance.Port))
}
