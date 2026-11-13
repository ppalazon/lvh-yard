package command

import (
	"fmt"
	"io"
	"os"

	"github.com/spf13/cobra"
)

func NewExport(options *Options) *cobra.Command {
	var output string
	cmd := &cobra.Command{
		Use:   "export",
		Short: "Prints a Backup of this Workstation's setup as YAML.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			backup, err := options.connect().Backup()
			if err != nil {
				return err
			}
			if !cmd.Flags().Changed("output") {
				_, err := io.WriteString(cmd.OutOrStdout(), backup)
				return err
			}
			if err := writeBackup(output, backup); err != nil {
				return fmt.Errorf("Cannot write %s: %w", output, err)
			}
			_, err = fmt.Fprintf(cmd.OutOrStdout(), "Wrote the Backup to %s.\n", output)
			return err
		},
	}
	cmd.Flags().StringVarP(&output, "output", "o", "", "Writes the Backup to a file that only you can read.")
	return cmd
}

func writeBackup(path, backup string) error {
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE, 0600)
	if err != nil {
		return err
	}
	defer func() { _ = file.Close() }() // The last line reports the error of Close. This one only covers early returns.
	// A Backup holds basic-auth hashes. Restrict an existing file before writing them.
	if err := file.Chmod(0600); err != nil {
		return err
	}
	if err := file.Truncate(0); err != nil {
		return err
	}
	if _, err := file.WriteString(backup); err != nil {
		return err
	}
	return file.Close()
}

func NewImport(options *Options) *cobra.Command {
	var update, dryRun bool
	cmd := &cobra.Command{
		Use:   "import <file>",
		Short: "Adds what is new in a Backup and skips what exists.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			var data []byte
			var err error
			if args[0] == "-" {
				data, err = io.ReadAll(options.Process.Input())
				if err != nil {
					return fmt.Errorf("Cannot read the standard input: %w", err)
				}
			} else {
				data, err = os.ReadFile(args[0])
				if err != nil {
					return fmt.Errorf("Cannot read %s: %w", args[0], err)
				}
			}
			mode := "add"
			if update {
				mode = "update"
			}
			result, err := options.connect().ImportBackup(string(data), mode, dryRun)
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			if from := result.ExportedFrom; from != nil && from.Workstation != "" {
				when := ""
				if from.At != "" {
					when = " at " + from.At
				}
				fmt.Fprintf(out, "Backup of %s%s.\n", from.Workstation, when)
			}
			added, updated := 0, 0
			for _, entry := range result.Entries {
				fmt.Fprintln(out, entry.Line())
				switch entry.Action {
				case "add":
					added++
				case "update":
					updated++
				}
			}
			skipped := len(result.Entries) - added - updated
			if result.DryRun {
				fmt.Fprintln(out, "Dry run: nothing changed.")
			} else if added+updated == 0 {
				fmt.Fprintf(out, "Nothing changed: skipped %d.\n", skipped)
			} else if updated == 0 {
				fmt.Fprintf(out, "Added %d, skipped %d.\n", added, skipped)
			} else {
				fmt.Fprintf(out, "Added %d, updated %d, skipped %d.\n", added, updated, skipped)
			}
			if result.SafetyBackup != "" {
				fmt.Fprintf(out, "Safety Backup: %s, in the Backups folder.\n", result.SafetyBackup)
			}
			return nil
		},
	}
	cmd.Flags().BoolVar(&update, "update", false, "Also overwrites what exists with the Backup.")
	cmd.Flags().BoolVar(&dryRun, "dry-run", false, "Shows the plan and changes nothing.")
	return cmd
}
