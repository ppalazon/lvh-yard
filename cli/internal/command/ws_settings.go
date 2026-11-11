package command

import (
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"unicode"

	"github.com/spf13/cobra"
)

const renameWorkstationHint = "To change the Workstation name, export a Backup, run lvh-yard ws init under the new name, and import the Backup"

var wsSettings = map[string]string{
	"base-domain":            "YARD_BASE_DOMAIN",
	"peers":                  "YARD_PEERS",
	"bind-address":           "YARD_BIND_ADDRESS",
	"traefik-bind-address":   "YARD_TRAEFIK_BIND_ADDRESS",
	"dashboard-bind-address": "YARD_DASHBOARD_BIND_ADDRESS",
	"http-port":              "YARD_HTTP_PORT",
	"https-port":             "YARD_HTTPS_PORT",
	"traefik-admin-port":     "YARD_TRAEFIK_ADMIN_PORT",
	"dashboard-port":         "YARD_DASHBOARD_PORT",
	"consul-http-port":       "YARD_CONSUL_HTTP_PORT",
	"consul-dns-port":        "YARD_CONSUL_DNS_PORT",
	"consul-grpc-tls-port":   "YARD_CONSUL_GRPC_TLS_PORT",
	"consul-server-port":     "YARD_CONSUL_SERVER_PORT",
	"consul-serf-lan-port":   "YARD_CONSUL_SERF_LAN_PORT",
	"consul-serf-wan-port":   "YARD_CONSUL_SERF_WAN_PORT",
	"out-of-range":           "YARD_OUT_OF_RANGE",
	"stack-traces":           "YARD_STACK_TRACES",
	"traefik":                "YARD_TRAEFIK_ENABLED",
}

func newWSShow(folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "show", Short: "Prints this Workstation's settings with the Gossip Key hidden.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			for _, line := range env.lines {
				key, value, ok := strings.Cut(line, "=")
				if !ok || strings.HasPrefix(key, "#") {
					continue
				}
				if key == "YARD_GOSSIP_KEY" {
					value = "[hidden]"
				}
				fmt.Fprintf(cmd.OutOrStdout(), "%s=%s\n", key, value)
			}
			return nil
		},
	}
}

func newWSSet(folder func() string) *cobra.Command {
	return &cobra.Command{
		Use: "set <name> <value>", Short: "Changes one Workstation setting without restarting services.", Args: cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			name, value := args[0], args[1]
			if name == "workstation" || name == "workstation-name" || name == "YARD_WORKSTATION" {
				return fmt.Errorf("%s", renameWorkstationHint)
			}
			key, ok := wsSettings[name]
			if !ok {
				return fmt.Errorf("unknown setting %q: use base-domain, peers, bind-address, traefik-bind-address, dashboard-bind-address, http-port, https-port, traefik-admin-port, dashboard-port, consul-http-port, consul-dns-port, consul-grpc-tls-port, consul-server-port, consul-serf-lan-port, consul-serf-wan-port, out-of-range, stack-traces or traefik", name)
			}
			checked, err := checkWSSetting(name, value)
			if err != nil {
				return err
			}
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if err := env.set(key, checked); err != nil {
				return err
			}
			if name == "traefik" {
				profiles := strings.FieldsFunc(strings.Trim(env.get("COMPOSE_PROFILES"), "\"'"), func(r rune) bool { return r == ',' || unicode.IsSpace(r) })
				kept := make([]string, 0, len(profiles)+1)
				for _, profile := range profiles {
					if profile != "traefik" {
						kept = append(kept, profile)
					}
				}
				if checked == "true" {
					kept = append(kept, "traefik")
				}
				if err := env.set("COMPOSE_PROFILES", strings.Join(kept, ",")); err != nil {
					return err
				}
			}
			if err := saveWSEnv(cmd, folder(), env); err != nil {
				return err
			}
			if name == "traefik" && checked == "false" {
				fmt.Fprintln(cmd.OutOrStdout(), "If this Workstation has traefik-admin, run lvh-yard remove traefik-admin.")
			}
			return nil
		},
	}
}

