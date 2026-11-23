# 05: Start: Install

**What to build:** Install the CLI, run `ws init`, start the Workstation, and check it. One page for Linux and macOS, with PowerShell tabs for Windows 11.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## Reader's need

The reader wants a working Workstation in five minutes, without reading the whole user guide.

## The page covers

- `curl -fsSL https://lvhy.cc/install.sh | sh`, and the PowerShell command from the README, as tabs.
- `lvh-yard ws init`, with a pointer to Pick your Base Domain for its second question.
- `docker compose up -d` in the Workstation Folder, and `lvh-yard status`.
- A diagram of what `ws init` writes and what Docker starts (Consul, the dashboard, Traefik).
- Requirements: Docker with Compose, and the Windows limits. Docker is the only runtime. Podman is not supported.
- The dashboard-has-no-login warning, before the first command that starts it.

## Verification

Run the steps on a clean Linux user. Windows: label "tested by hand" and use `docs/windows-test-vm.md`.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/start/install.md`, the second page under "Start" in the nav.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` (saved in `.scratch/guide-site/drafts/05-install.stage1.md`, which Git ignores) and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and the page shows no output. See "Draft notes". The Windows commands come from `docs/user-guide.md` and could not run here.
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve`.
- [x] The maintainer approved the page on 2026-11-22, and it left `draft_docs`. The maintainer chose `lvh.me` as the default of the Base Domain field, and a field on every page. The ticket-07 diagrams keep `lvh.me`, because the maintainer did not ask to change them.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

Run on Linux, on a throwaway Workstation in `/tmp`, with the CLI built from the sources:

- The install script ran from `https://lvhy.cc/install.sh` with `LVH_YARD_VERSION=0.2.0-rc.1` and `LVH_YARD_INSTALL_DIR=/tmp/lvh-bin`. It installed `lvh-yard 0.2.0-rc.1` and printed the line to add to `PATH`. Without `LVH_YARD_VERSION`, it fails with a 404 on `releases/latest`, because the only release is a pre-release. The page says "the latest one" for the default, which holds after the first stable release (`v0.2.0`). Check this at the release gate.
- `lvh-yard ws init` at a terminal asked two questions: the name (default: the hostname of the computer) and the Base Domain. It did not ask for a LAN address. It wrote `compose.yaml`, `.env`, `lvh-yard.yaml`, `consul.d/`, `scripts/`, `tls/`, `traefik/`, `backups/` and a layout marker, with mode 700 for folders and 600 for files.
- `lvh-yard ws compose start`, then `lvh-yard status`: the first line was `LVH Yard on this Workstation: ok`. With Traefik stopped, the first line was `LVH Yard on this Workstation: failing`, the Traefik line said `FAIL` and "Traefik does not answer its ping ... Make sure that it runs.", and the command exited with 1. `lvh-yard ws compose logs traefik` showed the Traefik log.
- The test used other ports than the defaults (`ws set http-port` and the rest), because a real Workstation runs on this computer. The page uses the default dashboard address, `http://127.0.0.1:7780`, from `docs/user-guide.md`.
- Windows: the requirements (Docker Desktop with Linux containers, the WSL2 backend, host networking on, Enhanced Container Isolation off, IPv4 only, an unsigned executable), the installer commands and the start commands come from `docs/user-guide.md`. "Docker Desktop 4.34 or later" comes from `.scratch/windows-11/spec.md`. Verify them on Windows with `docs/windows-test-vm.md`.
- The page does not link to this page from "What LVH Yard solves" yet. Add the link when this page leaves `draft_docs`, because a link from a published page to a draft fails the strict build.

Review round of 2026-11-22:

