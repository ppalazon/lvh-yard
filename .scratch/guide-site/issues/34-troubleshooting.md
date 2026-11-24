# 34: Reference: Troubleshooting

**What to build:** A table of symptoms, causes and commands. It links to recipe 14 and to the Base Domain recipe.

**Blocked by:** 01, 24

**Status:** done

**Core for v0.2.0:** no

## The page covers

- Symptoms: a name does not resolve, a Route answers 404 or 502, a Service is Unavailable, Unhealthy or Degraded, a Remote Service is missing, an Instance is marked Out-of-range, a Workstation is unreachable, a certificate is not trusted.
- For each: the one command that shows the cause, and the fix.

## Verification

- Reproduce each symptom on `dev:up`.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Page: `docs/guide/reference/troubleshooting.md`, in `draft_docs`, nav label "Troubleshooting" in the Reference group. It is a router: one table with the symptom, the cause, the command that shows it, and the fix with a link to the page that covers it. It does not repeat the outputs of those pages. The `Troubleshooting` heading is the one finding of `ste_check.py` that the spec allows.
- Twelve rows: `Could not resolve host`, `Failed to connect`, `404 page not found`, `502 Bad Gateway`, `unavailable`, `unhealthy`, `degraded`, a Remote Service is missing, an Instance is marked out of range, `Workstation unreachable`, `LVH Yard on this Workstation: failing`, and `unable to get local issuer certificate`. The ticket lists 404 and 502, the three states, the missing Remote Service, the out-of-range Instance, the unreachable Workstation and the certificate. The last row `failing` is added, because the install page covers it.
- Reproduced on two throwaway Workstations in one Yard (removed afterwards), except the resolver case, which ticket 06 reproduced with dnsmasq:
  - A Service with no Publication: the catalog said `unpublished`, and the hostname answered `404`. After `publish`, it answered `200`.
  - A Static Instance on a closed port: the hostname answered `502`, the catalog said `unhealthy` `0/1`, and `lvh-yard test` printed `Connection refused`.
  - A published Service with no Instance: the catalog said `unavailable` and `Empty`, and the hostname answered `404`. The row says so.
  - Two Instances, one on a closed port: the catalog said `degraded` and the hostname answered `200`.
  - A Service of `ws-b` with Local Scope: the catalog of `ws-a` said `ws-b: no Services`, and `--scope every` listed it. After `publish --scope yard` it stayed missing, because its Instance was on `127.0.0.1`. `catalog --details --scope every` marked the Instance `out of range` and printed `risk: [Loopback Instances]`. The row names both causes.
  - `https://` without `--cacert` printed `unable to get local issuer certificate (20)`, and with `--cacert ca/ca.crt` answered `200`.
  - With the Traefik container stopped, `curl` printed `Failed to connect`, and `lvh-yard test` named the Route and the address at once. `lvh-yard status` said `ok` for Traefik for about three seconds, then `failing`. The row uses `test` for that reason.
  - With `ws-b` stopped, the catalog printed `ws-b: Workstation unreachable` and the `ws compose logs consul` hint.
- The anchors of the links exist in the built pages.
