package command

import (
	"bufio"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"syscall"
	"unicode"

	"github.com/ppalazon/lvh-yard/cli/internal/templates"
	"github.com/spf13/cobra"
)

var workstationName = regexp.MustCompile(`^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`)
var baseDomainName = regexp.MustCompile(`^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$`)

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

func (f *initOptions) create(cmd *cobra.Command, options *Options, folder string) error {
	env := parseEnvFile(templates.Env())
	terminal := f.terminal || options.Process.IsTerminal()
	answers := bufio.NewReader(options.Process.Input())
	ask := func(question, flag string) (string, error) {
		if !terminal {
			return "", fmt.Errorf("Missing %s: no terminal to ask for it, so give it as %s", flag, flag)
		}
		fmt.Fprint(cmd.OutOrStdout(), question)
		line, err := answers.ReadString('\n')
		if err != nil && !(err == io.EOF && len(line) > 0) {
			fmt.Fprintln(cmd.OutOrStdout())
			if err == io.EOF {
				return "", fmt.Errorf("Missing %s: the input ended before it was given", flag)
			}
			return "", fmt.Errorf("Missing %s: could not read it: %w", flag, err)
		}
		return strings.TrimSpace(line), nil
	}
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
		for {
			typed, err := ask(question+": ", "--workstation")
			if err != nil {
				return err
			}
			name = typed
			if name == "" {
				name = offered
			}
			if workstationName.MatchString(name) {
				break
			}
			if name == "" {
				fmt.Fprintln(cmd.OutOrStdout(), "A Workstation needs a name: use lowercase letters, digits and -, at most 63, not starting or ending with -.")
			} else {
				fmt.Fprintln(cmd.OutOrStdout(), name+" is not a Workstation name: use lowercase letters, digits and -, at most 63, not starting or ending with -.")
			}
		}
	}
	if !workstationName.MatchString(name) {
		return fmt.Errorf("--workstation %s is not a Workstation name: use lowercase letters, digits and -, at most 63, not starting or ending with -", name)
	}
	domain := f.baseDomain
	if !cmd.Flags().Changed("base-domain") {
		domain = env.get("YARD_BASE_DOMAIN")
		if terminal {
			for {
				typed, err := ask("Base Domain ["+env.get("YARD_BASE_DOMAIN")+"]: ", "--base-domain")
				if err != nil {
					return err
				}
				domain = typed
				if domain == "" {
					domain = env.get("YARD_BASE_DOMAIN")
				}
				if baseDomainName.MatchString(domain) {
					break
				}
				fmt.Fprintln(cmd.OutOrStdout(), domain+" is not a domain name such as lvh.me.")
			}
		}
	}
	if !baseDomainName.MatchString(domain) {
		return fmt.Errorf("--base-domain %s is not a domain name such as lvh.me", domain)
	}
	key := f.gossipKey
	if cmd.Flags().Changed("gossip-key") || key != "" {
		if err := checkInitGossipKey(key); err != nil {
			return err
		}
	} else {
		bytes := make([]byte, 32)
		if _, err := rand.Read(bytes); err != nil {
			return fmt.Errorf("could not make a Gossip Key: %w", err)
		}
		key = base64.StdEncoding.EncodeToString(bytes)
	}
	var peers []string
	for _, list := range f.peers {
		peers = append(peers, strings.FieldsFunc(list, func(r rune) bool { return r == ',' || unicode.IsSpace(r) })...)
	}
	checkedPeers, err := checkWSSetting("peers", strings.Join(peers, " "))
	if err != nil {
		return err
	}
	for _, found := range [][2]string{
		{"COMPOSE_PROFILES", "dashboard"},
		{"YARD_UID", options.Process.ID("-u")},
		{"YARD_GID", options.Process.ID("-g")},
		{"YARD_DOCKER_GID", dockerGroup(f.dockerSocket)},
	} {
		if err := env.fillIfEmpty(found[0], found[1]); err != nil {
			return err
		}
	}
	settings := [][2]string{{"YARD_WORKSTATION", name}, {"YARD_BASE_DOMAIN", domain}, {"YARD_GOSSIP_KEY", key}}
	settings = append(settings, [2]string{"YARD_PEERS", checkedPeers})
	for _, setting := range settings {
		if err := env.set(setting[0], setting[1]); err != nil {
			return err
		}
	}
	compose, err := templates.Compose(options.Version)
	if err != nil {
		return err
	}
	if err := writeWorkstationFiles(folder, compose, env.text(), f.force); err != nil {
		return err
	}
	if strings.TrimSpace(env.get("YARD_DOCKER_GID")) == "" {
		fmt.Fprintf(cmd.ErrOrStderr(), "Warning: no Docker socket at %s, so YARD_DOCKER_GID in .env is empty. Set it to the group of /var/run/docker.sock, or the dashboard will not start.\n", f.dockerSocket)
	}
	fmt.Fprintf(cmd.OutOrStdout(), "Wrote compose.yaml, .env to %s.\n\nThe Yard's Gossip Key: %s\nGive this key to every other Workstation: lvh-yard ws init --gossip-key <key>.\n\n", folder, key)
	printInitNextSteps(cmd.OutOrStdout(), folder, env)
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
	}{{"compose.yaml", compose, 0644}, {".env", env, 0600}}
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
	// Create this before Compose starts, or Docker creates it owned by root.
	if err := os.MkdirAll(filepath.Join(folder, "backups"), 0755); err != nil {
		return fmt.Errorf("could not create %s: %w", folder, err)
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
		if file.name == ".env" {
			if err := output.Chmod(0600); err != nil {
				_ = output.Close()
				return fmt.Errorf("could not protect %s: %w", path, err)
			}
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
	return nil
}

type envFile struct {
	lines []string
}

func parseEnvFile(text string) *envFile {
	return &envFile{lines: strings.Split(strings.TrimSuffix(strings.ReplaceAll(text, "\r\n", "\n"), "\n"), "\n")}
}

func (e *envFile) get(key string) string {
	for _, line := range e.lines {
		if value, ok := strings.CutPrefix(line, key+"="); ok {
			return value
		}
	}
	return ""
}

func (e *envFile) set(key, value string) error {
	for i, line := range e.lines {
		if strings.HasPrefix(strings.TrimPrefix(line, "#"), key+"=") {
			e.lines[i] = key + "=" + value
			return nil
		}
	}
	return fmt.Errorf(".env has no setting %s", key)
}

func (e *envFile) fillIfEmpty(key, found string) error {
	if strings.TrimSpace(e.get(key)) == "" && found != "" {
		return e.set(key, found)
	}
	return nil
}

func (e *envFile) text() string {
	return strings.Join(e.lines, "\n") + "\n"
}

func (e *envFile) valueOrDefault(key string) string {
	if value := e.get(key); strings.TrimSpace(value) != "" {
		return value
	}
	return envSetting(templates.Env(), key, "")
}

func printInitNextSteps(out io.Writer, folder string, env *envFile) {
	bind := env.valueOrDefault("YARD_DASHBOARD_BIND_ADDRESS")
	if bind == "0.0.0.0" {
		bind = "127.0.0.1"
	}
	fmt.Fprintf(out, "Next steps:\n  1. Open TCP port %s and TCP and UDP port %s to the other Workstations.\n  2. Start this Workstation: cd %s && docker compose up -d\n  3. Open the dashboard: http://%s:%s\n  4. Make sure that LVH Yard works: lvh-yard status\n\n", env.valueOrDefault("YARD_CONSUL_SERVER_PORT"), env.valueOrDefault("YARD_CONSUL_SERF_WAN_PORT"), folder, bind, env.valueOrDefault("YARD_DASHBOARD_PORT"))
	fmt.Fprintln(out, "To join a Yard later, run this with the address of one of its Workstations:\n  lvh-yard ws join --peers <ip> --gossip-key <key>")
}
