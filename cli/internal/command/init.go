package command

import (
	"bufio"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"unicode"

	"github.com/ppalazon/lvh-yard/cli/internal/envfile"
	"github.com/ppalazon/lvh-yard/cli/internal/settings"
	"github.com/ppalazon/lvh-yard/cli/internal/templates"
	"github.com/spf13/cobra"
)

type initOptions struct {
	workstation  string
	baseDomain   string
	peers        []string
	gossipKey    string
	force        bool
	dockerSocket string
	hostname     string
	terminal     bool
}

func newWSInit(options *Options, folder func() string) *cobra.Command {
	flags := &initOptions{}
	cmd := &cobra.Command{
		Use:   "init",
		Short: "Writes the setup files for this Workstation.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return flags.create(cmd, options, folder())
		},
	}
	f := cmd.Flags()
	f.StringVar(&flags.workstation, "workstation", "", "Sets this Workstation's unique name in the Yard.")
	f.StringVar(&flags.baseDomain, "base-domain", "", "Sets the Base Domain at the end of this Workstation's hostnames.")
	f.StringArrayVar(&flags.peers, "peers", nil, "Sets the LAN addresses of other Workstations, separated by spaces or commas.")
	f.StringVar(&flags.gossipKey, "gossip-key", "", "Uses the Yard's shared Gossip Key.")
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
	domain, err := f.baseDomainValue(cmd, prompt, env.Get("YARD_BASE_DOMAIN"))
	if err != nil {
		return err
	}
	key, err := f.gossipKeyValue(cmd)
	if err != nil {
		return err
	}
	peers, err := f.checkedPeers()
	if err != nil {
		return err
	}
	if err := f.fillEnv(env, options, name, domain, key, peers); err != nil {
		return err
	}
	compose, err := templates.Compose(options.Version)
	if err != nil {
		return err
	}
	if err := writeWorkstationFiles(folder, compose, env.Text(), f.force); err != nil {
		return err
	}
	if strings.TrimSpace(env.Get("YARD_DOCKER_GID")) == "" {
		fmt.Fprintf(cmd.ErrOrStderr(), "Warning: no Docker socket at %s, so YARD_DOCKER_GID in .env is empty. Set it to the group of /var/run/docker.sock, or the dashboard will not start.\n", f.dockerSocket)
	}
	fmt.Fprintf(cmd.OutOrStdout(), "Wrote compose.yaml, .env, lvh-yard.yaml to %s.\n\n", folder)
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
		return "", fmt.Errorf("Missing %s: no terminal to ask for it, so give it as %s", flag, flag)
	}
	fmt.Fprint(p.out, question)
	line, err := p.answers.ReadString('\n')
	if err != nil && (err != io.EOF || len(line) == 0) {
		fmt.Fprintln(p.out)
		if err == io.EOF {
			return "", fmt.Errorf("Missing %s: the input ended before it was given", flag)
		}
		return "", fmt.Errorf("Missing %s: could not read it: %w", flag, err)
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

const workstationNameRule = "use lowercase letters, digits and -, at most 63, not starting or ending with -"

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
		question := "Workstation name, unique in the Yard (lowercase letters, digits and -)"
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

// baseDomainValue is the --base-domain flag, or the answer at a terminal, or the template's.
func (f *initOptions) baseDomainValue(cmd *cobra.Command, prompt *initPrompt, templateDomain string) (string, error) {
	domain := f.baseDomain
	if !cmd.Flags().Changed("base-domain") {
		domain = templateDomain
		if prompt.terminal {
			var err error
			domain, err = prompt.askValid("Base Domain ["+templateDomain+"]: ", "--base-domain", templateDomain, settings.ValidBaseDomain, func(answer string) string {
				return answer + " is not a domain name such as lvh.me."
			})
			if err != nil {
				return "", err
			}
		}
	}
	if !settings.ValidBaseDomain(domain) {
		return "", fmt.Errorf("--base-domain %s is not a domain name such as lvh.me", domain)
	}
	return domain, nil
}

// gossipKeyValue is the --gossip-key flag, checked, or a new random key.
func (f *initOptions) gossipKeyValue(cmd *cobra.Command) (string, error) {
	if cmd.Flags().Changed("gossip-key") || f.gossipKey != "" {
		return f.gossipKey, checkInitGossipKey(f.gossipKey)
	}
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		return "", fmt.Errorf("could not make a Gossip Key: %w", err)
	}
	return base64.StdEncoding.EncodeToString(bytes), nil
}

