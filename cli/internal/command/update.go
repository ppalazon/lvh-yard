package command

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/ppalazon/lvh-yard/cli/internal/update"
	"github.com/spf13/cobra"
)

const updateHelp = `Installs the latest release, or the one you name, and upgrades this Workstation.

Does:
  1. Writes a Backup to the Backups folder.
  2. Replaces this executable.
  3. Runs "ws compose upgrade".
  4. Asks to recreate the services, at a terminal.
Note:
  - --check shows the release and changes nothing.`

// NewUpdate replaces this executable with a release from GitHub, then runs ws compose upgrade with the new executable.
// The templates are inside the executable, so only the new one can write the files of the new release.
func NewUpdate(options *Options) *cobra.Command {
	var check bool
	var dir string
	cmd := &cobra.Command{
		Use:   "update [version]",
		Short: "Installs a release and upgrades this Workstation.",
		Long:  updateHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			wanted := ""
			if len(args) == 1 {
				wanted = strings.TrimPrefix(args[0], "v")
			}
			folder := options.WorkstationFolder()
			if cmd.Flags().Changed("dir") {
				folder = dir
			}
			return runUpdate(options, cmd, updateRequest{wanted: wanted, check: check, folder: folder, folderGiven: cmd.Flags().Changed("dir")})
		},
	}
	cmd.Flags().BoolVar(&check, "check", false, "Shows the release to install. Changes nothing.")
	cmd.Flags().StringVar(&dir, "dir", "", "Upgrades this Workstation Folder.")
	return cmd
}

type updateRequest struct {
	wanted      string
	check       bool
	folder      string
	folderGiven bool
}

func runUpdate(options *Options, cmd *cobra.Command, request updateRequest) error {
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
	if current == "dev" {
		return errors.New("this is a development build and has no release to update from. Use the install script")
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
	backupBeforeUpdate(options, cmd, request.folder)
	if err := update.Replace(path, executable); err != nil {
		return fmt.Errorf("could not replace %s: %w. If you cannot write there, run the install script again with sudo, or set LVH_YARD_INSTALL_DIR to a folder you own", path, err)
	}
	fmt.Fprintf(out, "Installed %s.\n", path)
	return upgradeWorkstation(options, cmd, path, request)
}

// upgradeWorkstation runs ws compose upgrade with the new executable, and then offers to recreate the services.
func upgradeWorkstation(options *Options, cmd *cobra.Command, executable string, request updateRequest) error {
	out := cmd.OutOrStdout()
	folder, err := filepath.Abs(request.folder)
	if err != nil {
		return err
	}
	if _, err := os.Stat(filepath.Join(folder, "compose.yaml")); err != nil {
		fmt.Fprintf(out, "No Workstation Folder at %s. Nothing more to upgrade.\nTo make one:\n  lvh-yard ws init\n", folder)
		return nil
	}
	args := []string{"ws"}
	if request.folderGiven {
		args = append(args, "--dir", folder)
	}
	args = append(args, "compose", "upgrade")
	fmt.Fprintln(out)
	code, err := options.Process.Run(executable, args, folder, out, cmd.ErrOrStderr())
	if err != nil {
		return fmt.Errorf("lvh-yard is updated, but \"lvh-yard %s\" did not run: %w", strings.Join(args, " "), err)
	}
	if code != 0 {
		return fmt.Errorf("lvh-yard is updated, but \"lvh-yard %s\" failed with exit code %d. Fix the cause and run it again", strings.Join(args, " "), code)
	}
	prompt := newPrompter(cmd, options)
	if !prompt.terminal {
		return nil
	}
	recreate, err := prompt.yesNo("Recreate the services now?", false)
	if err != nil || !recreate {
		return err
	}
	return runWSCompose(options, cmd, folder, composeRecreate)
}

// backupBeforeUpdate writes a Backup to the Backups folder before anything changes, in the way of the safety Backup
// of an import. It only warns when it cannot: the data lives in a Docker volume, which the update does not touch, and
// a Workstation that is stopped has no dashboard to ask.
func backupBeforeUpdate(options *Options, cmd *cobra.Command, folder string) {
	folder, err := filepath.Abs(folder)
	if err != nil {
		return
	}
	if _, err := os.Stat(filepath.Join(folder, "compose.yaml")); err != nil {
		return // no Workstation Folder: nothing to back up
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return
	}
	defer func() { _ = root.Close() }()
	env, _ := root.ReadFile(".env")
	warn := func(reason error) {
		fmt.Fprintf(cmd.ErrOrStderr(), "Warning: no Backup was written before the update: %v\n", reason)
	}
	backup, err := options.Dashboard(options.dashboardAddressIn(folder)).Backup()
	if err != nil {
		warn(err)
		return
	}
	backups := filepath.Join(folder, "backups")
	if err := os.MkdirAll(backups, 0700); err != nil {
		warn(err)
		return
	}
	name := envfile.Setting(string(env), "YARD_WORKSTATION", "workstation")
	if !settings.ValidWorkstationName(name) {
		name = "workstation" // .env is a file of the user: never let it put a path in the file name
	}
	stem := name + "-" + time.Now().Format("20060102-150405") + "-before-update"
	file := stem + ".yaml"
	for n := 2; ; n++ {
		if _, err := root.Lstat(filepath.Join("backups", file)); os.IsNotExist(err) {
			break
		}
		file = stem + "-" + strconv.Itoa(n) + ".yaml"
	}
	path := filepath.Join(backups, file)
	if err := writePrivateFile(path, []byte(backup)); err != nil {
		warn(err)
		return
	}
	fmt.Fprintf(cmd.OutOrStdout(), "Backup before the update: %s\n", path)
}
