# 16: Release job for Go

Status: done

Blocked by: 02

Spec: `../spec.md`.

## What to build

The Release workflow gets one job on an ordinary runner that builds the Go `lvh-yard` for `linux-amd64`, `linux-arm64` and `macos-arm64`, with no C code and no GraalVM. Each executable gets the tag as its version, is tested, and is packed as `lvh-yard-<version>-<platform>.tar.gz` with the licence, as the old job does. The new job runs next to the old `cli-native` job, so the old release is not broken before the cutover. It uploads its archives under a name that does not clash with the old ones.

## Acceptance criteria

- [ ] A run on a test tag, on a fork, makes three archives, and each has a working `lvh-yard --version` that prints the tag.
- [ ] The old `cli-native` job still works.
- [x] The job needs no GraalVM and no `ubuntu-22.04`.

## Comments

- Implemented `cli-go` on `ubuntu-latest` with `CGO_ENABLED=0`, stripped
  builds and `main.version` set from the tag without its leading `v`.
  It runs the Go tests and checks the Linux amd64 executable's version.
- The `go-cli-archives` artifact uses
  `lvh-yard-go-<version>-<platform>.tar.gz` filenames so neither artifact
  names nor archive filenames clash with the native job. Each tar holds
  `lvh-yard` and `LICENSE`. The native job and release dependency stay
  unchanged.
- Local verification passed: actionlint and the workflow's exact packaging
  step. The Linux amd64 executable and Linux arm64 executable through QEMU
  printed `lvh-yard 0.0.0-go-cli-check`. The macOS file is an arm64 Mach-O
  executable. Each archive holds `lvh-yard` and `LICENSE`.
- The fork test-tag criterion remains unverified: no remote is configured,
  the configured repository returns 404, and the requested fork URL was
  left blank. The old native workflow was not run.
