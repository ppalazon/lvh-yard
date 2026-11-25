#!/bin/sh
# Builds release archives from the repository root without C code.
set -eu
version=${1:?Usage: sh dev/package-cli.sh VERSION}
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root/cli"
export CGO_ENABLED=0
for platform in linux-amd64 linux-arm64 macos-arm64 windows-amd64; do
  os=${platform%-*}
  arch=${platform#*-}
  executable=lvh-yard
  if [ "$os" = macos ]; then os=darwin; fi
  if [ "$os" = windows ]; then executable=lvh-yard.exe; fi
  mkdir -p "target/$platform"
  GOOS="$os" GOARCH="$arch" go build -trimpath \
    -ldflags="-s -w -X main.version=$version -X main.installMethod=standalone" \
    -o "target/$platform/$executable" ./cmd/lvh-yard
  archive="lvh-yard-$version-$platform"
  if [ "$os" = windows ]; then
    cp "$root/LICENSE" "target/$platform/LICENSE"
    # Recreate the ZIP so a rerun cannot retain obsolete members.
    rm -f "target/$archive.zip"
    (cd "target/$platform" && zip -q -X "../$archive.zip" "$executable" LICENSE)
  else
    tar -czf "target/$archive.tar.gz" \
      -C "target/$platform" "$executable" -C "$root" LICENSE
  fi
done
test "$(target/linux-amd64/lvh-yard --version)" = "lvh-yard $version"
