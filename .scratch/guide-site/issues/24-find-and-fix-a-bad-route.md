# 24: Recipe 14: Find and fix a bad Route

**What to build:** Write the recipe "Recipe 14: Find and fix a bad Route" with the recipe template of the spec.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## Pain

"The hostname does not work, and I do not know why."

## Result

You name the cause from a Service Warning, a Reachability test, the Route Diagram, or the access log.

## Commands to use

- `lvh-yard catalog --details` for the state and the Service Warnings.
- `lvh-yard test <service>` for Reachability.
- `lvh-yard publish <service> --access-log on` and `lvh-yard ws compose logs traefik`.
- `lvh-yard status`.
- The Route Diagram of the dashboard, as a content tab.

## Diagram idea

A decision diagram that starts at "the hostname fails": the name resolves? the state? the Instance? the warning?

## Verification

`mise run dev:up`. Break a Service on purpose in four ways (no Instance, a critical check, an Out-of-range Instance, a bad Route Profile) and paste the real output. Read the user guide sections "Service Warnings" and "Reachability tests".

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

- The page is `docs/guide/recipes/fix/bad-route.md`, titled "Find and fix a bad Route" as in the outline. It is in `draft_docs`, and `zensical.toml` has a new navigation section "Find and fix problems". The stage 1 text is in `drafts/24-bad-route.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading. The diagram is a decision flowchart: does the name resolve, does the browser connect, what does Traefik answer.
- The page is organised by what the hostname answers, because that is what the reader sees first: `Could not resolve host` (links to the section in "Pick your Base Domain", no copy), `Failed to connect`, `404 page not found` and `502 Bad Gateway`. The CLI steps are the ladder: the answer, `catalog`, `catalog --details`, `test`, `status` and the access log.
- Tested on a throwaway Workstation in `/tmp` (folder `rcfw`, Workstation name `ws-a`, other ports than the defaults), with the Service `web` at `127.0.0.1:4000` (`python3 -m http.server`). Every output on the page is real, except that the Instance ID is the one of that run.
  - Baseline: `test web` printed `Every check passed.`
  - `unpublish web`: the hostname answered `404 page not found`, `catalog` showed `unpublished` with no hostname, and `test` said `Not published, so only its Instances are tested.`
  - Process stopped: the hostname answered `502 Bad Gateway`, `catalog` showed `unhealthy 0/1`, and `test` printed the three FAIL lines on the page. The Service page showed the red alert "Unhealthy", and the Instance showed the critical check `dial tcp 127.0.0.1:4000: connect: connection refused`. The Reachability page and the Route Diagram page loaded, and the Route Diagram showed the Instance as `critical`, `in the Route`.
  - Traefik stopped with `docker stop`: `curl` exited with 7 (`Failed to connect`), `status` said `failing` with exit code 1 and the FAIL line for Traefik, `test` named the address, and the catalog still said `healthy`. `ws compose start` fixed it. The page does not quote the `curl` line, because its port text depends on the port of the Workstation.
  - Access log: `publish web --access-log on` printed `Access log: on`. In the log of `ws compose logs traefik`, a request for a hostname with no Route had `"-" "-"`, and a request that reached the app (the app answered 404 for `/missing`) had `"web@http" "http://127.0.0.1:4000"`. `--access-log off` ran with no extra output.
- Not reproduced, so the page leaves them out: an unknown Route Profile (the CLI refuses an unknown name, and refuses to remove a profile that a Service uses, so only a Backup import can make one), an Out-of-range Instance of a Remote Service (needs two Workstations, see the page "Show your work to a teammate"), and a router that blocks the DNS answer (see "Pick your Base Domain"). The Windows form of `ws compose logs traefik | tail` is not shown, because the page does not use a pipe.
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.

## Review round: a mutual TLS problem

- The maintainer asked for a problem when mutual TLS does not work, with `mendhak/http-https-echo` on port 8443. The page has a new answer `500 Internal Server Error` (row in the table, node in the diagram, section in Troubleshooting) and a second cause under `404 page not found`.
- Reproduced on a throwaway Workstation (folder `rcmw`) with `ws tls ca create`, `ws tls https create`, `ws tls mtls create`, and the Service `echo` as a Container Binding to the container on port 8443, all output real:
  - Mutual TLS off: the hostname answered `200`, because that image accepts plain HTTP and HTTPS on port 8443 (it uses `httpolyglot`). So this image cannot show the `502` of a TLS-only Service, and the page does not claim it. The page "Test HTTPS with your Workstation CA" keeps that symptom.
  - `publish echo --mutual-tls on` with the image's own certificate: the hostname answered `500 Internal Server Error`, the catalog stayed `healthy`, and `test` failed with "Traefik answered 500: it could not set up a connection to an Instance. If the Route uses mutual TLS, check that the Service Certificate names the address of each Instance. Run "lvh-yard ws compose logs traefik" to see why." The log of Traefik said `tls: failed to verify certificate: x509: certificate is valid for my.example.com, my.example.net, not echo`.
  - A certificate signed for the Service `other` and mounted in the container gave the same `500`, with `certificate is valid for other, localhost, not echo` in the log.
  - `ws tls ca issue service echo`, mounted at `/app/fullchain.pem` and `/app/testpk.pem` with `MTLS_ENABLE=1` (the three traps of the Trail learning `2026-11-17-testing-mtls-to-a-service-three-pablo`): `test` passed, and the body of the echo showed `clientCertificate` with `CN: traefik-ws-a`, which proves that Traefik sent its certificate.
  - `ws tls mtls disable` and `ws compose recreate` while `echo` had the setting: the hostname answered `404`, the catalog said `healthy`, `catalog --details` printed `error: [Mutual TLS] No mutual TLS on this Workstation. Turn it on, or turn it off for this Service.`, and `test` said "Traefik has no Route for echo.lvh.me yet, or no Instance is in range." `ws tls mtls enable` and `ws compose recreate` brought the Route back.
- The hint of the `500` said that the certificate must name "the address of each Instance". The user guide and the log say that Traefik checks the name of the Service, and `ca issue service` says that `--address` is not needed for Traefik. The maintainer asked to fix it. `ReachabilityTester` now says "check that the Service Certificate names the Service". The unit tests pass (`mise run test:unit`), and no test pins the text. The new text was not run against a built dashboard image, because the build would replace the local `latest` image of the maintainer. So the page still cuts the `test` line after its first sentence, and it can show the whole line once the image is rebuilt.
- The fix command uses `~/.config/lvh-yard/ca/service/echo/`. A Windows form is not given.
