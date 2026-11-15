package command

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"os/exec"
	"strings"
	"unicode"

	"github.com/spf13/cobra"
)

// The automated copy runs a Unix shell command on the other computer (umask, mkdir -p) and then
// scp. It works from a Unix computer to a Unix computer. Windows takes part only by hand: the
// sender saves the folders, and the recipient imports them with ws tls import. Both checks below
// run before the command writes a file or runs a command that changes anything, so a refused copy
// leaves both computers as they were.

const manualHandoffHelp = "docs/user-guide.md"

// checkNativeWindowsSender refuses --scp on the native Windows CLI. Windows has no umask, and the
// CLI does not run Unix commands on the other computer from there.
func checkNativeWindowsSender(options *Options, name, target string) error {
	if target == "" || options.Process.OS() != "windows" {
		return nil
	}
	return fmt.Errorf("--scp: native Windows cannot copy the files with ssh. Nothing was changed.\n"+
		"Run the command again without --scp. Send the folders it saves in certificates, one for each certificate, through a private channel. Never send ca.key.\n"+
		"Then, on %s, run lvh-yard ws tls import for each folder. See %s.", name, manualHandoffHelp)
}

// checkHandoffDestination refuses a Windows destination before the first remote command that could
// change it. A Windows folder shows in the target itself. Any other destination answers a
// read-only probe: a Unix shell knows uname, and cmd.exe on Windows does not.
func checkHandoffDestination(cmd *cobra.Command, options *Options, name, target string) error {
	if target == "" {
		return nil
	}
	if err := checkSCPHost(target); err != nil {
		return fmt.Errorf("--scp: %w", err)
	}
	host, folder, _ := splitSCPTarget(target)
	if windowsFolder(folder) {
		return windowsDestinationError(name, target)
	}
	var stdout, stderr bytes.Buffer
	code, err := options.Process.Run("ssh", []string{host, "uname -s"}, "", &stdout, &stderr)
	if errors.Is(err, exec.ErrNotFound) {
		return fmt.Errorf("ssh is not on the PATH: copy the files yourself, as described in %s", manualHandoffHelp)
	}
	if err != nil {
		return err
	}
	switch {
	case code == 255:
		// ssh itself failed: no connection, no login. The destination may be Unix.
		_, _ = io.Copy(cmd.ErrOrStderr(), &stderr)
		return fmt.Errorf("ssh failed with exit code %d: copy the files yourself, as described in %s", code, manualHandoffHelp)
	case code != 0, unixLookalikeOfWindows(stdout.String()):
		return windowsDestinationError(name, target)
	}
	return nil
}

// windowsFolder reports a folder that only Windows writes: a drive letter or a backslash.
func windowsFolder(folder string) bool {
	if strings.Contains(folder, `\`) {
		return true
	}
	return len(folder) >= 2 && folder[1] == ':' && unicode.IsLetter(rune(folder[0]))
}

// unixLookalikeOfWindows reports the uname of Git for Windows, MSYS2 and Cygwin: they run a Unix
// shell on a Windows computer, where the paths and the permissions are not Unix ones.
func unixLookalikeOfWindows(uname string) bool {
	upper := strings.ToUpper(uname)
	for _, system := range []string{"MINGW", "MSYS", "CYGWIN", "WINDOWS"} {
		if strings.Contains(upper, system) {
			return true
		}
	}
	return false
}

func windowsDestinationError(name, target string) error {
	return fmt.Errorf("--scp: %s does not look like a Unix computer, but like Windows. The automated copy needs a Unix shell there. Nothing was changed on either computer.\n"+
		"Run the command again without --scp. Send the folders it saves in certificates, one for each certificate, through a private channel. Never send ca.key.\n"+
		"Then, on %s, run lvh-yard ws tls import for each folder. See %s.", target, name, manualHandoffHelp)
}
