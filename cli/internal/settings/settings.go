// Package settings knows the settings of a Workstation: the name of each in the .env file, and
// what each accepts. It reads and writes no file.
package settings

import (
	"fmt"
	"net"
	"regexp"
	"strconv"
	"strings"
	"unicode"
)

var workstationName = regexp.MustCompile(`^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`)

var baseDomainName = regexp.MustCompile(`^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$`)

// envKeys maps the name that a person types to the key of the .env file.
var envKeys = map[string]string{
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

// EnvKey is the key of the .env file for a setting name.
func EnvKey(name string) (string, bool) {
	key, ok := envKeys[name]
	return key, ok
}

func ValidWorkstationName(name string) bool {
	return workstationName.MatchString(name)
}

func ValidBaseDomain(domain string) bool {
	return baseDomainName.MatchString(domain)
}

// A rule says what a setting accepts, for the error message, and checks a value. It gives the value
// as the .env file keeps it.
type rule struct {
	accepts string
	check   func(value string) (string, bool)
}

var rules = buildRules()

func buildRules() map[string]rule {
	rules := map[string]rule{
		"traefik": {"on or off", func(value string) (string, bool) {
			return strconv.FormatBool(value == "on"), value == "on" || value == "off"
		}},
		"base-domain": {"a domain name such as lvh.me", func(value string) (string, bool) {
			return value, ValidBaseDomain(value)
		}},
		"peers": {"IPv4 addresses with an optional port from 1 to 65535, separated by spaces or commas", checkPeers},
		"out-of-range": {"CIDR ranges separated by commas, or an empty value", func(value string) (string, bool) {
			if value == "" {
				return value, true
			}
			for cidr := range strings.SplitSeq(value, ",") {
				if _, _, err := net.ParseCIDR(strings.TrimSpace(cidr)); err != nil {
					return value, false
				}
			}
			return value, true
		}},
		"stack-traces": {"true or false", func(value string) (string, bool) {
			return value, value == "true" || value == "false"
		}},
	}
	for name := range envKeys {
		switch {
		case strings.HasSuffix(name, "bind-address"):
			rules[name] = rule{"an IPv4 address such as 127.0.0.1", bindAddressChecker(name == "bind-address")}
		case strings.HasSuffix(name, "-port"):
			rules[name] = rule{"a port from 1 to 65535", portChecker(name == "https-port")}
		}
	}
	return rules
}

func bindAddressChecker(mayBeEmpty bool) func(string) (string, bool) {
	return func(value string) (string, bool) {
		ip := net.ParseIP(value)
		return value, (ip != nil && ip.To4() != nil) || (mayBeEmpty && value == "")
	}
}

func portChecker(mayBeEmpty bool) func(string) (string, bool) {
	return func(value string) (string, bool) {
		n, err := strconv.Atoi(value)
		return value, (err == nil && n >= 1 && n <= 65535) || (mayBeEmpty && value == "")
	}
}

// checkPeers takes IPv4 addresses with an optional port, and gives them separated by one space.
func checkPeers(value string) (string, bool) {
	peers := strings.FieldsFunc(value, func(r rune) bool { return r == ',' || unicode.IsSpace(r) })
	valid := true
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
	return strings.Join(peers, " "), valid
}

// Check gives the value of a setting as the .env file keeps it, or says what the setting accepts.
func Check(name, value string) (string, error) {
	rule := rules[name]
	checked, valid := value, false
	if rule.check != nil {
		checked, valid = rule.check(value)
	}
	if !valid {
		return "", fmt.Errorf("invalid %s: use %s", name, rule.accepts)
	}
	return checked, nil
}
