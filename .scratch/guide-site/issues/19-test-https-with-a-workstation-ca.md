# 19: Recipe 3: Test HTTPS with a Workstation CA

**What to build:** Write the recipe "Recipe 3: Test HTTPS with a Workstation CA" with the recipe template of the spec.

**Blocked by:** 01, 06

**Status:** done

**Core for v0.2.0:** yes

## Pain

"I need HTTPS in development, and I do not want to click through a browser warning."

## Result

Traefik serves HTTPS with a certificate that your Workstation CA signs, and your browser trusts that CA.

## Commands to use

- `lvh-yard ws tls https create`, `lvh-yard ws tls https enable`, and the commands that they print.
- Browser trust for Linux, macOS and Windows, as tabs, and for Firefox. Read the user guide section "Browser trust".
- `lvh-yard ws tls ca issue service` for a Service Certificate, and `lvh-yard publish <service> --mutual-tls on` for mutual TLS to a Service.
- `curl --cacert` as the check.

## Diagram idea

A diagram of the three TLS Parts of Traefik (HTTPS, Client Certificate authentication, mutual TLS to Services), and a sequence diagram of the browser trusting the Workstation CA.

## Verification

`mise run dev:up` for the commands and `curl`. Browser trust is by hand: label each OS. Read ADR-0016 and ADR-0018. Warn that the Workstation CA key stays on the Workstation, and that a trusted CA can sign for any name.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/recipes/https/workstation-ca.md`, the first page of "HTTPS and access" in the nav.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. See "Draft notes".
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Setup: a throwaway Workstation in `/tmp` (folder `rc19w`, other ports than the defaults, `ws set https-port 18443`, Base Domain `lvh.me`) with a Static Instance `app` on `127.0.0.1:4000`, first a `python3 -m http.server`, then a small TLS server that requires a client certificate. The page uses the default ports (HTTP 80, HTTPS 443), so its `curl` commands have no port. The Workstation, the servers and the files are removed.
- HTTPS: `ws tls ca create`, `ws tls https create` (output `HTTPS certificate for ws-a signed.` and `Names: DNS:*.lvh.me, DNS:localhost, IP:127.0.0.1`), `ws compose recreate`. `curl -sI --cacert .../ca/ca.crt https://app.lvh.me` printed `HTTP/2 200`. The HTTP hostname still answered `200`. `openssl s_client` with the CA gave `Verify return code: 0 (ok)`. Without `--cacert`, `curl` stopped with `curl: (60) SSL certificate OpenSSL verify result: unable to get local issuer certificate (20)`.
- Mutual TLS: `ws tls mtls create`, `ws compose recreate`, `ws tls ca issue service app --password ...` (writes `ca/service/app/service-fullchain.crt` and `service.key`). The TLS server used those files and `ca.crt`, and required a client certificate. Before `publish app --mutual-tls on`, the Route answered `502`. After it, the Route answered `200` and `lvh-yard test app` printed the three `ok` lines that the page shows, with `Route https://app.lvh.me: answered 200`.
- Undo: `publish app --mutual-tls off`, `ws tls mtls disable`, `ws tls https disable` and `ws compose recreate` turned HTTPS off (the HTTPS request failed to connect) and kept the files.
- The dashboard catalog showed a lock next to each hostname, and the page of a Service has the switch `Mutual TLS to the Instances`. A form to make a CA or a certificate does not exist.
- Browser trust was not tested in any browser, and the commands for Linux and macOS are not on the page: the user guide has none, and nobody ran them. The page tells the reader to import `ca.crt` in the browser or in the operating system, as the user guide does. The Windows command comes from the user guide, which says that the tests ran no browser for it. Add tested commands for Linux and macOS when someone has run them. The Firefox sentence comes from the user guide.
- Not tested: a certificate for another Workstation (`--address '*.ws-b.lvh.me'`). The sentence comes from the user guide and the help text of `https create`. The Client Certificate part is not on this page.
- The warning before the trust step comes from the user guide ("Import only ca.crt", "Trusting the CA lets the browser trust any website certificate it signs", "nothing can revoke a certificate it signed").
- The recipe "Fix cookies and OAuth callbacks" links to this page since it was published.
