# 06: Start: Pick your Base Domain

**What to build:** The page that teaches the first principle: for development, the Base Domain is a wildcard DNS that answers `127.0.0.1`. It follows the spec section "The Base Domain".

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## Reader's need

The reader meets the question "Base Domain:" in `ws init` and does not know what to type.

## The page covers

- The diagram of the spec: browser, DNS, `127.0.0.1`, local Traefik, Instance, and the Yard Scope hostname `grafana.ws-b.<Base Domain>`.
- The four options in the order of the spec, with `lvh.me` and `l.lvhy.cc` as the examples. `l.lvhy.cc` is this project's domain.
- The approved warning about a shared domain, as an admonition.
- How to test a choice before you use it: a command that shows the answer for `anything.<Base Domain>` and the other levels (`a.ws-b.<Base Domain>`).
- How to change it later: `lvh-yard ws set base-domain <domain>` then `lvh-yard ws compose recreate`.
- A pointer to recipe "Your DNS blocks the Base Domain".

## Verification

Resolve `a.l.lvhy.cc`, `x.ws-b.l.lvhy.cc` and `a.lvh.me` again on the day you write. Test `localhost` (see the open check in the spec) on Linux, and say what you did not test on macOS and Windows. Test the change of Base Domain on a test Workstation. This is the first page to draft. It sets the voice.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/start/pick-your-base-domain.md`, in the nav under "Start".
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page follows the two-stage process of the spec: structure from `technical-writing`, then sentences from `ste80-humanizer`. This page was the trial for that process, so it has no separate stage 1 file: its text came from the `technical-writing` draft, was made STE-clean, and the maintainer edited it afterwards. `ste_check.py`: 0 findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real. See "Draft notes".
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve` (diagram, tabs, table, admonition).
- [x] The maintainer approved the page on 2026-11-22, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

Verified on 2026-11-21 on Linux, on two throwaway Workstations in `/tmp` (a Base Domain of `lvh.me` and one of `localhost`), with the dashboard from sources and from the image:

- DNS: `a.l.lvhy.cc`, `x.ws-b.l.lvhy.cc`, `lvh.me`, `a.lvh.me` and `x.ws-b.lvh.me` answer `127.0.0.1`, from the system resolver and from `1.1.1.1`. None has an AAAA record. `l.lvhy.cc` alone has no record.
- `localhost`: `curl` tries `::1`, is refused, and uses `127.0.0.1`. Chromium reaches Traefik. Python's `getaddrinfo` lists `::1` first. macOS and Windows are not tested.
- Routes: `front.lvh.me`, `grafana.localhost` and `grafana.ws-b.lvh.me` (opened on ws-a, whose home is ws-b with Base Domain `localhost`) all returned the page of the right Instance.
- Change: `ws set base-domain` then `ws compose recreate` moved the hostnames from `lvh.me` to `l.lvhy.cc`, back to `lvh.me` (dashboard from the image), and to `dev.example.com` (checked with `curl --resolve`). The old hostname then answers `404 page not found`. `ws set` refuses `not a domain`.
- Rebind protection: dnsmasq 2.93 with `stop-dns-rebind` returns no answer for `a.lvh.me` and `a.l.lvhy.cc`, and logs `possible DNS-rebind attack detected`. `address=/lab.home/127.0.0.1` answers. `rebind-domain-ok=/l.lvhy.cc/` lets the shared domain through. No router was tested.
- The dashboard and `lvh-yard status` do not check that the Base Domain resolves.
- Not on the page, by decision of the Writer: the recipe for a blocked DNS (ticket 15) will deepen "If the name does not resolve". The page has no link to it, because a link to a draft page fails the strict build.
- Diagram colors (2026-11-21, for every later diagram): the theme of Zensical sets one color for all sequence-diagram boxes, with a `rect.rect` rule that is part of the `themeCSS` that it passes to Mermaid. The SVG of a diagram sits in a closed shadow root, so a page stylesheet cannot select its boxes. `docs/guide/javascripts/mermaid-box-colors.js` (in `extra_javascript`) adds one `!important` fill rule for each of the three box colors to that `themeCSS`, before Mermaid starts. Tested on 2026-11-21 in light and dark: the boxes are blue, green and orange, and the theme keeps its text, line and arrow colors. A `themeCSS` in the frontmatter of one diagram also works, but it replaces the whole theme stylesheet, so it must restore the dark-mode rules by hand, and Mermaid drops it if it contains a greater-than sign. `rgba()` in a `style` line of a flowchart breaks the line, because of the commas. Zensical loads Mermaid from `unpkg.com` when a page is viewed, so the diagrams need internet access.
- The diagram shows `grafana` as a Service of `ws-a` with an Instance on `ws-b` at the invented address `192.168.1.20:3001`, as the maintainer asked. The text uses `wiki` for the Yard Scope example, so `grafana` has one meaning on the page.
- HTTPS with the Workstation CA (2026-11-22, Linux, a throwaway Workstation with Traefik in Docker, one Python app and one Docker container as Services, Chromium with the CA in a temporary trust store, and `curl --cacert`):
  - Base Domain `localhost`: `ws tls ca create` and `ws tls https create` work, and the certificate names `*.localhost`, `localhost` and `127.0.0.1`. The chain verifies (`openssl s_client`: OK), but `curl` says "subjectAltName does not match hostname front.localhost" and Chromium says `ERR_CERT_COMMON_NAME_INVALID`, for both Services. Both clients refuse a wildcard with one label after the star. Firefox was not tested.
  - Workaround that works: `ws tls https create --address front.localhost --address echo.localhost`, then `ws compose recreate`. Both clients accept both Services, and a `Secure` cookie that the app sets over HTTPS comes back on the next request. It needs one `--address` per Service.
  - Base Domain `lvh.me`: the default certificate (`*.lvh.me`) is accepted by `curl` and Chromium for both Services.
  - Base Domain `dev.localhost`: `ws set base-domain` accepts it, the default certificate (`*.dev.localhost`) is accepted by `curl` and Chromium for both Services, the `Secure` cookie round trip works, and plain HTTP still answers. `*.localhost` names need no DNS, so this works offline.
  - Rule: a Base Domain of one label (`localhost`) gets a certificate that browsers and `curl` reject. A Base Domain of two labels or more (`lvh.me`, `l.lvhy.cc`, `dev.localhost`) gets one that they accept. `l.lvhy.cc` was not run, but it has the same shape as `lvh.me`.
  - The `ws init` text and the page suggest `localhost` as the offline choice. They do not say that HTTPS fails with it.

Side effect on the maintainer's old test state: `dev:up` reused the Docker volumes `ws-a_consul-data` and `ws-b_consul-data` of 2026-11-07. I removed the Instances that I registered, but `front` (Local) and `grafana` (Yard Scope) were published again, and their earlier Publication is unknown. Both test Workstations are stopped again.
