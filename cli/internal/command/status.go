package command

import (
	"errors"
	"fmt"

	"github.com/spf13/cobra"
)

var errStatusFailed = errors.New("LVH Yard status is not ok")

func NewStatus(options *Options) *cobra.Command {
	var strict bool
	cmd := &cobra.Command{
		Use:   "status",
		Short: "Checks that LVH Yard works on this Workstation.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			status, err := options.connect().Status()
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			fmt.Fprintln(out, "LVH Yard on this Workstation: "+status.Overall)
			for _, part := range status.Parts {
				mark := "FAIL  "
				switch part.State {
				case "ok":
					mark = "ok    "
				case "warning":
					mark = "WARN  "
				}
				fmt.Fprintf(out, "  %s%s: %s\n", mark, part.Name, part.Reason)
				for _, detail := range part.Details {
					suffix := ""
					if detail.State != "" && detail.State != "ok" {
						suffix = " (" + detail.State + ")"
					}
					fmt.Fprintf(out, "          %s: %s%s\n", detail.Name, detail.Value, suffix)
				}
			}
			fmt.Fprintf(out, "\nDashboard: %s\n", options.DashboardAddress())
			if status.Overall != "ok" && (status.Overall != "warning" || strict) {
				return errStatusFailed
			}
			return nil
		},
	}
	cmd.Flags().BoolVar(&strict, "strict", false, "Exits with 1 on a warning too.")
	return cmd
}
