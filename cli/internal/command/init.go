package command

import (
	"bufio"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
	"github.com/spf13/cobra"
)

type initOptions struct {
	workstation  string
	baseDomain   string
	address      string
	force        bool
	dockerSocket string
	hostname     string
	terminal     bool
}

const initHelp = `Writes the setup files for this Workstation.

Does:
  1. Asks for the name, Base Domain and LAN address when a flag is missing.
  2. Makes a Gossip Key.
  3. Writes compose.yaml, .env, lvh-yard.yaml and consul.d/.

Next: start the Compose services, as it prints.
Note:
  - it stops when a file exists. Use --force to overwrite.
  - the Base Domain has no default. Use a domain that resolves every name to 127.0.0.1, such as lvh.me or l.lvhy.cc.`

func newWSInit(options *Options, folder func() string) *cobra.Command {
	flags := &initOptions{}
	cmd := &cobra.Command{
		Use:   "init",
		Short: "Writes the setup files of this Workstation.",
		Long:  initHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return flags.create(cmd, options, folder())
		},
	}
	f := cmd.Flags()
	f.StringVar(&flags.workstation, "workstation", "", "Sets the name of this Workstation.")
	f.StringVar(&flags.baseDomain, "base-domain", "", "Sets the Base Domain, such as lvh.me or l.lvhy.cc.")
	f.StringVar(&flags.address, "address", "", "Sets the LAN IPv4 address of this Workstation.")
	f.BoolVar(&flags.force, "force", false, "Overwrites the files when they exist.")
	f.StringVar(&flags.dockerSocket, "docker-socket", "/var/run/docker.sock", "Reads the Docker group from this socket.")
	f.StringVar(&flags.hostname, "hostname", "", "Offers this hostname as the Workstation name.")
	f.BoolVar(&flags.terminal, "terminal", false, "Reads answers as if at a terminal.")
	for _, name := range []string{"docker-socket", "hostname", "terminal"} {
		_ = f.MarkHidden(name)
	}
	return cmd
}

// create asks for what the flags left out, fills .env from the template and writes the files.
func (f *initOptions) create(cmd *cobra.Command, options *Options, folder string) error {
	env := envfile.Parse(templates.Env())
	prompt := &initPrompt{
		out:      cmd.OutOrStdout(),
		terminal: f.terminal || options.Process.IsTerminal(),
		answers:  bufio.NewReader(options.Process.Input()),
	}
	name, err := f.workstationName(cmd, prompt)
	if err != nil {
		return err
	}
	domain, err := f.baseDomainValue(cmd, prompt)
	if err != nil {
		return err
	}
	key, err := newGossipKey()
	if err != nil {
		return err
	}
	if err := f.lanIdentity(prompt, options, env); err != nil {
		return err
	}
	if err := f.fillEnv(env, options, folder, name, domain, key); err != nil {
		return err
	}
	compose, err := templates.Compose(options.Version)
	if err != nil {
		return err
	}
	if err := writeWorkstationFiles(folder, compose, env.Text(), f.force); err != nil {
		return err
	}
	if warning := dockerGroupWarning(env, f.dockerSocket); warning != "" {
		fmt.Fprintln(cmd.ErrOrStderr(), warning)
	}
	fmt.Fprintf(cmd.OutOrStdout(), "Wrote compose.yaml, .env and lvh-yard.yaml in %s.\n\n", folder)
	printInitNextSteps(cmd.OutOrStdout(), folder, env, options.WorkstationFolder())
	return nil
}

// initPrompt asks at a terminal. Without one, a question is an error that names the flag.
type initPrompt struct {
	out      io.Writer
	terminal bool
	answers  *bufio.Reader
}

