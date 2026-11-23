# 20: Recipe 19: A real HTTPS certificate on your own domain

**What to build:** Write the recipe "Recipe 19: A real HTTPS certificate on your own domain" with the recipe template of the spec.

**Blocked by:** 01, 06, 19

**Status:** done

**Core for v0.2.0:** yes

## Pain

"I own a domain, and I want HTTPS that every browser trusts with no CA to install."

## Result

Traefik serves a public certificate for `*.<own domain>`, and every browser trusts it.

## Commands to use

- Get a wildcard certificate for the Base Domain through DNS-01. Name the method, and say that the page does not teach one tool.
- `lvh-yard ws tls https import --ca ... --cert ... --key ...`, then `enable`.
- A second wildcard for each remote Workstation (`*.ws-b.<Base Domain>`), as the user guide says.

## Diagram idea

A diagram of the certificate chain: a public CA signs the wildcard, Traefik shows it, the browser trusts it.

## Verification

Test only the `import` step, with a certificate from a test CA. Label the issuance step "not tested by the maintainer" until someone runs it with a real domain. Do not invent steps for a DNS provider.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/recipes/https/real-certificate.md`, after "Test HTTPS with your Workstation CA" in the nav.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. See "Draft notes".
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Setup: a throwaway Workstation in `/tmp` (folder `rc20w`, other ports than the defaults, `ws set https-port 18443`) with the Base Domain `dev.example.com`, which needs no DNS for the import step. A test CA (`openssl`) signed a wildcard certificate for `*.dev.example.com` and `*.ws-b.dev.example.com`, with the whole chain in `fullchain.pem`. A Static Instance `app` ran on `127.0.0.1:4000`. The Workstation, the files and the listener are removed.
- Tested: `ws tls https import --ca testca.crt --cert fullchain.pem --key privkey.pem` imported the certificate, `ws compose recreate` applied it, and `curl --resolve ... --cacert testca.crt https://app.dev.example.com:18443/` answered `HTTP/2 200`. `openssl s_client` showed the chain of two certificates. Importing the same files a second time worked, so the renewal sentence is true. `--ca` is the only difference from the command on the page.
- Failures reproduced with the exact messages on the page: a certificate of a test CA without `--ca` (`certificate is valid neither for the given CA nor for a public CA: x509: certificate signed by unknown authority`), a certificate for `*.example.test` (`certificate needs yard.dev.example.com, the address of the Yard Route: ...`), and the key of another certificate (`certificate and key do not match or are invalid: tls: private key does not match public key`).
- Not tested, and nobody has run it with a real domain: the issuance of the certificate, the DNS-01 challenge, and that a browser or `curl` without `--cacert` trusts a public certificate. The Check of the page is therefore the command without `--cacert`, with the output that the test gave with it. The sentence about DNS-01 and the TXT record under `_acme-challenge` is general knowledge about ACME, not a step of a tool. The page names no tool and no DNS provider.
- The page has the comment `<!-- base-domain: off -->`, because its examples use `dev.example.com`, and the Base Domain field of the header would change `<Base Domain>` and `lvh.me` in a page about an own domain. It has none of them.
- The command `ws tls https import` prints the line `Browsers: import ca/ca.crt as a trusted authority when the Workstation CA signed the certificate.` after an import too. The page does not show it. It does not apply to a public CA.