func newWSJoin(folder func() string) *cobra.Command {
	var key, workstation string
	var peers []string
	cmd := &cobra.Command{
		Use: "join", Short: "Changes the Yard's Gossip Key and Peers without restarting services.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if cmd.Flags().Changed("workstation") {
				return fmt.Errorf("%s", renameWorkstationHint)
			}
			if err := checkInitGossipKey(key); err != nil {
				return err
			}
			var checked string
			var err error
			if cmd.Flags().Changed("peers") {
				checked, err = checkWSSetting("peers", strings.Join(peers, " "))
				if err != nil {
					return err
				}
			}
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			if err := env.set("YARD_GOSSIP_KEY", key); err != nil {
				return err
			}
			if cmd.Flags().Changed("peers") {
				if err := env.set("YARD_PEERS", checked); err != nil {
					return err
				}
			}
			return saveWSEnv(cmd, folder(), env)
		},
	}
	cmd.Flags().StringVar(&key, "gossip-key", "", "Uses the Yard's shared Gossip Key.")
	cmd.Flags().StringArrayVar(&peers, "peers", nil, "Sets Peer addresses separated by spaces or commas.")
	cmd.Flags().StringVar(&workstation, "workstation", "", "Refuses a change of the Workstation name.")
	_ = cmd.Flags().MarkHidden("workstation")
	return cmd
}

func checkWSSetting(name, value string) (string, error) {
	valid := false
	rule := ""
	switch {
	case name == "traefik":
		valid, rule = value == "on" || value == "off", "on or off"
		if valid {
			value = strconv.FormatBool(value == "on")
		}
	case name == "base-domain":
		valid, rule = baseDomainName.MatchString(value), "a domain name such as lvh.me"
	case name == "peers":
		peers := strings.FieldsFunc(value, func(r rune) bool { return r == ',' || unicode.IsSpace(r) })
		valid, rule = true, "IPv4 addresses with an optional port from 1 to 65535, separated by spaces or commas"
		for _, peer := range peers {
			host := peer
			if strings.Contains(peer, ":") {
				var port string
				var err error
				host, port, err = net.SplitHostPort(peer)
				n, portErr := strconv.Atoi(port)
				if err != nil || portErr != nil || n < 1 || n > 65535 {
					valid = false
				}
			}
			if ip := net.ParseIP(host); ip == nil || ip.To4() == nil {
				valid = false
			}
		}
		value = strings.Join(peers, " ")
	case strings.HasSuffix(name, "bind-address"):
		ip := net.ParseIP(value)
		valid, rule = ip != nil && ip.To4() != nil, "an IPv4 address such as 127.0.0.1"
		if name == "bind-address" && value == "" {
			valid = true
		}
	case strings.HasSuffix(name, "-port"):
		n, err := strconv.Atoi(value)
		valid, rule = err == nil && n >= 1 && n <= 65535, "a port from 1 to 65535"
		if name == "https-port" && value == "" { valid = true }
	case name == "out-of-range":
		valid, rule = true, "CIDR ranges separated by commas, or an empty value"
		if value != "" {
			for _, cidr := range strings.Split(value, ",") {
				if _, _, err := net.ParseCIDR(strings.TrimSpace(cidr)); err != nil {
					valid = false
				}
			}
		}
	case name == "stack-traces":
		valid, rule = value == "true" || value == "false", "true or false"
	}
	if !valid {
		return "", fmt.Errorf("invalid %s: use %s", name, rule)
	}
	return value, nil
}

func readWSEnv(folder string) (*envFile, error) {
	path := filepath.Join(folder, ".env")
	text, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("could not read %s: %w", path, err)
	}
	return parseEnvFile(string(text)), nil
}

func saveWSEnv(cmd *cobra.Command, folder string, env *envFile) error {
	if err := protectWorkstationFolder(folder); err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(folder, ".env"), []byte(env.text()), 0600); err != nil {
		return err
	}
	fmt.Fprintln(cmd.OutOrStdout(), "Saved .env. Apply the settings:")
	quotedFolder := "'" + strings.ReplaceAll(folder, "'", "'\"'\"'") + "'"
	if strings.Trim(env.get("YARD_TRAEFIK_ENABLED"), "\"'") == "false" {
		fmt.Fprintf(cmd.OutOrStdout(), "  docker compose --project-directory %s --profile traefik stop traefik\n", quotedFolder)
	}
	fmt.Fprintf(cmd.OutOrStdout(), "  docker compose --project-directory %s up -d --force-recreate\n", quotedFolder)
	return nil
}
