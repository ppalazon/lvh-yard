# 16: Release job for Go

Status: ready-for-agent

Blocked by: 02

Spec: `../spec.md`.

## What to build

The Release workflow gets one job on an ordinary runner that builds the Go `lvh-yard` for `linux-amd64`, `linux-arm64` and `macos-arm64`, with no C code and no GraalVM. Each executable gets the tag as its version, is tested, and is packed as `lvh-yard-<version>-<platform>.tar.gz` with the licence, as the old job does. The new job runs next to the old `cli-native` job, so the old release is not broken before the cutover. It uploads its archives under a name that does not clash with the old ones.

## Acceptance criteria

- [ ] A run on a test tag, on a fork, makes three archives, and each has a working `lvh-yard --version` that prints the tag.
- [ ] The old `cli-native` job still works.
- [ ] The job needs no GraalVM and no `ubuntu-22.04`.
