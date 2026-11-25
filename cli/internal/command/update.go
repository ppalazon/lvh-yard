package command

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/update"
	"github.com/spf13/cobra"
)

// InstallMethodStandalone is the install method of an executable from a release archive, which is also what the
// install scripts put in place. dev/package-cli.sh sets it with -ldflags "-X main.installMethod=standalone". Any other
// value, or none, means that something else owns the executable, such as a package manager or a build from the
// sources, and update leaves it alone.
const InstallMethodStandalone = "standalone"

const updateHelp = `Installs the latest release, or the one you name.

Does:
  1. Downloads the release and checks it against SHA256SUMS.
  2. Replaces this executable.
Next:
  Run "lvh-yard ws compose update" to update the Workstation.
Note:
  - Only an executable from a release archive or the install script updates itself. A package manager updates the others.
  - --check shows the release and changes nothing.`

// NewUpdate replaces this executable with a release from GitHub, and invites the user to update the Workstation. It
// does not touch a Workstation Folder: the templates are inside the executable, so the new executable does that, in
// ws compose update.
func NewUpdate(options *Options) *cobra.Command {
	var check bool
	cmd := &cobra.Command{
		Use:   "update [version]",
		Short: "Installs a release of lvh-yard.",
		Long:  updateHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			wanted := ""
			if len(args) == 1 {
				wanted = strings.TrimPrefix(args[0], "v")
			}
			return runUpdate(options, cmd, updateRequest{wanted: wanted, check: check})
		},
	}
	cmd.Flags().BoolVar(&check, "check", false, "Shows the release to install. Changes nothing.")
	return cmd
}

type updateRequest struct {
	wanted string
	check  bool
}

func runUpdate(options *Options, cmd *cobra.Command, request updateRequest) error {
	if options.InstallMethod != InstallMethodStandalone {
		return errors.New("this lvh-yard does not update itself, because it was not installed from a release archive. Update it the way you installed it, for example with your package manager. Then run \"lvh-yard ws compose update\"")
	}
	out := cmd.OutOrStdout()
	platform, err := update.Platform(options.Process.OS(), runtime.GOARCH)
	if err != nil {
		return err
	}
	release, err := update.Find(options.HTTP, options.Releases, request.wanted, platform)
	if err != nil {
		return err
	}
	current := options.Version
	if request.check {
		fmt.Fprintf(out, "Installed: %s\nRelease:   %s\n", current, release.Version)
	}
	if release.Version == current {
		fmt.Fprintf(out, "lvh-yard %s is already installed. Nothing to do.\n", current)
		return nil
	}
	if order, ok := update.Compare(current, release.Version); ok && order > 0 && request.wanted == "" {
		fmt.Fprintf(out, "lvh-yard %s is newer than the latest release, %s. Nothing to do.\nTo install that release anyway:\n  lvh-yard update %s\n", current, release.Version, release.Version)
		return nil
	}
	if request.check {
		fmt.Fprintf(out, "To install it:\n  lvh-yard update %s\n", release.Version)
		return nil
	}
	path, err := options.Executable()
	if err != nil {
		return fmt.Errorf("could not find this executable: %w", err)
	}
	fmt.Fprintf(out, "Updating lvh-yard %s to %s for %s.\n", current, release.Version, platform)
	archive, err := release.Download(options.HTTP)
	if err != nil {
		return err
	}
	executable, err := update.Extract(archive, platform)
	if err != nil {
		return err
	}
	if err := update.Replace(path, executable); err != nil {
		return fmt.Errorf("could not replace %s: %w. If you cannot write there, run the install script again with sudo, or set LVH_YARD_INSTALL_DIR to a folder you own", path, err)
	}
	fmt.Fprintf(out, "Installed %s.\n", path)
	inviteWorkstationUpdate(options, out)
	return nil
}

// inviteWorkstationUpdate says what to run next. Only the new executable has the files of the new release, so the
// Workstation is updated by a second command, which the user runs. With no Workstation Folder, there is nothing to
// update, and the invitation is to make one.
func inviteWorkstationUpdate(options *Options, out io.Writer) {
	folder := options.WorkstationFolder()
	if _, err := os.Stat(filepath.Join(folder, "compose.yaml")); err != nil {
		fmt.Fprintf(out, "No Workstation Folder at %s. To make one:\n  lvh-yard ws init\n", folder)
		return
	}
	fmt.Fprintf(out, "To update this Workstation to the new release:\n  lvh-yard ws compose update\n")
}
