---
title: Code is formatted by a pre-commit hook with Google Java Format and Prettier
author: Pablo
date: 2026-11-02
branch: main
cost: { minutes: 7, tokens: 67543 }
id: l-cbcaa6a2
scope: repo
status: active
src: agent
touches:
  - ".pre-commit-config.yaml"
  - mise.toml
  - dashboard/openapi.yaml
  - package-lock.json
---

## Decided
- Files are formatted before each commit by prek, a fast runner for pre-commit hooks, pinned in mise.
- Java uses Google Java Format 2.0.0. The user chose Google style over pinning Java 25 for Palantir.
- YAML, JSON, Markdown, JavaScript and CSS use Prettier.

## Tried and ruled out
- Palantir Java Format (2.83 and 2.103) and Google Java Format 1.33 crash on the system Java 27 with "NoSuchFieldError ... EndPosTable endPositions". Palantir works on Java 25, but the user did not want to pin Java 25.
- The TOML formatter sorts every key and table, which scrambles the task order in mise.toml. Only the TOML syntax check stays.

## Watch out
- dashboard/openapi.yaml must stay out of every formatter: OpenApiTest compares it byte for byte with what the dashboard serves.
- .trail/ and package-lock.json are written by tools, so they are excluded too.

In the code: .pre-commit-config.yaml; mise.toml tasks `hooks` (prek install) and `fmt` (prek run --all-files).
