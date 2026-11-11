#!/bin/sh
# Installs the lvh-yard executable from a GitHub release.
#
#   curl -fsSL https://lvhy.cc/install.sh | sh
#
# Settings, as environment variables:
#   LVH_YARD_VERSION      A release such as 0.1.0 or v0.1.0. The default is the latest release.
#   LVH_YARD_INSTALL_DIR  The folder for lvh-yard. The default is ~/.local/bin, or /usr/local/bin for root.
set -eu

REPO="ppalazon/lvh-yard"

say() { printf '%s\n' "$*"; }
fail() {
  printf 'install.sh: %s\n' "$*" >&2
  exit 1
}
have() { command -v "$1" >/dev/null 2>&1; }

# Prints the file at a URL on stdout.
fetch() {
  if have curl; then
    curl -fsSL "$1"
  elif have wget; then
    wget -qO- "$1"
  else
    fail "curl or wget is required."
  fi
}

# Writes the file at a URL to a path.
download() {
  if have curl; then
    curl -fsSL -o "$2" "$1"
  else
    wget -qO "$2" "$1"
  fi
}

platform() {
  os=$(uname -s)
  arch=$(uname -m)
  case "$os/$arch" in
  Linux/x86_64 | Linux/amd64) echo linux-amd64 ;;
  Linux/aarch64 | Linux/arm64) echo linux-arm64 ;;
  Darwin/arm64) echo macos-arm64 ;;
  *) fail "no lvh-yard release for $os $arch. Releases cover Linux (amd64, arm64) and macOS (arm64)." ;;
  esac
}

# Checks the archive against its line of SHA256SUMS.
verify() {
  if have sha256sum; then
    (cd "$1" && sha256sum -c "$2") >/dev/null
  elif have shasum; then
    (cd "$1" && shasum -a 256 -c "$2") >/dev/null
  else
    fail "sha256sum or shasum is required to check the download."
  fi
}

main() {
  have tar || fail "tar is required."

  target=$(platform)

  if [ -n "${LVH_YARD_VERSION:-}" ]; then
    base="https://github.com/$REPO/releases/download/v${LVH_YARD_VERSION#v}"
  else
    base="https://github.com/$REPO/releases/latest/download"
  fi

  if [ -n "${LVH_YARD_INSTALL_DIR:-}" ]; then
    dir="$LVH_YARD_INSTALL_DIR"
  elif [ "$(id -u)" -eq 0 ]; then
    dir=/usr/local/bin
  else
    dir="$HOME/.local/bin"
  fi

  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' EXIT INT TERM

  # SHA256SUMS names the archive of each platform, so it also tells the version.
  fetch "$base/SHA256SUMS" >"$tmp/SHA256SUMS" || fail "cannot download $base/SHA256SUMS. Check the version."
  line=$(grep -E " lvh-yard-.*-$target\.tar\.gz\$" "$tmp/SHA256SUMS" || true)
  [ -n "$line" ] || fail "this release has no archive for $target."
  archive=${line##* }
  version=${archive#lvh-yard-}
  version=${version%-"$target".tar.gz}

  say "Installing lvh-yard $version for $target."
  download "$base/$archive" "$tmp/$archive" || fail "cannot download $base/$archive."
  printf '%s\n' "$line" >"$tmp/archive.sha256"
  verify "$tmp" archive.sha256 || fail "the checksum of $archive does not match."

  tar -xzf "$tmp/$archive" -C "$tmp" lvh-yard

  mkdir -p "$dir" 2>/dev/null || true
  [ -w "$dir" ] || fail "cannot write to $dir. Set LVH_YARD_INSTALL_DIR to a folder you own, or run this script with sudo."
  # A new file, then mv: a running lvh-yard cannot be overwritten in place ("Text file busy").
  cp "$tmp/lvh-yard" "$dir/.lvh-yard.new"
  chmod 755 "$dir/.lvh-yard.new"
  mv -f "$dir/.lvh-yard.new" "$dir/lvh-yard"

  say "Installed $dir/lvh-yard"
  case ":$PATH:" in
  *":$dir:"*) ;;
  *)
    say ""
    say "$dir is not on your PATH. Add it, for example in ~/.profile:"
    say "  export PATH=\"$dir:\$PATH\""
    ;;
  esac
}

# The whole script is read before main runs, so a cut download runs nothing.
main "$@"
