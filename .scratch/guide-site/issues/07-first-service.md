# 07: Start: Your first Service

**What to build:** Create a Service, add a Static Instance and a Container Binding, publish it, open it, and read `lvh-yard catalog`.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## Reader's need

Pains 1 and 2: "I forget the port" and "Docker changes my port". The reader wants the first hostname working.

## The page covers

- `lvh-yard service create front`, `register static front --port 3000`, `publish front`, and the browser address.
- `lvh-yard register container mailpit-1 --port 8025 --service mailpit` and `publish mailpit`.
- `lvh-yard catalog`, with its real output and what each column means.
- A diagram of Service, Instances and Publication, using only the words of the glossary.
- Undo: `unpublish`, `remove`, `service delete`.

## Verification

Run every command on `mise run dev:up` and paste the real output. Use a real Docker container.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/start/first-service.md`, in the nav under "Start" after "Pick your Base Domain".
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` (saved in `.scratch/guide-site/drafts/07-first-service.stage1.md`, which Git ignores) and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real. See "Draft notes".
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve` (diagram, code blocks, list).
- [x] The maintainer approved the page on 2026-11-22, and it left `draft_docs`. The same day, the maintainer asked for the output rule on the Base Domain page and for `--host 127.0.0.1` in the README demo. Both are done.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

Verified on 2026-11-22 on Linux, on a throwaway Workstation in `/tmp` (Base Domain `lvh.me`, `bind-address` set to the LAN address `192.168.1.67`, the dashboard from the image, HTTP on port 10480). The page shows `http://front.lvh.me` without a port, as on a normal install (port 80). I removed the Workstation, the container and the app afterwards.

- Every command and output of the page ran in that order. The outputs on the page are the real ones, except that the creation, registration and publication outputs of the container Service are shown as one block.
- Without `--host`, `register static` used the LAN address of the Workstation (`192.168.1.67:4000`). The app listened on `127.0.0.1` only, so the Service was `unhealthy` (`0/1`), `lvh-yard test front` printed "Connection refused", and the hostname answered `502 Bad Gateway`. The section "Connection refused" shows that output. This is why the page uses `--host 127.0.0.1` in its main example. The README demo uses `--port 3000` with no `--host`, which fails for a dev server that listens on localhost only. Consider updating the README demo.
- The page uses port 4000 and not 3000, because another program on this computer (`hw_server`) listens on 3000 and 3001.
- After `unpublish`, the hostname answered `404 page not found` and the catalog showed `front` as `unpublished` with `1/1`. The section "404 page not found" relies on the 404.
- The container started with `-p 8025` got host port 33673, and after `docker rm -f` and a new `docker run`, port 33674. The Container Binding followed the new port, and `mailpit.lvh.me` kept answering `200`.
- The first `publish` output shows `State: unhealthy`, and the catalog was `healthy` 12 seconds later. The page says the default check runs every 10 seconds (`docs/user-guide.md`: "default: TCP, 10s, 2s").
- Not covered, on purpose: Yard Scope, HTTPS, Route Profiles and Compose bindings. Other pages cover them.
- The Mermaid labels use `<br/>`, because the labels wrapped at the hyphen of `mailpit-1`.

Review round of 2026-11-22. The maintainer asked for two product changes and two edits of the page:

- `lvh-yard service create` now prints a command for each Instance Source (Static Instance, Container Binding, Container Binding for a Compose service, Alias). `cli/internal/command/service.go`, test `TestServiceCreateSuggestsEveryInstanceSource`.
- `lvh-yard register static` asks for the port and the host at a terminal when no flag gives them. Without a terminal, a missing port is an error ("Missing --port. No terminal is open: pass --port") and a missing host is left to the dashboard. `cli/internal/command/register.go`, tests `TestRegisterStaticAsksForTheFlagsThatAreMissing`, `TestRegisterStaticWithoutAPortAsksNothingWhenNoTerminalIsOpen` and `TestRegisterStaticStopsWhenTheInputEndsBeforeTheAnswer`. `mise run cli:check` and `mise run cli:smoke` pass. `docs/user-guide.md` describes both changes.
- The intro now has two paragraphs: the problem of a process (the port that you must remember), then the problem of a container (the host port that changes at each start).
- The step `docker port mailpit-1 8025` and its output are gone, and so is the sentence with the port `33674`.
- The page was verified again with the rebuilt CLI on a new throwaway Workstation. The prompt transcript on the page is the real text of the prompts and the answers typed, in the order that a terminal shows them. The capture through a pseudo terminal printed the typed answers first, because `script` read them from a pipe.
- The README demo (`register static front --port 3000`) still has no `--host`. The maintainer has not decided about it.

