#!/bin/sh
# Starts the dashboard.
set -e
# Desktop cannot bind a native Windows interface. Reject it even with direct Compose.
for bind in "${YARD_DASHBOARD_BIND_ADDRESS}" "${YARD_TRAEFIK_BIND_ADDRESS}"; do
  case "$bind" in
    127.0.0.1|0.0.0.0) ;;
    *) printf '%s\n' 'Windows listeners require 127.0.0.1 or 0.0.0.0.' >&2; exit 1 ;;
  esac
done
# Desktop host networking needs IPv4-only Java sockets to reach IPv4 loopback.
exec java -Djava.net.preferIPv4Stack=true -jar /app/dashboard.jar
