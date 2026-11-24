# 28: Concepts: Instance Sources and Priority

**What to build:** The four kinds of Instance Source and the order that Instance Priority sets.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## The page covers

- A diagram of the four sources: Static Instance, Container Binding, Alias, Foreign Instance.
- The rule: the Route uses the first source with a passing Instance. With Priority off, every passing Instance shares the Route.
- Where recipes 6, 7, 9 and 10 apply it.

## Verification

Check against the glossary and ADR-0009.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- The page is `docs/guide/concepts/instance-sources-and-priority.md`, titled "Instance Sources and Priority". It is in `draft_docs`, and the group "Concepts" lists it as "Instance Sources", between "How it works" and "Scope". The stage 1 text is in `drafts/28-instance-sources-and-priority.stage1.md`. `ste_check.py` reports no findings. The page has a table of the four kinds with the command that adds each, a diagram of the order, the rules, `priority` and the dashboard, a walk down the order, and a list of the recipes that apply it.
- Checked against the glossary, ADR-0005 (superseded) and ADR-0009 (a Service keeps one order of its Instance Sources, and the Route uses the first source with a passing Instance; a new source and a new Foreign Instance go to the end; only the Home Workstation's Route uses Aliases) and the user guide section "Instance Priority".
- Tested on two throwaway Workstations in one Yard over loopback (folders `rcisa` and `rcisb`, other ports than the defaults). `ws-a` had one Service `api` with four sources: a Static Instance `127.0.0.1:4001`, a Container Binding to the container `mailpit-1` (port 8025), an Alias to `api` on `ws-b` (an Instance at the LAN address of this computer), and a Foreign Instance `foreign-1` at `127.0.0.1:4003`, registered through the HTTP API of Consul. The outputs on the page are real.
  - `priority api` listed the four sources in the order of their registration (the Foreign Instance last), with `(in use)` on the Static Instance.
  - Stopping the sources one by one (static server, then the container, then the server behind the Alias, then the Foreign server) moved `(in use)` to 2, 3 and 4, and the answers changed with it. The catalog said `degraded`, with `[docker]`, `[alias]` and `[foreign]`. With all four stopped, the hostname answered `502`, and the catalog said `unhealthy`, `0/2`.
  - `priority api --off` with all sources up: eight requests answered from all four sources in turn, and the catalog said `[static] [docker] [alias] [foreign]`.
  - Open question for the maintainer: the counter. With the Instance Priority off and four sources, the catalog said `3/3`, and `catalog --details` listed three Instances (static, container, foreign) as `routed`. The Instance of the Alias was not in the list or the count. In other states, the counter was `1/2` with the Alias in use. The page shows the real lines and says nothing about the counter. The user guide says that the counter is passing Instances over all Instances. The count of an Alias may need a line in the user guide.
- Not tested: the dashboard card (the arrows, the switch and the badge are from the user guide and the tests of the pages "Process first" and "Alias"), and the rule "an edited Static Instance keeps its place" and "a removed source leaves the order, and goes last when added again" (both are from the user guide and ADR-0009).
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.

## Review round: 502 or 404, and load balancing

- The maintainer asked what "If no Instance passes, the Route uses every Instance in range" means, and whether the Route does not work and answers 404. The answer is no: the Route exists and sends the requests to every Instance in range. A `404` means that no Route exists. Tested on a throwaway Workstation with one Static Instance and an HTTP check of 5 seconds:
  - The app answered, but `/health` answered 404, so the check failed. The catalog said `unhealthy` `0/1`, and the hostname answered `200`. `lvh-yard test` printed `No Instance passes. The Route uses every Instance in range.` and `Every check passed.`
  - The app stopped: the hostname answered `502`.
  - `unpublish`: the hostname answered `404`.
    The page now says this in the bullet, and links to the page "Find and fix a bad Route".
- The maintainer asked to describe the Instance Priority off as a load balancer, and not as sharing. The page says that the Route load balances the requests across the passing Instances (of the first source with the Priority on, and of every source with it off). The Route Diagram of the dashboard says "Round robin over the passing Instances", and the test with the Priority off alternated between the four sources. The recipe pages "Test replicas and failover" and "Process first" still say that the Instances "share" the Route. They are unchanged.
- The maintainer doubted that the Route sends the requests to every Instance when no Instance passes, and said that Traefik sends each request to one Instance, as a load balancer. The maintainer is right. Tested with two Static Instances whose HTTP checks both failed (`/health` answered 404, the apps answered): the catalog said `unhealthy` `0/2`, the Route that Traefik read (`/traefik/configuration`) had both servers in its `loadBalancer` (`http://127.0.0.1:4001` and `http://127.0.0.1:4002`), and eight requests alternated between the two apps (`app 2 | app 1 | ...`). So each request went to one Instance, and the Route balanced across all of them. When one Instance passed, the Route had only that server, and six requests answered from it. The page says "load balances the requests across every Instance in range, and each request goes to one of them".
