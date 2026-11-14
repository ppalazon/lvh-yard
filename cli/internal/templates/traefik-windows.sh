#!/bin/sh
# Generated, do not edit.
set -eu
umask 077

for bind in "${YARD_TRAEFIK_BIND_ADDRESS:-127.0.0.1}" "${YARD_DASHBOARD_BIND_ADDRESS:-127.0.0.1}"; do
  case "$bind" in
    127.0.0.1|0.0.0.0) ;;
    *) printf '%s\n' 'Windows listeners require 127.0.0.1 or 0.0.0.0.' >&2; exit 1 ;;
  esac
done

# NTFS does not forward every change to inotify. Watch only container-local files.
work=$(mktemp -d /run/lvh-yard-traefik.XXXXXX)
mkdir -p /etc/traefik/dynamic
child=
cleanup() {
  if [ -n "$child" ]; then
    kill "$child" 2>/dev/null || true
    wait "$child" 2>/dev/null || true
  fi
  rm -rf "$work"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

copy_sources() {
  mkdir -p "$1/dynamic" "$1/tls" &&
    cp -R /source/tls/. "$1/tls/" && cp -R /source/dynamic/. "$1/dynamic/"
}

snapshot() {
  rm -rf "$work/next" "$work/check"
  copy_sources "$work/next" || return 1
  sleep 1
  copy_sources "$work/check" || return 1
  # Accept a whole source generation only after two identical observations.
  diff -r "$work/next" "$work/check" >/dev/null || return 1
  rm -rf "$work/check"
}

publish_dynamic() {
  source=$1
  find /etc/traefik/dynamic -type f | while IFS= read -r file; do
    relative=${file#/etc/traefik/dynamic/}
    [ -f "$source/$relative" ] || rm -f "$file"
  done
  find /etc/traefik/dynamic -depth -type d | while IFS= read -r directory; do
    [ "$directory" != /etc/traefik/dynamic ] || continue
    relative=${directory#/etc/traefik/dynamic/}
    [ -d "$source/$relative" ] || rmdir "$directory"
  done
  find "$source" -type f | while IFS= read -r file; do
    relative=${file#"$source"/}
    target="/etc/traefik/dynamic/$relative"
    if ! cmp -s "$file" "$target"; then
      mkdir -p "$(dirname "$target")"
      # Temporary files stay outside the watched folder, on the same filesystem.
      cp "$file" "$work/publish"
      chmod 600 "$work/publish"
      mv -f "$work/publish" "$target"
    fi
  done
}

while ! snapshot; do sleep 1; done
mv "$work/next" "$work/generation-0"
ln -s "$work/generation-0" "$work/current"
# The complete certificate/key snapshot is visible before any provider event.
ln -s "$work/generation-0/tls" "$work/tls.next"
mv -Tf "$work/tls.next" /etc/traefik/tls
publish_dynamic "$work/current/dynamic"

dashboard=${YARD_DASHBOARD_BIND_ADDRESS:-127.0.0.1}
[ "$dashboard" != 0.0.0.0 ] || dashboard=127.0.0.1
if [ -n "${YARD_HTTPS_PORT:-}" ]; then
  set -- "$@" "--entryPoints.websecure.address=${YARD_TRAEFIK_BIND_ADDRESS:-127.0.0.1}:${YARD_HTTPS_PORT}"
fi
traefik "$@" \
  "--providers.http.endpoint=http://$dashboard:${YARD_DASHBOARD_PORT:-7780}/traefik/configuration" &
child=$!

generation=0
while kill -0 "$child" 2>/dev/null; do
  sleep 1
  if ! snapshot; then continue; fi
  if diff -r "$work/current" "$work/next" >/dev/null; then continue; fi
  generation=$((generation + 1))
  next="$work/generation-$generation"
  mv "$work/next" "$next"
  tls_changed=false
  if ! diff -r "$work/current/tls" "$next/tls" >/dev/null; then
    tls_changed=true
  fi
  # Switch the full TLS tree, including deletions, before changing or touching config.
  ln -s "$next/tls" "$work/tls.next"
  mv -Tf "$work/tls.next" /etc/traefik/tls
  publish_dynamic "$next/dynamic"
  if [ "$tls_changed" = true ]; then
    find /etc/traefik/dynamic -type f -exec touch {} \;
  fi
  # Keep the last generation while Traefik consumes the queued provider events.
  if [ -n "${previous:-}" ]; then rm -rf "$previous"; fi
  previous=$(readlink -f "$work/current")
  ln -s "$next" "$work/current.next"
  mv -Tf "$work/current.next" "$work/current"
done
wait "$child"
