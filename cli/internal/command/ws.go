package command

import "github.com/spf13/cobra"

func NewWS(options *Options) *cobra.Command {
	var dir string
	ws := &cobra.Command{
		Use:   "ws",
		Short: "Manages this Workstation's setup files.",
		Args:  cobra.NoArgs,
	}
	ws.PersistentFlags().StringVar(&dir, "dir", "", "Uses this Workstation Folder.")
	folder := func() string {
		if ws.PersistentFlags().Changed("dir") {
			return dir
		}
		return options.WorkstationFolder()
	}
	ws.AddCommand(newWSInit(options, folder))
	ws.AddCommand(newWSShow(folder), newWSSet(folder), newWSJoin(folder))
	ws.AddCommand(&cobra.Command{
		Use:   "upgrade",
		Short: "Updates the service images in compose.yaml.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return upgradeInit(options, folder(), cmd.OutOrStdout())
		},
	})
	return ws
}