Correction of 2026-11-22, found while checking ticket 05. The page said that, without `--host`, `register static` uses the LAN address of the Workstation. That is true only for a Workstation that is in a Yard. The default host is the address that Consul uses (`YARD_BIND_ADDRESS`). On a Workstation that `ws init` just created, on Linux and macOS, it is `127.0.0.1`, and `register static front --port 4000` with an app on `127.0.0.1` answered `200`. After `ws yard init` or `ws yard join`, it is the LAN address, and the same command gave `Connection refused` in the check of this ticket. The page, the `--host` help text, the prompt text, the `Long` text of `register static`, the README and the user guide now say so. The test of ticket 07 had set `bind-address` to the LAN address by hand, so it showed the Workstation-in-a-Yard case.

Second review round of 2026-11-22. The maintainer set a rule (now in the spec): show the output of a command only when the reader needs it to follow the guide, and do not explain the columns of a table. The page keeps two outputs: the catalog, because the section is about reading it, and `lvh-yard test front`, because it names the cause in "Connection refused". The prompt transcript, the other outputs and the list of columns are gone from the page. The page still says what `lvh-yard publish` prints, because the State `unhealthy` needs that sentence.

Real outputs of the commands that the page no longer shows (Base Domain `lvh.me`, from the verified run):

```text
$ lvh-yard service create front
Created front. It is empty and Unpublished.
Next, add Instances. Pick the Instance Source that fits:
  Static Instance             lvh-yard register static front --port <port>
  Container Binding           lvh-yard register container <container> --service front
  Container Binding, Compose  lvh-yard register compose <project>/<service> --port <port> --service front
  Alias                       lvh-yard alias add front --workstation <workstation> --service <service>
$ lvh-yard register static front --host 127.0.0.1 --port 4000
Registered front at 127.0.0.1:4000, Instance 1cb10113-542f-4b47-b0cb-1875de2aaf44.
$ lvh-yard register static front
Port of the app: 4000
Use 127.0.0.1 for an app that listens on localhost only.
Host of the app (Enter for the LAN address of this Workstation): 127.0.0.1
Registered front at 127.0.0.1:4000, Instance 2feb6871-9060-4fa1-8c32-3e563e154500.
$ lvh-yard publish front
Published front at front.lvh.me.
  Scope: Local
  State: unhealthy
$ lvh-yard register container mailpit-1 --port 8025 --service mailpit
Registered mailpit-1 as mailpit. mailpit is not published.
$ lvh-yard unpublish front
Unpublished front. Its Instances stay registered.
$ lvh-yard remove front --port 4000
Removed front at 127.0.0.1:4000.
$ lvh-yard remove mailpit --container mailpit-1
Removed the Container Binding of mailpit to container mailpit-1, port 8025.
$ lvh-yard service delete front
Deleted front with its Publication, Static Instances and Container Bindings. Its containers keep running.
```

## Review round: the dashboard section

- The maintainer asked for a short section "The same steps in the dashboard" in every page with CLI steps for Services, and for a review of the pages already written. Only this page has such steps, so only it got the section. "What LVH Yard solves", "Install LVH Yard" and "Pick your Base Domain" have no steps for Services.
- The labels come from the running dashboard of a throwaway Workstation: the navigation bar has Catalog, Containers, Route Profiles and Backups. The Catalog page has New Service. The page of a Service has Add Static Instance, Bind container, Add Alias and the Published switch. The Containers page has a Create Service button for each port. The dashboard at `http://yard.<Base Domain>` is the `yard` Service, and `http://127.0.0.1:7780` is always open.
- The spec says in the recipe template that every page with such steps has this section.
