package command

import "github.com/spf13/cobra"

func NewWS(options *Options) *cobra.Command {
	var dir string
	ws := &cobra.Command{
		Use:   "ws",
		Short: "Manages this Workstation's files and services.",
		Args:  cobra.NoArgs,
		RunE:  func(cmd *cobra.Command, args []string) error { return cmd.Help() },
	}
	ws.PersistentFlags().StringVar(&dir, "dir", "", "Uses this Workstation Folder.")
	folder := func() string {
		if ws.PersistentFlags().Changed("dir") {
			return dir
		}
		return options.WorkstationFolder()
	}
	ws.AddCommand(newWSInit(options, folder))
	ws.AddCommand(newWSShow(folder), newWSSet(folder), newWSYard(options, folder))
	ws.AddCommand(newWSLifecycle(options, folder)...)
	ws.AddCommand(newWSTLS(options, folder))
	return ws
}
