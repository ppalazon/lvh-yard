#!/usr/bin/env bash
set -euo pipefail

action=$1
cli=$2
root=$3

for workstation in ws-a ws-b; do
    folder="$root/dev/$workstation"
    case "$action" in
        up)
            if [[ ! -f "$folder/.env" ]]; then
                domain=lvh.me
                offset=0
                peer=18312
                if [[ "$workstation" == ws-b ]]; then
                    domain=localhost
                    offset=10
                    peer=18302
                fi
                # This key only joins the test Workstations on loopback.
                "$cli" ws --dir "$folder" init --workstation "$workstation" --base-domain "$domain" \
                    --gossip-key 'eUs7jAdJHAfhNlzlLnxMhxI9isP2SUDkT+2V/vJiIVY=' --peers "127.0.0.1:$peer"
                for setting in bind-address traefik-bind-address dashboard-bind-address; do
                    "$cli" ws --dir "$folder" set "$setting" 127.0.0.1
                done
                # Both Workstations share loopback, so their loopback Instances are in range.
                "$cli" ws --dir "$folder" set out-of-range '172.17.0.0/16,172.18.0.0/15,172.20.0.0/14,172.24.0.0/13'
                for setting_port in http-port:10080 traefik-admin-port:18080 dashboard-port:17780 \
                    consul-http-port:18500 consul-dns-port:18600 consul-grpc-tls-port:18503 \
                    consul-server-port:18300 consul-serf-lan-port:18301 consul-serf-wan-port:18302; do
                    "$cli" ws --dir "$folder" set "${setting_port%:*}" "$((${setting_port#*:} + offset))"
                done
            fi
            # The dashboard runs from sources with dev:dashboard.
            COMPOSE_PROFILES=traefik "$cli" ws --dir "$folder" compose start
            ;;
        down)
            "$cli" ws --dir "$folder" compose stop
            ;;
        *)
            echo "Use up or down." >&2
            exit 1
            ;;
    esac
done
