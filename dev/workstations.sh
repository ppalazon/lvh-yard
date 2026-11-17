#!/usr/bin/env bash
set -euo pipefail

action=$1
cli=$2
root=$3

init_workstation() {
    local workstation=$1
    local folder="$root/dev/$workstation"
    local domain=lvh.me
    local offset=0
    if [[ "$workstation" == ws-b ]]; then
        domain=localhost
        offset=10
    fi
    "$cli" ws --dir "$folder" init --workstation "$workstation" --base-domain "$domain"
    for setting in traefik-bind-address dashboard-bind-address; do
        "$cli" ws --dir "$folder" set "$setting" 127.0.0.1
    done
    # Both Workstations share loopback, so their loopback Instances are in range.
    "$cli" ws --dir "$folder" set out-of-range '172.17.0.0/16,172.18.0.0/15,172.20.0.0/14,172.24.0.0/13'
    for setting_port in http-port:10080 traefik-admin-port:18080 dashboard-port:17780 \
        consul-http-port:18500 consul-dns-port:18600 consul-grpc-tls-port:18503 \
        consul-server-port:18300 consul-serf-lan-port:18301 consul-serf-wan-port:18302; do
        "$cli" ws --dir "$folder" set "${setting_port%:*}" "$((${setting_port#*:} + offset))"
    done
}

# ws-a shares its Yard with ws-b over loopback. The password only protects the zip file for a moment.
join_workstations() {
    local invitation
    invitation="$(mktemp -d)/ws-b-yard.zip"
    "$cli" ws --dir "$root/dev/ws-a" tls ca create
    "$cli" ws --dir "$root/dev/ws-a" yard init --address 127.0.0.1
    "$cli" ws --dir "$root/dev/ws-a" yard share ws-b --password lvh-yard-dev --output "$invitation"
    "$cli" ws --dir "$root/dev/ws-b" yard join "$invitation" --address 127.0.0.1 --password lvh-yard-dev
    rm -rf "$(dirname "$invitation")"
}

case "$action" in
    up)
        for workstation in ws-a ws-b; do
            if [[ ! -f "$root/dev/$workstation/.env" ]]; then
                init_workstation "$workstation"
                fresh=1
            fi
        done
        if [[ "${fresh:-}" == 1 ]]; then
            join_workstations
        fi
        for workstation in ws-a ws-b; do
            # The dashboard runs from sources with dev:dashboard.
            COMPOSE_PROFILES=traefik "$cli" ws --dir "$root/dev/$workstation" compose start
        done
        ;;
    down)
        for workstation in ws-a ws-b; do
            "$cli" ws --dir "$root/dev/$workstation" compose stop
        done
        ;;
    *)
        echo "Use up or down." >&2
        exit 1
        ;;
esac
