---
title: "A Backup is one YAML file per Workstation, and Route Profiles move from a file into Consul KV so a Backup can hold them"
author: Pablo
date: 2026-11-05
branch: main
cost: { minutes: 5, tokens: 15192 }
id: l-b00ec441
scope: repo
status: active
src: agent
touches:
  - GLOSSARY.md
  - docs/adr/0004-route-profiles-travel-through-consul-kv.md
  - compose.yaml
---

## Decided
- A Backup holds Publications (Scope, Instance Priority, Route Profile names), Static Instances (with check, tags, meta), Container Bindings, Aliases and Route Profiles. Never Bound Instances, Health or Remote Services.
- The dashboard parses, checks and merges a Backup. The CLI only sends or receives the YAML. The import is all or nothing, with --dry-run and a plan.
- Import modes: add (default) skips existing things, --update overwrites them. No --replace. The dashboard writes a safety Backup before every import that changes something.
- Matching: Service by name, Static Instance by host and port inside its Service, Container Binding by its ID, Alias and Route Profile by name. publication: none unpublishes in --update; a missing publication key changes nothing.
- A Static Instance whose host is the exporting Workstation's LAN address is written without host, so it gets the importing Workstation's LAN address.
- Route Profiles live only in Consul KV. route-profiles.yaml, its polling, last good copy and stored errors go away. You edit profiles from a dashboard page (text box YAML editor), the CLI (profiles, show, set, edit, remove) or a Backup import. Removing a profile that a Publication names is refused. No automatic migration of the old file.

## Tried and ruled out
- Keeping route-profiles.yaml as the source and letting an import write it: the dashboard sees the Workstation Folder read-only, a rewrite loses comments, and hand edits race with imports.
- File plus KV as two sources of Route Profiles: name clashes and two origins to show.

## Watch out
- The Backups folder needs its own read-write mount, because the Workstation Folder is mounted read-only. Backup files hold basic-auth hashes, so they are written with mode 0600.

In the code:
- docs/adr/0004-route-profiles-travel-through-consul-kv.md rewritten for the KV-only decision.
- Backups folder: <Workstation Folder>/backups/, written into compose.yaml by lvh-yard init and reinit.
- CLI: lvh-yard export [-o file], lvh-yard import <file|-> [--update] [--dry-run].
