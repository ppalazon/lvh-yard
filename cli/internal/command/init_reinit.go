package command

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

var initSetting = regexp.MustCompile(`^[A-Z][A-Z0-9_]*$`)

func (f *initOptions) recreate(cmd *cobra.Command, options *Options, folder string) error {
	path := filepath.Join(folder, ".env")
	text, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return fmt.Errorf("No %s to carry values from: nothing was written. Run lvh-yard init without --reinit to write new files", path)
	}
	if err != nil {
		return fmt.Errorf("could not read %s: %w", path, err)
	}
	return f.create(cmd, options, folder, parseEnvFile(string(text)))
}

func carryInitSettings(env, old *envFile) error {
	seen := make(map[string]bool)
	var gone []string
	for _, line := range old.lines {
		key, value, setting := strings.Cut(line, "=")
		if !setting || !initSetting.MatchString(key) || seen[key] {
			continue
		}
		seen[key] = true
		known := false
		for _, current := range env.lines {
			if strings.HasPrefix(strings.TrimPrefix(current, "#"), key+"=") {
				known = true
				break
			}
		}
		if known {
			if err := env.set(key, value); err != nil {
				return err
			}
		} else {
			gone = append(gone, "#"+key+"="+value)
		}
	}
	if len(gone) > 0 {
		env.lines = append(env.lines, "", "# No longer settings of lvh-yard, kept from the old .env:")
		env.lines = append(env.lines, gone...)
	}
	return nil
}

func backUpInitFiles(out io.Writer, folder string) error {
	stamp := time.Now().Format("20060102-150405")
	var files [][2]string
	for _, name := range []string{"compose.yaml", ".env"} {
		path := filepath.Join(folder, name)
		if _, err := os.Lstat(path); os.IsNotExist(err) {
			continue
		} else if err != nil {
			return fmt.Errorf("could not read %s: %w", path, err)
		}
		backup := path + "." + stamp + ".bak"
		if _, err := os.Lstat(backup); err == nil {
			return fmt.Errorf("%s already exists: nothing was written", backup)
		} else if !os.IsNotExist(err) {
			return fmt.Errorf("could not read %s: %w", backup, err)
		}
		files = append(files, [2]string{path, backup})
	}
	for _, file := range files {
		if err := os.Rename(file[0], file[1]); err != nil {
			return fmt.Errorf("could not back up %s to %s: %w", file[0], file[1], err)
		}
		fmt.Fprintf(out, "Backed up %s to %s.\n", filepath.Base(file[0]), file[1])
	}
	return nil
}
