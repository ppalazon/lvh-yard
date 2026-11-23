# 25: Recipe 15: Move or copy a setup with a Backup

**What to build:** Write the recipe "Recipe 15: Move or copy a setup with a Backup" with the recipe template of the spec.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## Pain

"I got a new laptop, or I want my teammate to have my setup."

## Result

You export a Backup, import it on another Workstation, and check the plan before it writes.

## Commands to use

- `lvh-yard export -o ws-a.yaml`, `lvh-yard import --dry-run ws-a.yaml`, `lvh-yard import ws-a.yaml`, `lvh-yard import --update ws-a.yaml`.

## Diagram idea

A flow diagram of what a Backup holds and what it never holds (Bound Instances, Health, Remote Services).

## Verification

`mise run dev:up`. Warn first that a Backup holds the basic-auth hashes of your Route Profiles. Read the user guide section "Backups".

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- The page is `docs/guide/recipes/move/backup.md`, titled "Move or copy a setup with a Backup" as in the outline. It is in `draft_docs`, and `zensical.toml` has a new navigation section "Move a setup". The stage 1 text is in `drafts/25-backup.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading. The diagram is a flowchart of the move with what a Backup holds and never holds. The warning about the basic-auth hashes comes before the first command.
- Tested on throwaway Workstations in `/tmp` with other ports than the defaults, one after the other: `ws-a` (the old one) with the Route Profile `team-login` (Apache `apr1` hash of a test password), the Service `web` (Static Instance `127.0.0.1:4000`, a Python server), `mailpit` (Container Binding to `mailpit-1`) and an empty `notes`. After `export -o`, I removed `ws-a`, made `ws-b` with the same ports (the move to a new laptop, with default ports on both), and imported the file. The outputs on the page are real. The page shortens the plan with `...` and leaves out its first line, which has the date of the Backup.
  - `export -o` printed `Wrote the Backup to <path>.` and wrote a file with mode 600. `import --dry-run` printed 21 lines and `Dry run: nothing changed.` `import` printed the same plan, `Added 8, skipped 13.` and the safety Backup, with the files in `backups/`. A second `import` said `Nothing changed: skipped 24.`
  - Check: `catalog` listed the Services as the page shows. `web` answered `401 Unauthorized` without a password and `200 OK` with the password of the test, so the hash moved.
  - `--update`: an `import` in add mode skipped `Publication of web: It exists already.` after `publish web --no-profiles`, and `import --update` printed `update Publication of web` and `Added 0, updated 1, skipped 23.` Importing the safety Backup of that import with `--update` gave `update Publication of web`, so the safety Backup restores overwritten values.
  - Refused files: a port of 70000 and an unknown Route Profile each printed the line on the page and exited with 1, with no change. `lvh-yard export | lvh-yard import --dry-run -` worked (the standard input form).
  - Other ports: a third Workstation with other ports got `Added 11, skipped 10`, and `yard`, `consul-ui` and `traefik-admin` were `degraded`, with the Static Instance of the old port `critical`. `remove yard --host 127.0.0.1 --port 19780` made `yard` healthy. The ports in that output are the ports of the test, and the page says so.
  - Dashboard: the Backups page has `New Backup`, `Upload a Backup`, and `Import`, `Download` and `Delete` for each row. `Upload a Backup` opens `/backups/upload` with the file field `Backup file` and `Upload`. `Import` opens "Plan a Backup importation" with `Add` and `Update` and `Show the plan`. The plan shows "What's going to change" and Skipped, and `Import` made the change, with the name of the safety Backup. I did not test `New Backup`, `Download` or `Delete`.
- Not tested: `ssh old-laptop lvh-yard export | lvh-yard import -` over a real `ssh` (the standard input form works with a local pipe), the move between operating systems, `scp`, and a Yard.
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.

## Review round: what a Backup holds

- The maintainer asked to take the holds and never-holds out of the diagram and to list both in "Before you start". The diagram is now the move only. "Before you start" has two lists, "A Backup holds" and "A Backup never holds".
- Sources of the lists: the user guide section "Backups" (the Services with their Publications, Instance Priority and Instance Sources, the Route Profiles, the version and the `exported-from` block, the host left out, Bound Instances, Foreign Instances, Health, Remote Services, private certificates, Consul data and Compose files, the environment settings) and the glossary entry "Backup". The export of the test showed exactly four top-level keys: `version`, `exported-from`, `route-profiles` and `services`. So the file holds nothing else, and the items "membership of a Yard" and "containers, images and processes" follow from that and from the format, not from a sentence of the user guide. A Static Instance with the address of the old Workstation had no `host` in the export. Not tested: the `access-log` and `mutual-tls` keys, Aliases and Compose Bindings in an export (the user guide shows them).

## Review round: ws-a and ws-c

- The maintainer asked for the Workstation badges on this page, with `ws-a` for the old Workstation and `ws-c` for the new one, in blue. `steps.css` has a third class, `ws-c`, with the blue of the sequence diagrams (`rgb(33, 118, 255)`, the pill at 35 percent), and `docs/development.md` names the three badges. The page uses `**ws-a**{.ws-a}` and `**ws-c**{.ws-c}` on the steps, the bullets and the paragraphs that belong to one Workstation, and the diagram colors its two Workstations the same way. The new Workstation is `ws-c` everywhere, in the text and in the outputs.
- The outputs with the name of the new Workstation (the catalog, the two safety Backups) come from a new run of the move test, with the new Workstation named `ws-c` (folders `old` and `new` under `/tmp`). The results were the same: `Added 8, skipped 13.`, `401` then `200`, and `update Publication of web` with `Added 0, updated 1, skipped 23.` The folder names `old` and `new` are generic Compose project names. The volumes of `new` did not exist before the test (the volume `new_consul-data` was created at the time of the test), and `old` was removed with `down -v`, so a volume of that name could have been removed if it had existed. None of the maintainer's containers or volumes shows that.
- The Mermaid `style` line cannot hold `rgba(...)`, because a comma splits its properties. The diagram uses 8-digit hex colors, which work in the light and the dark theme.

## Approval notes

- The maintainer approved the page after editing it: a shorter "A Backup holds" and "A Backup never holds" (Bound Instances, Foreign Instances, Health and Remote Services are not in the second list any more), inline `ws-a` and `ws-c` badges in the text, and the step numbers (Prettier numbers the list in order).
- The hook gitleaks refused the commit because of the Check line of `curl` with a user and a password (rule `curl-auth-user`). The page now has only the request without a password, which answers `401 Unauthorized`, and that is enough to show that the Route Profile came with the Backup. The request with the password answered `200 OK` in the test, and the page does not show it.
