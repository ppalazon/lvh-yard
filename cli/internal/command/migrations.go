package command

import (
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"
)

// layoutFile holds the layout number of a Workstation Folder: how many of the migrations below it has had. A folder
// from before this file has none, and counts as layout 0.
const layoutFile = ".lvh-yard-layout"

// migration changes a Workstation Folder, or tells the user what to change, when a new release needs it. It runs once
// in ws compose upgrade, which lvh-yard update runs after it replaces the executable. The new executable runs it, so
// a migration may use the templates of the release that needs it.
//
// To add one, append it to migrations. Never reorder or remove one: a folder's layout is a place in this list. A
// migration reads the folder before it changes anything, so it is safe on a folder that it does not concern.
type migration struct {
	run func(folder string, out io.Writer) error
}

var migrations = []migration{
	{run: noteConsulDataFolder},
}

// currentLayout is the layout that this executable writes.
func currentLayout() int { return len(migrations) }

func layoutText() string { return strconv.Itoa(currentLayout()) + "\n" }

func readLayout(folder string) (int, error) {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return 0, err
	}
	defer func() { _ = root.Close() }()
	text, err := root.ReadFile(layoutFile)
	if os.IsNotExist(err) {
		return 0, nil
	}
	if err != nil {
		return 0, fmt.Errorf("could not read %s: %w", layoutFile, err)
	}
	layout, err := strconv.Atoi(strings.TrimSpace(string(text)))
	if err != nil || layout < 0 {
		return 0, fmt.Errorf("%s in %s does not hold a layout number", layoutFile, folder)
	}
	return layout, nil
}

func writeLayout(folder string, layout int) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	if err := root.WriteFile(layoutFile, []byte(strconv.Itoa(layout)+"\n"), 0600); err != nil {
		return fmt.Errorf("could not write %s: %w", layoutFile, err)
	}
	return protectWorkstationTree(folder)
}

// runMigrations runs each migration that this folder has not had, and records each one when it is done. A failure
// stops the run, and the next run starts at the migration that failed.
func runMigrations(folder string, out io.Writer) error {
	layout, err := readLayout(folder)
	if err != nil {
		return err
	}
	if layout > currentLayout() {
		fmt.Fprintf(out, "%s was written by a newer lvh-yard. Its migrations are skipped.\n", folder)
		return nil
	}
	for ; layout < currentLayout(); layout++ {
		if err := migrations[layout].run(folder, out); err != nil {
			return fmt.Errorf("migration %d of %d failed: %w", layout+1, currentLayout(), err)
		}
		if err := writeLayout(folder, layout+1); err != nil {
			return err
		}
	}
	return nil
}

// noteConsulDataFolder tells the owner of a Linux or macOS folder from before the data volume that the folder keeps
// using ./consul-data. compose.yaml is the user's file, and moving the data needs the containers stopped, so this
// changes nothing.
func noteConsulDataFolder(folder string, out io.Writer) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	text, err := root.ReadFile("compose.yaml")
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	if !strings.Contains(string(text), "./consul-data:/consul/data") {
		return nil
	}
	fmt.Fprintln(out, "Note: compose.yaml still keeps the Consul data in ./consul-data.\n  - New Workstations use the Docker volume consul-data.\n  - \"ws compose down -v\" does not remove ./consul-data.\nTo use the volume:\n  1. Export a Backup.\n  2. Run \"ws init\" in a new folder, with the same Workstation name.\n  3. Start the new Workstation.\n  4. Import the Backup.\n  5. Join the Yard again with \"ws yard join\", if this Workstation was in one.")
	return nil
}