func (p *initPrompt) ask(question, flag string) (string, error) {
	if !p.terminal {
		return "", fmt.Errorf("Missing %s. No terminal is open: pass %s", flag, flag)
	}
	fmt.Fprint(p.out, question)
	line, err := p.answers.ReadString('\n')
	if err != nil && (err != io.EOF || len(line) == 0) {
		fmt.Fprintln(p.out)
		if err == io.EOF {
			return "", fmt.Errorf("Missing %s. The input ended: pass %s", flag, flag)
		}
		return "", fmt.Errorf("Missing %s. Could not read the answer: %w", flag, err)
	}
	return strings.TrimSpace(line), nil
}

// askValid asks again until the answer, or the fallback for an empty one, is valid.
func (p *initPrompt) askValid(question, flag, fallback string, valid func(string) bool, refusal func(string) string) (string, error) {
	for {
		typed, err := p.ask(question, flag)
		if err != nil {
			return "", err
		}
		answer := typed
		if answer == "" {
			answer = fallback
		}
		if valid(answer) {
			return answer, nil
		}
		fmt.Fprintln(p.out, refusal(answer))
	}
}

const workstationNameRule = "use lowercase letters, digits and -; at most 63 characters; no - at the start or end"

// workstationName is the --workstation flag, or the answer to a question that offers the name of
// this computer.
func (f *initOptions) workstationName(cmd *cobra.Command, prompt *initPrompt) (string, error) {
	name := f.workstation
	if !cmd.Flags().Changed("workstation") && name == "" {
		hostname := f.hostname
		if !cmd.Flags().Changed("hostname") {
			hostname, _ = os.Hostname()
		}
		offered := nameFromHostname(hostname)
		question := "Workstation name (lowercase letters, digits and -)"
		if offered != "" {
			question += " [" + offered + "]"
		}
		var err error
		name, err = prompt.askValid(question+": ", "--workstation", offered, settings.ValidWorkstationName, func(answer string) string {
			if answer == "" {
				return "A Workstation needs a name: " + workstationNameRule + "."
			}
			return answer + " is not a Workstation name: " + workstationNameRule + "."
		})
		if err != nil {
			return "", err
		}
	}
	if !settings.ValidWorkstationName(name) {
		return "", fmt.Errorf("--workstation %s is not a Workstation name: %s", name, workstationNameRule)
	}
	return name, nil
}

// baseDomainValue is the --base-domain flag, or the answer at a terminal. It has no default: the
// right domain depends on the person's network, so a missing one is asked for or refused.
func (f *initOptions) baseDomainValue(cmd *cobra.Command, prompt *initPrompt) (string, error) {
	domain := f.baseDomain
	if !cmd.Flags().Changed("base-domain") {
		if prompt.terminal {
			fmt.Fprintln(prompt.out, baseDomainExplanation)
		}
		var err error
		domain, err = prompt.askValid("Base Domain: ", "--base-domain", "", settings.ValidBaseDomain, func(answer string) string {
			if answer == "" {
				return "A Workstation needs a Base Domain. " + baseDomainRule
			}
			return answer + " is not a domain name. " + baseDomainRule
		})
		if err != nil {
			return "", err
		}
	}
	if !settings.ValidBaseDomain(domain) {
		return "", fmt.Errorf("--base-domain %s is not a domain name. %s", domain, baseDomainRule)
	}
	return domain, nil
}

const baseDomainRule = "Use a domain that resolves every name to 127.0.0.1, such as lvh.me or l.lvhy.cc."

const baseDomainExplanation = `The Base Domain ends every hostname that this Workstation routes, such as web.<Base Domain>.
Use a wildcard domain that resolves every name to 127.0.0.1, such as "lvh.me" or "l.lvhy.cc".
If you are offline, or your router blocks such answers, use localhost.`

// newGossipKey is a random Gossip Key: Base64 of 32 bytes.
func newGossipKey() (string, error) {
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		return "", fmt.Errorf("could not make the Gossip Key: %w", err)
	}
	return base64.StdEncoding.EncodeToString(bytes), nil
}