// checkedPeers joins the --peers values, which may hold several addresses, and checks them.
func (f *initOptions) checkedPeers() (string, error) {
	var peers []string
	for _, list := range f.peers {
		peers = append(peers, strings.FieldsFunc(list, func(r rune) bool { return r == ',' || unicode.IsSpace(r) })...)
	}
	return settings.Check("peers", strings.Join(peers, " "))
}

// fillEnv gives the template's empty settings a value of this computer, and sets the answers.
func (f *initOptions) fillEnv(env *envfile.File, options *Options, name, domain, key, peers string) error {
	for _, found := range [][2]string{
		{"COMPOSE_PROFILES", "dashboard,traefik"},
		{"YARD_UID", options.Process.ID("-u")},
		{"YARD_GID", options.Process.ID("-g")},
		{"YARD_DOCKER_GID", dockerGroup(f.dockerSocket)},
	} {
		if err := env.FillIfEmpty(found[0], found[1]); err != nil {
			return err
		}
	}
	for _, setting := range [][2]string{{"YARD_WORKSTATION", name}, {"YARD_BASE_DOMAIN", domain}, {"YARD_GOSSIP_KEY", key}, {"YARD_PEERS", peers}} {
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
		return fmt.Errorf("--gossip-key is not a Gossip Key: copy the Yard's key, Base64 of 32 bytes, from another Workstation's .env")
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

func dockerGroup(socket string) string {
	info, err := os.Stat(socket)
	if err != nil {
		return ""
	}
	if stat, ok := info.Sys().(*syscall.Stat_t); ok {
		return fmt.Sprint(stat.Gid)
	}
	return ""
}

func writeWorkstationFiles(folder, compose, env string, force bool) error {
	files := []struct {
		name string
		text string
		mode os.FileMode
	}{{"compose.yaml", compose, 0600}, {".env", env, 0600}, {"lvh-yard.yaml", templates.Dashboard(), 0600}, {"consul.d/00-lvh-yard.hcl", templates.Consul(), 0600}}
	if !force {
		for _, file := range files {
			path := filepath.Join(folder, file.name)
			if _, err := os.Lstat(path); err == nil {
				return fmt.Errorf("%s already exists: nothing was written. Give --force to overwrite", path)
			} else if !os.IsNotExist(err) {
				return fmt.Errorf("could not read %s: %w", path, err)
			}
		}
	}
	// Create these before Compose starts, or Docker creates them owned by root.
	for _, name := range []string{"backups", "consul-data", "consul.d", "traefik/dynamic", "tls/consul", "tls/traefik"} {
		if err := os.MkdirAll(filepath.Join(folder, name), 0700); err != nil {
			return fmt.Errorf("could not create %s: %w", folder, err)
		}
	}
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	for _, file := range files {
		path := filepath.Join(folder, file.name)
		flags := os.O_WRONLY | os.O_CREATE | os.O_EXCL
		if force {
			flags = os.O_WRONLY | os.O_CREATE | os.O_TRUNC
		}
		output, err := os.OpenFile(path, flags, file.mode)
		if err != nil {
			return fmt.Errorf("could not write %s: %w", path, err)
		}
		if err := output.Chmod(0600); err != nil {
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
	}
	return protectWorkstationFolder(folder)
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
	fmt.Fprintln(out, "Next steps:")
	fmt.Fprintf(out, "\n1. Start Consul, Traefik and the dashboard. Choose ONE:\n\n   A. Workstation command (recommended):\n      %s compose start\n\n   B. Docker Compose (same action):\n      docker compose --project-directory %s up -d\n", ws, quoteShell(folder))
	fmt.Fprintf(out, "\n2. Check the status:\n   %s\n", status)
	fmt.Fprintf(out, "\n3. Open either address:\n   %s\n   %s\n", dashboard, yard)
	fmt.Fprintf(out, "\n4. Connect other Workstations. Choose ONE:\n\n   A. Invite a Workstation into this Yard:\n      %s yard share\n\n   B. Join another Yard:\n      Ask a member for its invitation. For command help:\n      lvh-yard ws yard join --help\n", ws)
}