- Clickable addresses. `http://127.0.0.1:7780` is now a Markdown link in both places. `http://yard.<Base Domain>` and the addresses of the new table are made links by the Base Domain script, because they hold a placeholder.
- `irm https://lvhy.cc/install.ps1 | iex`: checked in PowerShell 7.7.0 on Linux (`mise x powershell@7.7.0 -- pwsh`). `irm` is the alias of `Invoke-RestMethod` and `iex` the alias of `Invoke-Expression`. `irm` returns a `String` of 5535 characters, equal to the content length, although the server sends `application/octet-stream`, while `Invoke-WebRequest` returns `Byte[]`. Piped to `iex`, the script ran: it downloaded the Windows ZIP of `0.2.0-rc.1` and extracted `lvh-yard.exe` into the folder of `LVH_YARD_INSTALL_DIR`. It then failed at the user `PATH` step, which needs the Windows registry (`dev/install.ps1` line 57). That failure is expected on Linux. Windows PowerShell 5.1 was not run here. The earlier Windows evidence (`.scratch/windows-11/evidence/10-maintainer-checklist.md`) shows that `Invoke-RestMethod` returns text in both shells, and the aliases exist since PowerShell 3 (not checked here). The page keeps the read-first form and adds the short form as an option.
- The new section "The default Services" comes from a fresh Workstation with the dashboard from the image. `lvh-yard catalog --details` showed `yard`, `consul-ui` and `traefik-admin` with one Static Instance each on `127.0.0.1`, at the ports of that Workstation (the default ports are 7780, 8500 and 8080). Through Traefik, `yard.<Base Domain>` answered `200`, `consul-ui.<Base Domain>` redirected to `/ui/` and `traefik-admin.<Base Domain>` redirected to `/dashboard/`. The three Services have Local Scope. The ordinary-Services sentence and the "always at `127.0.0.1:7780`" sentence come from `docs/user-guide.md`. Page 07 repeats the first sentence about these Services. When this page is published, replace that sentence with a link to "The default Services".
- Study of a Base Domain field in the header (prototype, not final): see "Base Domain field" below.

## Base Domain field

The header of the preview now has a field. Findings from the prototype (`docs/guide/javascripts/base-domain.js`, `docs/guide/stylesheets/base-domain.css`):

- It works. Zensical gives `document$`, which fires on each page of the instant navigation, and a header with `.md-header__inner` that a script can extend. The script replaced `lvh.me` and `<Base Domain>` in inline code, in code blocks and in a table, made 4 addresses on the Install page and 2 on "Your first Service" links, kept the domain after a reload and after the instant navigation, and ignored an invalid hostname. The page "Pick your Base Domain" stays as written because of the comment `<!-- base-domain: off -->`.
- Limits: a Mermaid diagram is in a closed shadow root, so its text keeps `lvh.me`. The header field and the replaced text need JavaScript. Without it, a reader sees `lvh.me` and `<Base Domain>`, and an address with a placeholder is not a link. A host name that a syntax highlighter splits into two elements is not replaced. No page of the guide splits one today. The script replaces every `lvh.me`, so a page that uses it for another meaning needs the `off` comment.
- Open questions for the maintainer: use `lvh.me` as the default of the field, or `<Base Domain>` everywhere? Show the field on every page, or only on pages with examples? Replace the domain in the ticket-07 diagrams by hand?

## Optional section: a Workstation without Traefik

- The maintainer moved the section "A shared host without Traefik" from the recipe "Reach a service on another computer" (ticket 08) to this page, as an optional section for a shared Workstation that nobody uses directly to open Services. The page has it as `A Workstation without Traefik`, with the way back (`ws set traefik on`).
- The commands were tested in the ticket 08 session on a Workstation of a two-Workstation Yard: `ws compose stop`, `ws set traefik off`, `ws compose start`. `lvh-yard status` had no Traefik part, no process listened on its Traefik port, and the other Workstation still routed to its Service. `lvh-yard service delete traefik-admin` worked. The way back (`ws set traefik on`, then stop and start) follows the user guide ("A host without Traefik"). Only `ws set traefik on` printed its output in the test.