// fillEnv gives the template's empty settings a value of this computer, and sets the answers.
func (f *initOptions) fillEnv(env *envfile.File, options *Options, folder, name, domain, key string) error {
	if err := env.FillIfEmpty("COMPOSE_PROFILES", "dashboard,traefik"); err != nil {
		return err
	}
	if err := fillPlatformEnv(env, options, folder, f.dockerSocket); err != nil {
		return err
	}
	for _, setting := range [][2]string{{"YARD_WORKSTATION", name}, {"YARD_BASE_DOMAIN", domain}, {"YARD_GOSSIP_KEY", key}} {
		if err := env.Set(setting[0], setting[1]); err != nil {
			return err
		}
	}
	return nil
}

func checkInitGossipKey(key string) error {
	decoded, err := base64.StdEncoding.DecodeString(key)
	if err != nil && !strings.Contains(key, "=") {
		decoded, err = base64.RawStdEncoding.DecodeString(key)
	}
	if err != nil || (len(decoded) != 16 && len(decoded) != 24 && len(decoded) != 32) {
		return fmt.Errorf("the Gossip Key is not valid. Use Base64 of 16, 24 or 32 bytes")
	}
	return nil
}

func nameFromHostname(hostname string) string {
	name, _, _ := strings.Cut(strings.TrimSpace(hostname), ".")
	name = strings.Map(func(r rune) rune {
		if r >= 'A' && r <= 'Z' {
			return r + ('a' - 'A')
		}
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') || r == '-' {
			return r
		}
		return '-'
	}, name)
	name = strings.TrimLeft(name, "-")
	if len(name) > 63 {
		name = name[:63]
	}
	return strings.TrimRight(name, "-")
}

func (f *initOptions) lanIdentity(prompt *initPrompt, options *Options, env *envfile.File) error {
	if !lanAddressRequired && f.address == "" {
		// A new Workstation listens on this computer only. ws yard share and ws yard join open it to
		// the LAN.
		return env.Set("YARD_BIND_ADDRESS", loopbackAddress)
	}
	address := f.address
	valid := func(value string) bool { return isGlobalUnicastIPv4(net.ParseIP(value)) }
	if address == "" {
		offered, selectionErr := options.Process.LANAddress()
		if selectionErr != nil {
			fmt.Fprintln(prompt.out, selectionErr)
		}
		if prompt.terminal || !valid(offered) {
			var err error
			address, err = prompt.askValid("LAN IPv4 address ["+offered+"]: ", "--address", offered, valid, func(string) string {
				return "Use a LAN IPv4 address that the other Workstations can reach."
			})
			if err != nil {
				return err
			}
		} else {
			address = offered
		}
	}
	if !valid(address) {
		return fmt.Errorf("invalid --address. Use a LAN IPv4 address that the other Workstations can reach")
	}
	return env.Set("YARD_BIND_ADDRESS", address)
}

type workstationFile struct {
	name string
	text string
}

// workstationFiles lists the files that ws init writes. Each one is private to the user.
func workstationFiles(compose, env string) []workstationFile {
	files := []workstationFile{{"compose.yaml", compose}, {".env", env}, {"lvh-yard.yaml", templates.Dashboard()}, {"consul.d/00-lvh-yard.hcl", templates.Consul()}, {layoutFile, layoutText()}}
	return append(files, scriptFiles(false)...)
}

// scriptFiles lists the scripts that compose.yaml mounts. ws init writes them plain, because they are the user's
// to change. Any other command writes them with a notice that it can overwrite them.
func scriptFiles(generated bool) []workstationFile {
	var files []workstationFile
	for _, script := range templates.Scripts() {
		text := script.Text
		if generated {
			text = templates.Generated(text)
		}
		files = append(files, workstationFile{script.Name, text})
	}
	return files
}

