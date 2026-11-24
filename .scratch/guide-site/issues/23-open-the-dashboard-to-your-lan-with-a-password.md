# 23: Recipe 20: Open the dashboard to your LAN with a password

**What to build:** Write the recipe "Recipe 20: Open the dashboard to your LAN with a password" with the recipe template of the spec.

**Blocked by:** 01, 19, 21

**Status:** done

**Core for v0.2.0:** no

## Pain

"I want to open the dashboard from another computer, but it has no login."

## Result

`yard.<own domain>` asks for a password. The dashboard itself still listens on `127.0.0.1`.

## Commands to use

- `lvh-yard profiles set` with a basic-auth profile, `lvh-yard publish yard --profile basic-auth`.
- `lvh-yard ws set traefik-bind-address <LAN address>`, and keep `dashboard-bind-address` at `127.0.0.1`.
- HTTPS: a pointer to recipes 3 and 19. A reader needs an own domain that points to the Workstation.
- `curl -H 'Host: yard.<Base Domain>' http://<LAN address>/` as the check: `401` without the password, `200` with it.

## Diagram idea

A diagram: a remote browser, Traefik on the LAN address (password), the dashboard on loopback. Show the bypass when the dashboard listens on `0.0.0.0`.

## Verification

`mise run dev:up`. Test the three conditions of the spec. State what happens over plain HTTP. A public server is not tested: use the approved text and link to the Advanced page.

## Notes

Warn first: anything that runs on the Workstation still reaches the dashboard without a password.

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

- The page is `docs/guide/recipes/https/dashboard-password.md`, titled "Open the dashboard to your LAN with a password" (`<!-- base-domain: off -->`, because it uses `dev.example.com`). It is in `draft_docs`, and the navigation lists it in "HTTPS and access". The stage 1 text is in `drafts/23-dashboard-password.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading. The warning that anything on the Workstation still reaches the dashboard comes first, before the diagram and every command. The diagram shows the browser, Traefik on the LAN address with the password, the dashboard on loopback, and the bypass when the dashboard listens on `0.0.0.0`. The page links to "Run on a public server (not tested)" of the Advanced page for a server that the internet reaches.
- Tested on a throwaway Workstation in `/tmp` (folder `rcdlx`, Workstation name `ws-a`) with the default ports and the Base Domain `dev.example.com`, on the LAN address of this computer (`192.168.1.67`, written as the invented `192.168.1.20` on the page). The maintainer's Workstation was stopped, and its ports were free. The outputs on the page are real, except for the `risk` line and the `test` lines, which are exact. DNS of `dev.example.com` was not set, so the test used `curl --resolve`.
  - `htpasswd -nbB`, `profiles set`, and `publish yard --profile basic-auth` printed the lines on the page, with the `risk: [Authenticated Route]` line for `127.0.0.1:7780`.
  - `ws set traefik-bind-address <LAN>`, `ws set dashboard-bind-address 127.0.0.1` and `ws compose recreate`: Traefik listened on the LAN address only. A request to `yard.dev.example.com` on `127.0.0.1:80` failed with `curl: (7) Failed to connect to yard.dev.example.com:80 after 0 ms: Could not connect to server`.
  - Before the profile, a request with the `Host` header to the LAN address answered `200`: the dashboard was open. After it, HTTP answered `401` and HTTPS answered `HTTP/2 401` without a password and `HTTP/2 200` with it (the page does not show the password request, because the gitleaks hook refuses a `curl` line with a user and a password). The dashboard port on the LAN address answered `000` (no connection). `lvh-yard test yard` printed `answered 401` for the HTTP and the HTTPS Route and `Every check passed.`
  - Bypass: with `dashboard-bind-address 0.0.0.0`, `http://<LAN>:7780` answered `200` without a password. With `127.0.0.1` again, it answered `000`.
  - Undo: `publish yard --no-profiles`, `ws set traefik-bind-address 127.0.0.1` and `ws compose recreate` made the Route on loopback answer `200` again, and `status` said `ok`.
  - The `ws set` commands print no warning about the bind addresses.
- Not tested: a second computer, a firewall, a real DNS record, the dashboard form of the profile and the Service card for `yard` (the same forms as ticket 21), a browser, and a public server.
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.
