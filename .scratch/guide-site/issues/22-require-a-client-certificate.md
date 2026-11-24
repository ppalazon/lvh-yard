# 22: Recipe 13: Require a Client Certificate for an internal tool

**What to build:** Write the recipe "Recipe 13: Require a Client Certificate for an internal tool" with the recipe template of the spec.

**Blocked by:** 01, 19

**Status:** done

**Core for v0.2.0:** no

## Pain

"Only people with my certificate should open this tool."

## Result

A Route Profile with `clientCertificate: {}` makes the Route answer only on HTTPS, and only with a Client Certificate.

## Commands to use

- The commands of the user guide section "Client Certificates": `ws tls client ...` and `ws tls ca issue ...`. Confirm each name with `--help`.
- `lvh-yard profiles set` with `clientCertificate: {}`, then `publish --profile`.
- `curl --cert` as the check, and browser import for each OS.

## Diagram idea

A sequence diagram of the browser showing its certificate to Traefik.

## Verification

`mise run dev:up` and `curl`. Browser import is by hand.

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

- The page is `docs/guide/recipes/https/client-certificate.md`, titled "Require a Client Certificate". It is in `draft_docs`, and the navigation lists it in "HTTPS and access". The stage 1 text is in `drafts/22-client-certificate.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading. The diagram is a sequence of the browser showing its certificate to Traefik. The page sends the reader to "Test HTTPS with your Workstation CA" (steps 1 to 4) for the CA, the HTTPS certificate and the browser trust, and to "Protect a dev service with Route Profiles" for the warning that a profile protects the hostname only.
- Tested on a throwaway Workstation in `/tmp` (folder `rcccw`, Workstation name `ws-a`, other ports than the defaults) with `mendhak/http-https-echo` on port 8080 as the Service `admin` (Container Binding). The outputs on the page are real, except that the path in the `issue user` line is cut to `...` and the `curl` error text is cut to `...` (the OpenSSL version is in the middle).
  - `ws tls client create` printed that Traefik asks for a Client Certificate on Routes whose profile has `clientCertificate: {}`, and `profiles set` and `publish admin --profile certificate` printed the lines of the page, with the `risk: [Authenticated Route]` line. In the test I ran `client create` before the first `compose start`, so the `compose recreate` of step 2 was tested with `ws tls client enable` and `disable`, both followed by `ws compose recreate`.
  - `ws tls ca issue user alice --password <password>` wrote `ca/client/alice.p12` with mode 600.
  - `curl` with no certificate failed with `curl: (55) OpenSSL SSL_read: ... tlsv13 alert certificate required, errno 0`. With `--cert-type P12 --cert alice.p12:<password>` and the CA, it answered `HTTP/2 200`. `http://admin.lvh.me` answered `404 page not found`. `lvh-yard test admin` printed `Traefik asks for a Client Certificate, as the Route requires. This test has none.` and `Every check passed.`
  - The chain profile `secure-with-user` (`certificate` and `passTLSClientCert` with `commonName: true`): the echo showed `x-forwarded-tls-client-cert-info` with `Subject%3D%22CN%3Dalice%22%2CSubject%3D%22CN%3DLVH+Yard+CA+-+ws-a%22`. `curl` sends the whole chain, so the header has the CA after the person, as the user guide says.
  - `ws tls client disable` and `ws compose recreate`: the HTTPS hostname answered `404`, the catalog said `healthy`, `catalog --details` printed `error: [Client Certificate] No client authentication on this Workstation. ...`, and `test` said Traefik has no Route yet. `ws tls client enable` and `ws compose recreate` brought it back (`HTTP/2 200`).
  - Dashboard: the Route Profiles form and the Service card were tested for ticket 21. This page names the Examples `Client Certificate` and `Client Certificate user`, which the sidebar showed. The lock icon next to a hostname was in the Catalog screenshot of the landing page.
- The gitleaks hook refuses a `curl` line with `-u user:password`. The `--cert alice.p12:<password>` lines use a placeholder.
- Not tested: a real browser (the import of the `.p12` file and the choice of the certificate), the Windows import (`Import-PfxCertificate`, from the user guide, so its tab says `(not tested)`), the Linux and macOS browser steps (from the user guide), a Client Certificate from another CA, and Yard Scope.
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.
