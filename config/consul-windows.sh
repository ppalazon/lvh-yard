#!/bin/sh
# Starts Consul. compose.yaml gives the flags that Compose fills in, such as the ports, as "$@".
set -e
# Desktop forwards only wildcard and loopback listeners to the Windows LAN. A bind to the VM's own address
# would never be reachable. Consul listens on all addresses and advertises the LAN address.
# The Peers are a list, separated by spaces or commas, and -retry-join-wan takes one address.
# -f: a Peer such as [fd00::1]:8302 is not a file pattern.
set -f
for peer in $(printf '%s' "${YARD_PEERS:-}" | tr ',' ' '); do
  set -- "$@" "-retry-join-wan=$peer"
done
exec consul agent \
  -datacenter="${YARD_WORKSTATION}" -node="${YARD_WORKSTATION}" \
  -encrypt="${YARD_GOSSIP_KEY}" \
  -bind=0.0.0.0 \
  -advertise="${YARD_BIND_ADDRESS}" -advertise-wan="${YARD_BIND_ADDRESS}" \
  "$@"
