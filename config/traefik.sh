#!/bin/sh
# Starts Traefik. compose.yaml gives the fixed flags as "$@". This script adds the flags that depend on .env.
# A dashboard on the wildcard 0.0.0.0 is reached through loopback.
dashboard="${YARD_DASHBOARD_BIND_ADDRESS:-127.0.0.1}"
[ "$dashboard" != 0.0.0.0 ] || dashboard=127.0.0.1
if [ -n "${YARD_HTTPS_PORT:-}" ]; then
  set -- "$@" "--entryPoints.websecure.address=${YARD_TRAEFIK_BIND_ADDRESS:-127.0.0.1}:${YARD_HTTPS_PORT}"
fi
exec traefik "$@" \
  "--providers.http.endpoint=http://$dashboard:${YARD_DASHBOARD_PORT:-7780}/traefik/configuration"
