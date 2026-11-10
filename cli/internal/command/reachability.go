package command

import (
	"errors"
	"fmt"

	"github.com/ppalazon/lvh-yard/cli/internal/client"
	"github.com/spf13/cobra"
)

var errReachabilityFailed = errors.New("Reachability test failed")

func NewReachability(options *Options) *cobra.Command {
	var workstation string
	cmd := &cobra.Command{
		Use:   "test <service>",
		Short: "Tests whether this Workstation reaches a Service.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			c := client.New(options.DashboardAddress())
			var tested client.Reachability
			var err error
			if cmd.Flags().Changed("workstation") {
				tested, err = c.Test(workstation, args[0])
			} else {
				tested, err = c.TestOwn(args[0])
			}
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			fmt.Fprintf(out, "Reachability of %s on %s, from this Workstation:\n", tested.Service, tested.Workstation)
			failed, skipped := 0, 0
			line := func(reached bool, what, detail string) {
				mark := "FAIL  "
				if reached {
					mark = "ok    "
				} else {
					failed++
				}
				if detail != "" {
					what += ": " + detail
				}
				fmt.Fprintln(out, "  "+mark+what)
			}
			for _, instance := range tested.Instances {
				if instance.OutOfRange {
					fmt.Fprintf(out, "  skip  Instance %s: out of range\n", instance.Address)
					skipped++
					continue
				}
				routed := ""
				if tested.Routing != nil {
					routed = " (not in the Route now)"
					if instance.Routed {
						routed = " (in the Route)"
					}
				}
				line(instance.Reached, "Instance "+instance.Address+routed, instance.Error)
			}
			for _, route := range tested.Routes {
				detail := route.Error
				if route.Reached {
					status := "null"
					if route.Status != nil {
						status = fmt.Sprint(*route.Status)
					}
					detail = "answered " + status
				}
				line(route.Reached, "Route "+route.Hostname, detail)
			}
			if tested.NoRoute != "" {
				fmt.Fprintln(out, tested.NoRoute)
			}
			if tested.Routing != nil {
				fmt.Fprintln(out, reachabilityRouting(tested))
			}
			summary := "Every check passed"
			if failed != 0 {
				checks := len(tested.Instances) - skipped + len(tested.Routes)
				plural := "s"
				if checks == 1 {
					plural = ""
				}
				summary = fmt.Sprintf("%d of %d check%s failed", failed, checks, plural)
			}
			if skipped != 0 {
				fmt.Fprintf(out, "%s. %d skipped.\n", summary, skipped)
			} else {
				fmt.Fprintln(out, summary+".")
			}
			if !tested.Passed {
				return errReachabilityFailed
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&workstation, "workstation", "", "The Service's Home Workstation (default: this one).")
	return cmd
}

func reachabilityRouting(tested client.Reachability) string {
	routing := tested.Routing
	if routing.Off {
		return "The Instance Priority is off, so the Route uses every passing Instance, or every Instance in range if none passes."
	}
	if routing.Kind == "" {
		return "No Instance passes, so the Route uses every Instance in range."
	}
	if routing.Kind == "alias" {
		if tested.Alias == nil {
			return "The Route sends requests to the Alias's Remote Service."
		}
		return "The Route sends requests to the Alias's Remote Service: " + tested.Alias.Service + " on " + tested.Alias.Workstation + "."
	}
	return "The Route uses the " + routing.Kind + " Instance Source " + routing.Source + " now."
}
