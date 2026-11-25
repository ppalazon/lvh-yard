package command

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/ppalazon/lvh-yard/cli/internal/composefile"
	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
)

// upgradeServices are the services whose image the upgrade changes. The compose.yaml must have all of them.
var upgradeServices = []string{"consul", "traefik", "dashboard"}

// dataInitService is the one-shot service that prepares the Consul data volume. The upgrade changes its image when
// compose.yaml has it. A Linux folder from before the volume has none, and still upgrades.
const dataInitService = "consul-data-init"

func upgradeInit(options *Options, folder, ws string, out, errOut io.Writer) error {
	text, images, err := readComposeImages(folder)
	if err != nil {
		return err
	}
	template, err := templates.Compose(options.Version)
	if err != nil {
		return err
	}
	desired := composefile.Images(template)
	backupBeforeUpdate(options, folder, out, errOut)
	if err := writeFixedFiles(folder); err != nil {
		return err
	}
	if err := upgradeImages(folder, text, images, desired, out); err != nil {
		return err
	}
	if err := runMigrations(folder, out); err != nil {
		return err
	}
	fmt.Fprintf(out, "Updated %s.\nNext:\n  %s compose recreate\n", folder, ws)
	if strings.Contains(text, "./tls:/consul/tls") || strings.Contains(text, "./tls:/etc/traefik/tls") {
		fmt.Fprintln(out, "Warning: compose.yaml still mounts ./tls in Consul and Traefik. Certificates now live in tls/consul and tls/traefik.\n  1. Mount ./tls/consul:/consul/tls:ro in Consul.\n  2. Mount ./tls/traefik:/etc/traefik/tls:ro in Traefik.\n  3. Install the certificates again.")
	}
	return nil
}

// readComposeImages reads compose.yaml and refuses one that lacks the image of a service to upgrade.
func readComposeImages(folder string) (string, []composefile.Image, error) {
	file := filepath.Join(folder, "compose.yaml")
	root, err := os.OpenRoot(folder)
	if err != nil {
		if os.IsNotExist(err) {
			return "", nil, fmt.Errorf("%s does not exist. Nothing to update. Run \"lvh-yard ws init\" first", file)
		}
		return "", nil, fmt.Errorf("Could not read %s: %w", file, err)
	}
	defer func() { _ = root.Close() }()
	data, err := root.ReadFile("compose.yaml")
	if os.IsNotExist(err) {
		return "", nil, fmt.Errorf("%s does not exist. Nothing to update. Run \"lvh-yard ws init\" first", file)
	}
	if err != nil {
		return "", nil, fmt.Errorf("Could not read %s: %w", file, err)
	}
	text := string(data)
	images := composefile.Images(text)
	var missing []string
	for _, service := range upgradeServices {
		if _, ok := composefile.Find(images, service); !ok {
			missing = append(missing, service)
		}
	}
	if len(missing) != 0 {
		return "", nil, fmt.Errorf("%s has no image: line for %s. Nothing was changed. Edit the images by hand", file, strings.Join(missing, ", "))
	}
	return text, images, nil
}

// writeFixedFiles writes the files that LVH Yard writes again at each upgrade, and makes the TLS options follow
// the ones that are on.
func writeFixedFiles(folder string) error {
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	for _, name := range []string{"consul.d", "scripts", "traefik/dynamic", "tls/consul", "tls/traefik"} {
		if err := root.MkdirAll(name, 0700); err != nil {
			return fmt.Errorf("could not create %s: %w", name, err)
		}
	}
	if err := root.WriteFile("consul.d/00-lvh-yard.hcl", []byte(templates.Consul()), 0600); err != nil {
		return fmt.Errorf("could not write fixed Consul options: %w", err)
	}
	for _, file := range scriptFiles(true) {
		if err := root.WriteFile(file.name, []byte(file.text), 0600); err != nil {
			return fmt.Errorf("could not write %s: %w", file.name, err)
		}
	}
	if _, err := root.Stat("consul.d/10-tls.hcl"); err == nil {
		if err := writeConsulTLS(folder); err != nil {
			return err
		}
	} else if !os.IsNotExist(err) {
		return err
	}
	if err := regenerateTraefikTLS(folder); err != nil {
		return err
	}
	return protectWorkstationTree(folder)
}

// upgradeImages says for each service whether its image changes, and writes compose.yaml with the
// new images when one does.
func upgradeImages(folder, text string, images, desired []composefile.Image, out io.Writer) error {
	changed := false
	services := append([]string{}, upgradeServices...)
	if _, found := composefile.Find(images, dataInitService); found {
		services = append(services, dataInitService)
	}
	for _, service := range services {
		from, _ := composefile.Find(images, service)
		to, ok := composefile.Find(desired, service)
		if !ok {
			return fmt.Errorf("compose.yaml template has no image: line for %s", service)
		}
		if from.Value == to.Value {
			fmt.Fprintf(out, "%s: %s (already this version)\n", service, to.Value)
		} else {
			changed = true
			fmt.Fprintf(out, "%s: %s -> %s\n", service, from.Value, to.Value)
		}
	}
	if !changed {
		return nil
	}
	file := filepath.Join(folder, "compose.yaml")
	root, err := os.OpenRoot(folder)
	if err != nil {
		return fmt.Errorf("Could not write %s: %w", file, err)
	}
	defer func() { _ = root.Close() }()
	if err := root.WriteFile("compose.yaml", []byte(composefile.Replace(text, images, desired, services)), 0600); err != nil {
		return fmt.Errorf("Could not write %s: %w", file, err)
	}
	return nil
}

// backupBeforeUpdate writes a Backup to the Backups folder before anything changes, in the way of the safety Backup
// of an import. It only warns when it cannot: the data lives in a Docker volume, which the update does not touch, and
// a Workstation that is stopped has no dashboard to ask.
func backupBeforeUpdate(options *Options, folder string, out, errOut io.Writer) {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return
	}
	defer func() { _ = root.Close() }()
	env, _ := root.ReadFile(".env")
	warn := func(reason error) {
		fmt.Fprintf(errOut, "Warning: no Backup was written before the update: %v\n", reason)
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
	fmt.Fprintf(out, "Backup before the update: %s\n", path)
}