func writeWorkstationFiles(folder, compose, env string, force bool) error {
	files := workstationFiles(compose, env)
	if err := validateWorkstationStorage(folder); err != nil {
		return err
	}
	if err := os.MkdirAll(folder, 0700); err != nil {
		return fmt.Errorf("could not create %s: %w", folder, err)
	}
	// Windows protects the new folder before any secret is written. On Unix, MkdirAll already made it 0700, and a
	// refused init must leave the modes of an existing folder alone.
	if err := protectNewFolder(folder); err != nil {
		return err
	}
	root, err := os.OpenRoot(folder)
	if err != nil {
		return fmt.Errorf("could not read %s: %w", folder, err)
	}
	defer func() { _ = root.Close() }()
	if !force {
		if err := refuseExistingFiles(root, folder, files); err != nil {
			return err
		}
	}
	// Create these before Compose starts, or Docker creates them owned by root.
	for _, name := range []string{"backups", "consul.d", "scripts", "traefik/dynamic", "tls/consul", "tls/traefik"} {
		if err := root.MkdirAll(name, 0700); err != nil {
			return fmt.Errorf("could not create %s: %w", folder, err)
		}
	}
	if err := protectWorkstationTree(folder); err != nil {
		return err
	}
	for _, file := range files {
		if err := writeWorkstationFile(root, folder, file, force); err != nil {
			return err
		}
	}
	return protectWorkstationTree(folder)
}

func refuseExistingFiles(root *os.Root, folder string, files []workstationFile) error {
	for _, file := range files {
		path := filepath.Join(folder, file.name)
		if _, err := root.Lstat(file.name); err == nil {
			return fmt.Errorf("%s already exists. Nothing was written. Use --force to overwrite it", path)
		} else if !os.IsNotExist(err) {
			return fmt.Errorf("could not read %s: %w", path, err)
		}
	}
	return nil
}

func writeWorkstationFile(root *os.Root, folder string, file workstationFile, force bool) error {
	path := filepath.Join(folder, file.name)
	flags := os.O_WRONLY | os.O_CREATE | os.O_EXCL
	if force {
		flags = os.O_WRONLY | os.O_CREATE | os.O_TRUNC
	}
	output, err := root.OpenFile(file.name, flags, 0600)
	if err != nil {
		return fmt.Errorf("could not write %s: %w", path, err)
	}
	if err := protectFile(output, 0600); err != nil {
		_ = output.Close()
		return fmt.Errorf("could not protect %s: %w", path, err)
	}
	_, writeErr := io.WriteString(output, file.text)
	closeErr := output.Close()
	if writeErr != nil {
		return fmt.Errorf("could not write %s: %w", path, writeErr)
	}
	if closeErr != nil {
		return fmt.Errorf("could not write %s: %w", path, closeErr)
	}
	return nil
}

func printInitNextSteps(out io.Writer, folder string, env *envfile.File, defaultFolder string) {
	bind := env.ValueOrDefault("YARD_DASHBOARD_BIND_ADDRESS")
	if bind == "0.0.0.0" {
		bind = "127.0.0.1"
	}
	dashboard := "http://" + bind + ":" + env.ValueOrDefault("YARD_DASHBOARD_PORT")
	yard := "http://yard." + env.ValueOrDefault("YARD_BASE_DOMAIN")
	if port := env.ValueOrDefault("YARD_HTTP_PORT"); port != "80" {
		yard += ":" + port
	}
	ws := wsCommand(folder, defaultFolder)
	status := "lvh-yard status"
	if filepath.Clean(folder) != filepath.Clean(defaultFolder) {
		status = "lvh-yard --dashboard " + quoteShell(dashboard) + " status"
	}
	fmt.Fprintln(out, "Next:")
	fmt.Fprintf(out, "  1. Start Consul, Traefik and the dashboard. Use one of:\n       %s compose start\n       docker compose --project-directory %s up -d\n", ws, quoteShell(folder))
	fmt.Fprintf(out, "  2. Check the status:\n       %s\n", status)
	fmt.Fprintf(out, "  3. Open the dashboard or the Yard page:\n       %s\n       %s\n", dashboard, yard)
	fmt.Fprintf(out, "  4. To start a Yard, run in this order:\n       %[1]s tls ca create\n       %[1]s yard init\n       %[1]s yard share <workstation>\n     To join a Yard, ask a member for an Invitation, then run:\n       %[1]s yard join <zip file>\n", ws)
}
