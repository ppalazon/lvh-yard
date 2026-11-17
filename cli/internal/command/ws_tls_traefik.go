package command

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/spf13/cobra"
)

// Traefik has three TLS parts: HTTPS for browsers, the check of the Client Certificates of people, and
// mutual TLS to the Services. Each has its own files in tls/traefik/, its own generated Traefik file
// in traefik/dynamic/ and its own setting in .env. So each one is made, imported, turned on and turned
// off alone, and turning one off keeps its files.
type traefikPart struct {
	name  string
	label string
	short string
	// files are the names in tls/traefik/ that the part needs.
	files []string
	// generated is the Traefik file in traefik/dynamic/ that holds the part's options, or empty when
	// the part has none. Mutual TLS has none: the dashboard writes the transport of each Service.
	generated string
	template  func() string
	// flag is the .env setting that says whether the part is on. HTTPS has none: it is on when
	// YARD_HTTPS_PORT has a value.
	flag string
	// check refuses files that Traefik could not use here.
	check func(env *envfile.File, files map[string][]byte) error
	// announce says what the part does now that it is on.
	announce func(cmd *cobra.Command, folder string, env *envfile.File)
}

const httpsPortSetting = "YARD_HTTPS_PORT"

// The three parts, in the order of the generated files.
var traefikParts = []traefikPart{httpsPart, clientPart, mtlsPart}

func (p traefikPart) isOn(env *envfile.File) bool {
	if p.flag == "" {
		return strings.TrimSpace(env.Get(httpsPortSetting)) != ""
	}
	return strings.Trim(env.Get(p.flag), "\"'") == "true"
}

// setOn changes the setting that says whether the part is on. HTTPS keeps a port that was set before.
func (p traefikPart) setOn(env *envfile.File, on bool) {
	switch {
	case p.flag != "":
		env.SetOrAppend(p.flag, strconv.FormatBool(on))
	case !on:
		env.SetOrAppend(httpsPortSetting, "")
	case strings.TrimSpace(env.Get(httpsPortSetting)) == "":
		env.SetOrAppend(httpsPortSetting, "443")
	}
}

