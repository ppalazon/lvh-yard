package command

import (
	"errors"
	"fmt"
	"io"

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
			c := options.connect()
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
			printReachability(cmd.OutOrStdout(), tested)
			if !tested.Passed {
				return errReachabilityFailed
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&workstation, "workstation", "", "Sets the Home Workstation of the Service (default: this one).")
	return cmd
}

// reachabilityReport prints the checks of a test and counts the failed and skipped ones.
type reachabilityReport struct {
	out             io.Writer
	failed, skipped int
}

func printReachability(out io.Writer, tested client.Reachability) {
	report := &reachabilityReport{out: out}
	fmt.Fprintf(out, "Reachability of %s on %s, from this Workstation:\n", tested.Service, tested.Workstation)
	report.instances(tested)
	report.routes(tested)
	if tested.NoRoute != "" {
		fmt.Fprintln(out, tested.NoRoute)
	}
	if tested.Routing != nil {
		fmt.Fprintln(out, reachabilityRouting(tested))
	}
	report.summary(tested)
}

func (r *reachabilityReport) line(reached bool, what, detail string) {
	mark := "FAIL  "
	if reached {
		mark = "ok    "
	} else {
		r.failed++
	}
	if detail != "" {
		what += ": " + detail
	}
	fmt.Fprintln(r.out, "  "+mark+what)
}

func (r *reachabilityReport) instances(tested client.Reachability) {
	for _, instance := range tested.Instances {
		if instance.OutOfRange {
			fmt.Fprintf(r.out, "  skip  Instance %s: out of range\n", instance.Address)
			r.skipped++
			continue
		}
		routed := ""
		if tested.Routing != nil {
			routed = " (not in the Route now)"
			if instance.Routed {
				routed = " (in the Route)"
			}
		}
		r.line(instance.Reached, "Instance "+instance.Address+routed, instance.Error)
	}
}

func (r *reachabilityReport) routes(tested client.Reachability) {
	for _, route := range tested.Routes {
		detail := route.Error
		if route.Reached {
			status := "null"
			if route.Status != nil {
				status = fmt.Sprint(*route.Status)
			}
			detail = "answered " + status
			if route.Note != "" {
				detail = route.Note
			}
		}
		// A dashboard before the HTTPS check sends no scheme, and tests HTTP only.
		scheme := route.Scheme
		if scheme == "" {
			scheme = "http"
		}
		r.line(route.Reached, "Route "+scheme+"://"+route.Hostname, detail)
	}
}

func (r *reachabilityReport) summary(tested client.Reachability) {
	summary := "Every check passed"
	if r.failed != 0 {
		checks := len(tested.Instances) - r.skipped + len(tested.Routes)
		plural := "s"
		if checks == 1 {
			plural = ""
		}
		summary = fmt.Sprintf("%d of %d check%s failed", r.failed, checks, plural)
	}
	if r.skipped != 0 {
		fmt.Fprintf(r.out, "%s. %d skipped.\n", summary, r.skipped)
	} else {
		fmt.Fprintln(r.out, summary+".")
	}
}

func reachabilityRouting(tested client.Reachability) string {
	routing := tested.Routing
	if routing.Off {
		return "The Instance Priority is off. The Route uses every passing Instance, or every Instance in range if none passes."
	}
	if routing.Kind == "" {
		return "No Instance passes. The Route uses every Instance in range."
	}
	if routing.Kind == "alias" {
		if tested.Alias == nil {
			return "The Route sends requests to the Alias's Remote Service."
		}
		return "The Route sends requests to the Alias's Remote Service: " + tested.Alias.Service + " on " + tested.Alias.Workstation + "."
	}
	return "The Route uses the " + routing.Kind + " Instance Source " + routing.Source + " now."
}
