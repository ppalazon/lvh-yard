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
		Short: "Prints a Backup of this Workstation as YAML.",
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
			if err := writePrivateFile(output, []byte(backup)); err != nil {
				return fmt.Errorf("Cannot write %s: %w", output, err)
			}
			_, err = fmt.Fprintf(cmd.OutOrStdout(), "Wrote the Backup to %s.\n", output)
			return err
		},
	}
	cmd.Flags().StringVarP(&output, "output", "o", "", "Writes the Backup to this file. Only you can read it.")
	return cmd
}

// writePrivateFile writes a file that only the current user can read: a Backup, or a zip file of
// certificates.
func writePrivateFile(path string, data []byte) error {
	if err := validateOutputPath(path); err != nil {
		return err
	}
	// #nosec G304 G703 -- The user selects the output path with --output.
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE, 0600)
	if err != nil {
		return err
	}
	defer func() { _ = file.Close() }() // The last line reports the error of Close. This one only covers early returns.
	// Restrict an existing file before writing secrets into it.
	if err := protectFile(file, 0600); err != nil {
		return err
	}
	if err := file.Truncate(0); err != nil {
		return err
	}
	if _, err := file.Write(data); err != nil {
		return err
	}
	return file.Close()
}

const importHelp = `Adds what is new in a Backup. Skips what exists.

Before: have a Backup file, made with "lvh-yard export".
Does:
  1. Reads the file, or the standard input for -.
  2. Adds each new item.
  3. Skips each item that exists, unless you use --update.
Note: --dry-run shows the plan and changes nothing.`

func NewImport(options *Options) *cobra.Command {
	var update, dryRun bool
	cmd := &cobra.Command{
		Use:   "import <file>",
		Short: "Adds the new items of a Backup.",
		Long:  importHelp,
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
	cmd.Flags().BoolVar(&update, "update", false, "Overwrites what exists with the Backup.")
	cmd.Flags().BoolVar(&dryRun, "dry-run", false, "Shows the plan. Changes nothing.")
	return cmd
}