func (p traefikPart) writeGenerated(folder string) error {
	if p.generated == "" {
		return nil
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	if err := root.MkdirAll(filepath.Join("traefik", "dynamic"), 0700); err != nil {
		return err
	}
	return root.WriteFile(filepath.Join("traefik", "dynamic", p.generated), []byte(p.template()), 0600)
}

func (p traefikPart) removeGenerated(folder string) error {
	if p.generated == "" {
		return nil
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	if err := root.Remove(filepath.Join("traefik", "dynamic", p.generated)); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

// enableSteps are the last steps of the help of "enable", which depend on the generated file.
func (p traefikPart) enableSteps() string {
	if p.generated == "" {
		return "  2. Sets " + p.flag + " in .env."
	}
	return "  2. Writes traefik/dynamic/" + p.generated + "."
}

// disableSteps are the steps of the help of "disable".
func (p traefikPart) disableSteps() string {
	if p.generated == "" {
		return "  1. Clears the setting in .env."
	}
	return "  1. Removes traefik/dynamic/" + p.generated + ".\n  2. Clears the setting in .env."
}

func (p traefikPart) writeFiles(folder string, files map[string][]byte) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	if err := root.MkdirAll(roleDir("", roleTraefik), 0700); err != nil {
		return err
	}
	for name, data := range files {
		if err := root.WriteFile(filepath.Join(roleDir("", roleTraefik), name), data, 0600); err != nil {
			return err
		}
	}
	return nil
}

// readFiles reads the files of the part from tls/traefik/.
func (p traefikPart) readFiles(folder string) (map[string][]byte, error) {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return nil, err
	}
	defer func() { _ = root.Close() }()
	files := make(map[string][]byte, len(p.files))
	for _, name := range p.files {
		data, err := root.ReadFile(filepath.Join(roleDir("", roleTraefik), name))
		if err != nil {
			return nil, fmt.Errorf("this Workstation has no %s files in tls/traefik. Run \"lvh-yard ws tls %s create\" or \"lvh-yard ws tls %s import\": %w", p.label, p.name, p.name, err)
		}
		files[name] = data
	}
	return files, nil
}

const roleTraefik = "traefik"

func errTraefikOff() error {
	return fmt.Errorf("Traefik is off on this Workstation. Turn it on with \"lvh-yard ws set traefik on\"")
}

// turnOn writes the generated Traefik file and the setting of a part, and saves .env. The caller has
// checked the files.
func (p traefikPart) turnOn(cmd *cobra.Command, folder string, env *envfile.File) error {
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	if err := p.writeGenerated(folder); err != nil {
		return err
	}
	p.setOn(env, true)
	p.announce(cmd, folder, env)
	if err := saveWSEnv(cmd, folder, env); err != nil {
		return err
	}
	return protectWorkstationFolder(folder)
}

// install checks the files of a part, copies them to tls/traefik/ and turns the part on. Nothing is
// written when a check fails.
func (p traefikPart) install(cmd *cobra.Command, folder string, env *envfile.File, files map[string][]byte) error {
	if traefikOff(env) {
		return errTraefikOff()
	}
	if err := p.check(env, files); err != nil {
		return err
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	if err := p.writeFiles(folder, files); err != nil {
		return err
	}
	fmt.Fprintf(cmd.OutOrStdout(), "Files for %s saved in %s.\n", p.label, roleDir(folder, roleTraefik))
	return p.turnOn(cmd, folder, env)
}

// newWSTLSPart is the command group of one part: create, import, enable and disable.
func newWSTLSPart(folder func() string, part traefikPart, long string, create, importer *cobra.Command) *cobra.Command {
	group := &cobra.Command{Use: part.name, Short: part.short, Long: long, Args: cobra.NoArgs}
	group.AddCommand(create, importer, newWSTLSPartEnable(folder, part), newWSTLSPartDisable(folder, part))
	return group
}

func newWSTLSPartEnable(folder func() string, part traefikPart) *cobra.Command {
	return &cobra.Command{
		Use: "enable", Short: "Turns on " + part.label + ".", Args: cobra.NoArgs,
		Long: "Turns on " + part.label + " with the files in tls/traefik/.\n\nBefore:\n  - Make or import them with \"ws tls " + part.name + " create\" or \"ws tls " + part.name + " import\".\n\nDoes:\n  1. Checks the files.\n" + part.enableSteps(),
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if traefikOff(env) {
				return errTraefikOff()
			}
			files, err := part.readFiles(folder())
			if err != nil {
				return err
			}
			if err := part.check(env, files); err != nil {
				return fmt.Errorf("the files in tls/traefik cannot be used for %s: %w", part.label, err)
			}
			return part.turnOn(cmd, folder(), env)
		},
	}
}

func newWSTLSPartDisable(folder func() string, part traefikPart) *cobra.Command {
	return &cobra.Command{
		Use: "disable", Short: "Turns off " + part.label + ".", Args: cobra.NoArgs,
		Long: "Turns off " + part.label + " and keeps its files in tls/traefik/.\n\nDoes:\n" + part.disableSteps(),
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if err := protectWorkstationFolder(folder()); err != nil {
				return err
			}
			if err := part.removeGenerated(folder()); err != nil {
				return err
			}
			part.setOn(env, false)
			fmt.Fprintf(cmd.OutOrStdout(), "%s is off. The files stay in %s.\n", part.label, roleDir(folder(), roleTraefik))
			return saveWSEnv(cmd, folder(), env)
		},
	}
}

// readImportFile reads a file that a flag names, which may be outside the Workstation Folder.
func readImportFile(flag, path string) ([]byte, error) {
	data, err := os.ReadFile(path) // #nosec G304 -- A flag selects an input file outside the Workstation Folder.
	if err != nil {
		return nil, fmt.Errorf("cannot read --%s: %w", flag, err)
	}
	return data, nil
}

// regenerateTraefikTLS writes again the generated Traefik file of each part that has one, so that an
// upgrade brings the new options. A Workstation with Traefik off keeps its files as they are.
func regenerateTraefikTLS(folder string) error {
	root, err := os.OpenRoot(folder)
	if err != nil {
		return err
	}
	defer func() { _ = root.Close() }()
	checked := false
	for _, part := range traefikParts {
		if part.generated == "" {
			continue
		}
		if _, err := root.Stat(filepath.Join("traefik", "dynamic", part.generated)); err != nil {
			if os.IsNotExist(err) {
				continue
			}
			return err
		}
		if !checked {
			env, err := readWSEnv(folder)
			if err != nil {
				return err
			}
			if traefikOff(env) {
				return nil
			}
			checked = true
		}
		if err := part.writeGenerated(folder); err != nil {
			return err
		}
	}
	return nil
}
