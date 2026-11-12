package command

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"text/tabwriter"
	"time"

	"github.com/spf13/cobra"
)

// serfAlive is Serf's code for a member that answers: 1 alive, 2 leaving, 3 left, 4 failed.
const serfAlive = 1

const defaultSerfWANPort = "8302"

type wanMember struct {
	Name   string            `json:"Name"`
	Addr   string            `json:"Addr"`
	Port   int               `json:"Port"`
	Status int               `json:"Status"`
	Tags   map[string]string `json:"Tags"`
}

func newWSYardPeers(folder func() string) *cobra.Command {
	var sync bool
	cmd := &cobra.Command{
		Use: "peers", Short: "Lists the Workstations that Consul sees in the Yard.", Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			env, err := readWSEnv(folder())
			if err != nil {
				return err
			}
			self := strings.Trim(env.get("YARD_WORKSTATION"), "\"'")
			members, err := readWANMembers(env.valueOrDefault("YARD_CONSUL_HTTP_PORT"))
			if err != nil {
				return err
			}
			var peers []wanMember
			skipped := []string{}
			for _, member := range members {
				datacenter := member.Tags["dc"]
				if datacenter == self {
					continue
				}
				ip := net.ParseIP(member.Addr)
				if member.Status != serfAlive || ip == nil || ip.To4() == nil {
					skipped = append(skipped, datacenter)
					continue
				}
				peers = append(peers, member)
			}
			sort.Slice(peers, func(i, j int) bool { return peers[i].Tags["dc"] < peers[j].Tags["dc"] })
			out := cmd.OutOrStdout()
			table := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
			fmt.Fprintln(table, "WORKSTATION\tPEER")
			addresses := make([]string, 0, len(peers))
			for _, peer := range peers {
				address := peerAddress(peer)
				addresses = append(addresses, address)
				fmt.Fprintf(table, "%s\t%s\n", peer.Tags["dc"], address)
			}
			if err := table.Flush(); err != nil {
				return err
			}
			if len(skipped) > 0 {
				sort.Strings(skipped)
				fmt.Fprintf(out, "Left out, as they do not answer: %s\n", strings.Join(skipped, ", "))
			}
			if !sync {
				return nil
			}
			if len(addresses) == 0 {
				return fmt.Errorf("no other Workstation answers, so YARD_PEERS stays as it is")
			}
			value, err := checkWSSetting("peers", strings.Join(addresses, " "))
			if err != nil {
				return err
			}
			if strings.Join(strings.FieldsFunc(env.get("YARD_PEERS"), func(r rune) bool { return r == ',' || r == ' ' }), " ") == value {
				fmt.Fprintln(out, "YARD_PEERS is already up to date.")
				return nil
			}
			if err := env.set("YARD_PEERS", value); err != nil {
				return err
			}
			return saveWSEnv(cmd, folder(), env)
		},
	}
	cmd.Flags().BoolVar(&sync, "sync", false, "Sets YARD_PEERS to the Workstations that answer.")
	return cmd
}

// peerAddress leaves out the port when it is Consul's default.
func peerAddress(member wanMember) string {
	port := strconv.Itoa(member.Port)
	if port == defaultSerfWANPort {
		return member.Addr
	}
	return net.JoinHostPort(member.Addr, port)
}

func readWANMembers(port string) ([]wanMember, error) {
	address := "http://127.0.0.1:" + port
	response, err := (&http.Client{Timeout: 5 * time.Second}).Get(address + "/v1/agent/members?wan=1")
	if err != nil {
		return nil, fmt.Errorf("cannot reach Consul at %s: start this Workstation with lvh-yard ws compose start", address)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("Consul at %s answered with HTTP status %d", address, response.StatusCode)
	}
	var members []wanMember
	if err := json.NewDecoder(response.Body).Decode(&members); err != nil {
		return nil, fmt.Errorf("Consul at %s sent an answer that is not a member list: %w", address, err)
	}
	return members, nil
}
