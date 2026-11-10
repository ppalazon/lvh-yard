---
title: The CLI reads and writes Route Profiles as Traefik YAML
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-5b127d32
scope: repo
status: active
replaces: l-10fb5b4c
src: agent
touches:
  - cli/internal/command/profile_file.go
  - cli/internal/client/profiles.go
---

## Decided
- profile_file.go turns http.middlewares.<name> YAML into the set body of PUT /api/workstation/route-profiles, and back for profiles show. It reads and writes with go.yaml.in/yaml/v3.
- The reader accepts only one document with http.middlewares and nothing else at the top. It rejects duplicate keys, because a duplicate must not silently replace a profile.
- client.problemReason prints each reason of a problem answer's problems member on a line of its own, else the detail. So a refused set shows one line per profile.

## Watch out
- A YAML writer that does not quote the string "100" writes it as 100, so set would send a number back. The encoder quotes strings such as 100, true and null. TestProfilesShowSetRoundTrip covers the round trip of show and set.
