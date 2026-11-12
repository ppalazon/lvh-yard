Status: done

# The `lvh-yard ws` command, config files and TLS

Decisions are in [ADR-0011](../../docs/adr/0011-consul-files-traefik-flags-and-owned-files.md), [ADR-0012](../../docs/adr/0012-services-keep-host-networking.md) and [ADR-0013](../../docs/adr/0013-one-yard-ca-and-one-certificate-per-workstation.md). The terms Yard CA, Workstation Certificate and Client Certificate are in `GLOSSARY.md`.

## Problem Statement

A Workstation's setup is one `compose.yaml` with long shell commands, and a `.env`. To change a setting, I edit `.env` by hand. To change the Yard I join, I run `lvh-yard init --reinit`, which rewrites my files. I cannot start, stop or read the logs of Consul, Traefik and the dashboard without knowing the `docker compose` command and the folder.

There is no way to protect a Yard. Consul talks between Workstations without TLS, Traefik serves only HTTP, and a Route cannot ask for a Client Certificate. Adding any of this by hand means editing shell commands inside `compose.yaml`, which I own and which a new version cannot change for me.

On a shared host that nobody uses directly, I need Consul and the dashboard to publish Instances to the Yard, but I do not want Traefik. Today I cannot turn it off.

## Solution

A new `lvh-yard ws` group changes one Workstation's files and runs its services. `ws init` replaces `lvh-yard init`, and `ws upgrade` replaces `lvh-yard init --upgrade`. `ws show`, `ws set` and `ws join` change the settings without hand edits. `ws start`, `ws stop`, `ws restart` and `ws logs` run `docker compose` for me and print the line they run.

The fixed options of Consul move out of `compose.yaml` into files in the Workstation Folder. Traefik keeps its flags and reads certificates and TLS options from a watched folder. The dashboard gets a `lvh-yard.yaml` file of its own.

`ws tls` makes or imports a Yard CA and a Workstation Certificate. With them, Consul checks every Workstation it talks to, and Traefik serves each Route over HTTPS as well as HTTP. A Route Profile can require a Client Certificate, and `ws tls client create` gives a person one. A Traefik profile in Compose lets me leave Traefik out.

## User Stories

1. As a new user, I want `lvh-yard ws init` to ask only for the Workstation name and the Base Domain, so that a first setup takes seconds.
2. As a user in a script, I want `ws init --workstation <name> --base-domain <domain>` to work without a terminal, so that I can set up a Workstation in automation.
3. As a user without a terminal, I want `ws init` to fail and name the missing flag, so that a pipe never hangs.
4. As a user, I want `ws init` to refuse to overwrite my files unless I give `--force`, so that I never lose a hand edit.
5. As a user, I want `ws init` to offer my hostname as the Workstation name, so that I can press Enter.
6. As a user, I want `ws init` to make a Gossip Key for a new Yard and to take `--gossip-key` when I join one, so that the first Workstation and the next ones use one command.
7. As a user, I want `ws init` to take `--peers`, so that a new Workstation joins its Yard at once.
8. As a user, I want `--dir` on every `ws` command, so that I can work on a Workstation Folder other than the default one.
9. As a user, I want the Workstation Folder to be readable only by me, so that no other user of the computer reads my Gossip Key or my certificate keys.
10. As a user, I want `ws init`, `ws upgrade` and `ws set` to fix the modes of a folder from an older version, so that I do not have to run `chmod`.
11. As a user, I want the dashboard image in `compose.yaml` to match the version of `lvh-yard` that wrote it, so that I run what I installed.
12. As a user, I want `ws init` to write `lvh-yard.yaml` with every line commented out, so that I see the dashboard's defaults and change only what I need.
13. As a user, I want the dashboard to read `lvh-yard.yaml` from the Workstation Folder, so that structured settings have a home next to `.env`.
14. As a user, I want `.env` to stay the only place for per-Workstation values, so that I never wonder which file wins.
15. As a user, I want `ws upgrade` to change only the image lines of my `compose.yaml`, byte for byte, so that my hand edits stay.
16. As a user, I want `ws upgrade` to rewrite the files that LVH Yard generates, so that a new Consul or Traefik option reaches my Workstation.
17. As a user, I want `ws upgrade` to name each service and its image, and to say when it is already current, so that I see what changed.
18. As a user, I want `ws upgrade` to change nothing and fail when `compose.yaml` has no image line for a service, so that I never get a half-upgraded file.
19. As a user, I want files that I add to `consul.d/` or `traefik/dynamic/` to survive `ws upgrade`, so that I can extend the setup.
20. As a user, I want a "generated, do not edit" line at the top of each generated file, so that I know which files LVH Yard owns.
21. As a user, I want `ws show` to print the settings of this Workstation, with the Gossip Key hidden, so that I can paste the output in a chat.
22. As a user, I want `ws set base-domain <domain>` to check the domain and change one line of `.env`, so that my comments stay.
23. As a user, I want `ws set` to refuse an invalid value and say what is valid, so that a typo never breaks my Workstation.
24. As a user, I want `ws set peers <list>` to take spaces or commas, so that I paste the list as I have it.
25. As a user, I want a `ws set` name for each address, port and option that I may change, so that I never edit `.env` by hand.
26. As a user, I want `ws set` to refuse the Workstation name and to tell me how to rename, so that I do not break my hostnames by mistake.
27. As a user, I want `ws set` to name the recreate or stop command that applies the setting, so that I know the change is not live yet. `ws restart` remains `docker compose restart` and does not apply new environment settings.
28. As a user, I want `ws join --gossip-key <key> --peers <list>` to move this Workstation to another Yard, so that I do not rewrite files.
29. As a user, I want `ws join` to check the Gossip Key, so that a bad key never reaches `.env`.
30. As a user, I want no `ws rename`, and a message that says export a Backup, run `ws init` under the new name and import it, so that I keep my Services.
31. As a user, I want `ws start` to run `docker compose up -d` in my Workstation Folder from any directory, so that I do not `cd` first.
32. As a user, I want `ws stop` and `ws restart` to do the same, so that I manage all three services together.
33. As a user, I want `ws logs` to show the logs of all services, or of one: `consul`, `traefik` or `dashboard`, so that I find a problem fast.
34. As a user, I want `ws logs -f` to follow the logs, so that I watch a service while I test.
35. As a user, I want `ws logs` to refuse a name that is not a service and to list the valid names, so that a typo is clear.
36. As a user, I want each lifecycle command to print the `docker compose` line it runs, so that I learn the command and can run it myself.
37. As a user, I want the exit code of `docker compose` to be the exit code of `ws`, so that scripts see failures.
38. As a user, I want a clear message when `docker` is not on my PATH, so that I know what to install.
39. As a user, I want `docker compose up -d` in the folder to keep working exactly like `ws start`, so that I am never tied to the CLI.
40. As a user, I want Consul to run as my user and group, so that it can read my private files in a folder that only I can read.
41. As a user, I want Consul's data in a folder inside the Workstation Folder, so that it is protected like my other files.
42. As a user, I want `ws tls ca create` to make a Yard CA on one Workstation, so that I do not need `openssl`.
43. As a user, I want the CA key to stay in a folder that no container mounts, so that a compromised service cannot sign certificates.
44. As a user, I want `ws tls ca create` to refuse when a CA exists unless I give `--force`, so that I never replace my Yard's CA by accident.
45. As a user, I want `ws tls cert create --workstation <name>` to make a Workstation Certificate with the names `server.<name>.consul`, `localhost`, the addresses I give, and `*.<Base Domain>`, so that Consul and Traefik both accept it.
46. As a user, I want `--base-domain` and `--address` on `ws tls cert create`, so that I can sign for another Workstation with its own Base Domain and address.
47. As a user, I want the certificate and key of another Workstation written to a folder that I copy to it, so that its CA key never leaves my computer.
48. As a user, I want `ws tls import --ca <file> --cert <file> --key <file>` to take a CA and a certificate from outside, so that I can use my company's CA.
49. As a user, I want `ws tls import` to refuse a key that does not match its certificate, a certificate that the CA did not sign, and a certificate without `server.<this Workstation>.consul`, so that Consul never fails after a restart.
50. As a user, I want `ws tls import` to turn on Consul TLS and Traefik HTTPS for this Workstation, so that I run one command.
51. As a user, I want a warning from `ws tls import` that every Workstation of the Yard needs TLS, so that I do not cut a Workstation off.
52. As a user, I want the keys written with mode 600, so that only I read them.
53. As a user, I want `ws tls` to print how to make a browser trust my Yard CA, so that I know what is left to do.
54. As a browser user, I want every Route to answer on HTTP and on HTTPS once TLS is on, so that nothing that worked before breaks.
55. As a user, I want no automatic redirect from HTTP to HTTPS, so that a browser that does not trust the CA yet still works.
56. As a user, I want a Route Profile that requires a Client Certificate from my Yard CA, so that only holders of one reach a Publication.
57. As a user, I want a Route with that profile to answer on HTTPS only, so that it never falls back to an open HTTP Route.
58. As a user, I want a Workstation without HTTPS to give no Route to a Publication that needs a Client Certificate, so that the rule never fails open.
59. As a user, I want a Service Warning of level error when my own Workstation cannot serve my Publication with that profile, so that I see the problem on the Service.
60. As a user, I want `ws tls client create <name>` to write a `.p12` file for my browser, so that I can install it.
61. As a user, I want `--password` on `ws tls client create`, and a random password that the command prints when I give none, so that the file is never open.
62. As a user, I want the Route Profiles page, the API, and `lvh-yard profiles` to accept the client-certificate profile, so that I use the tool I like.
63. As a user, I want a Backup to hold the client-certificate profile, so that an import brings it back.
64. As a user, I want my version 2 Backups to import as before, so that old Backups stay useful.
65. As a user, I want a `traefik` profile in Compose, so that `docker compose up -d` can start only Consul and the dashboard.
66. As a user on a shared host, I want `ws set traefik off` to remove the `traefik` profile and to keep my other profiles, so that Traefik does not start.
67. As a user, I want `ws set traefik on` to put it back, so that the change is reversible.
68. As a user, I want the dashboard to know when Traefik is off, so that Status does not show a failing Traefik for ever.
69. As a user, I want the Reachability test through a Route to say "no Traefik on this Workstation", so that I do not read a connection error.
70. As a user, I want the first setup to skip `traefik-admin` when Traefik is off, so that I do not get a Service that cannot answer.
71. As a user, I want `ws set traefik off` to print `lvh-yard remove traefik-admin` for a Workstation that already has the Service, so that I can clean it up.
72. As a user, I want a dashboard that runs from the sources to read the same Traefik setting, so that it behaves the same outside Compose.
73. As a user, I want `ws` to work when the dashboard is down, so that I can repair a Workstation.
74. As a maintainer, I want the template files in one folder, and one task that embeds them in the CLI, so that no file has two sources.
75. As a maintainer, I want `mise run dev:up` to make two test Workstation Folders with the real `ws` commands, so that development uses the same path as a user.
76. As a maintainer, I want the old `lvh-yard init` removed with no alias, so that there is one way to do it.

## Implementation Decisions

**The `ws` group**

- `ws` is a subcommand group of the one `lvh-yard` binary, not a second binary. It never calls the dashboard. It works on files and on `docker compose` only.
- `--dir` is one persistent flag of `ws`. The default folder is the one the CLI already finds: `YARD_CONFIG_DIR`, then `XDG_CONFIG_HOME/lvh-yard`, then `~/.config/lvh-yard`.
- Subcommands: `init`, `upgrade`, `show`, `set`, `join`, `start`, `stop`, `restart`, `logs`, and `tls` with `ca create`, `cert create`, `import` and `client create`. There is no `rename` and no `enable`.
- The top-level `init` goes away with `--upgrade` and `--reinit`. No alias stays. Every mention moves to `ws init` and `ws upgrade`: the help text, error messages, shell completions, the compose and env comments, the install flow, the user guide, the README and the landing page.
- `ws init` keeps `--force`, `--workstation`, `--base-domain`, `--peers`, `--gossip-key` and the hidden test flags. Its questions, its checks and its Gossip Key rules do not change.
- `ws set` takes a name from a fixed list and the value. Each name maps to one setting of `.env`. The list covers every setting a person chooses: Base Domain, Peers, the three bind addresses, the ports, the out-of-range ranges, the stack-trace flag, the HTTPS port, and `traefik`. It leaves out the Workstation name and the Gossip Key. It checks the value with the rules `init` already uses, and edits one line without touching the comments. It never restarts anything and prints the next step.
- `ws join` sets the Gossip Key and optionally the Peers. It never touches the Workstation name.
- The lifecycle commands run `docker compose` with the Workstation Folder as the project directory, and print the line first. `start` runs `up -d`. `logs` takes an optional service from `consul`, `traefik` and `dashboard`, and `-f`. They need a new method on the CLI's process seam that runs a command and returns its exit code.

**Files and modes**

- `ws init` writes `compose.yaml`, `.env`, `lvh-yard.yaml`, `consul.d/00-lvh-yard.hcl`, an empty `traefik/dynamic/` folder, `backups/` and `consul-data/`. All folders are mode 700 and all files are mode 600, so the Workstation Folder is readable only by its owner. The tools create the folders before Compose starts, so that Docker does not create them as root.
- `ws upgrade`, `ws set` and the `ws tls` commands fix the modes of an existing folder when they run.
- LVH Yard owns `consul.d/00-lvh-yard.hcl`, `consul.d/10-tls.hcl` and `traefik/dynamic/10-tls.yaml`. Each starts with a "generated, do not edit" line. The user owns `compose.yaml`, `.env`, `lvh-yard.yaml` and every other file in those folders. `ws upgrade` rewrites only the generated files and the image lines.
- The template files live in a `config/` folder at the repository root. One mise task copies them into the CLI, as it does today for `compose.yaml` and `.env.example`. The check task lists them as sources. A unit test still compares the copies byte for byte.
- A `dev/` compose file layers the dashboard `build` on the template, for people who build the image through Compose. The test Workstations of `mise run dev:up` are `dev/ws-a/` and `dev/ws-b/`, made with `ws init` and `ws set`. The old `dev/*.env` files go away. The dev folders are git-ignored.

**Compose**

- All three services keep `network_mode: host` ([ADR-0012](../../docs/adr/0012-services-keep-host-networking.md)). Consul does not get DNS for Traefik.
- Consul runs as `YARD_UID:YARD_GID`, with the data folder and `consul.d/` mounted from the Workstation Folder. `ws init` always fills `YARD_UID` and `YARD_GID`. The Consul shell entry point stays, and the fixed options leave it for `consul.d/00-lvh-yard.hcl`. Values that depend on `.env` stay as flags.
- Traefik keeps its flags, because it takes its install configuration from one source only ([ADR-0011](../../docs/adr/0011-consul-files-traefik-flags-and-owned-files.md)). It adds the file provider on `traefik/dynamic/` with watch on. Its service has the profile `traefik`. The dashboard dependency on Traefik stays optional.
- HTTPS is off until the Workstation has a Workstation Certificate. The setting for the HTTPS port is empty by default. `ws tls import` sets it to 443. When it is set, Traefik gets a `websecure` entry point on the Traefik bind address and that port.
- The dashboard container mounts `lvh-yard.yaml` read-only where Spring Boot looks for an external config file. Compose keeps its explicit `environment:` list and adds the Traefik setting and the HTTPS port. It does not use `env_file`, because that would give the Gossip Key to the dashboard.
- `ws init` sets `COMPOSE_PROFILES` to `dashboard,traefik`.

**Traefik off**

- A new dashboard setting says whether Traefik is on. It is true by default. `ws set traefik on|off` writes it, and adds or removes `traefik` in `COMPOSE_PROFILES`, in the same step. Other profiles stay.
- When it is off: Status leaves out the Traefik Status Part, so it cannot fail. A Reachability test through a Route answers that this Workstation has no Traefik. The first setup does not register `traefik-admin`. The other two first-setup Services are unchanged. The setup marker rules of [ADR-0006](../../docs/adr/0006-lvh-yard-sets-up-its-own-services-once.md) stay.
- A Workstation that is off still publishes its Instances. Other Workstations still route to them with their own Traefik.

**TLS**

- The Yard CA and the keys are ECDSA P-256. The CA lasts 10 years and a certificate lasts 1 year, and `--days` changes each. [These defaults were not discussed. See Further Notes.]
- `ws tls ca create` writes the CA certificate and key. The CA key goes to a folder that no container mounts, with mode 600. `ws tls cert create` needs that key, so it works only on the Workstation that has it.
- `ws tls cert create --workstation <name>` writes one certificate and key, with the CA certificate, to a folder named for the Workstation. With no `--workstation`, it signs for this Workstation and installs the files. Its names are `server.<name>.consul`, `localhost`, `127.0.0.1`, each `--address` given, and `*.<Base Domain>`. `--base-domain` defaults to this Workstation's. The certificate has the server and the client key usage.
- `ws tls import` checks the key against the certificate, the certificate against the CA, and the Consul name against this Workstation's name. It copies the files into the Workstation Folder, writes `consul.d/10-tls.hcl` and `traefik/dynamic/10-tls.yaml`, and sets the HTTPS port.
- `consul.d/10-tls.hcl` makes Consul require TLS and a verified certificate for RPC with other Workstations, and check the server name against the datacenter. The HTTP API of Consul stays plain HTTP on loopback, so the dashboard needs no TLS to Consul.
- `traefik/dynamic/10-tls.yaml` gives Traefik the Workstation Certificate, a default TLS option, and one named TLS option that requires a Client Certificate signed by the Yard CA. That name is a fixed contract between this file and the dashboard.
- `ws tls client create <name>` signs a Client Certificate with the Yard CA key and writes a PKCS#12 file. The key usage is client only.
- TLS on Consul is Yard-wide. A Workstation with TLS and one without cannot federate.
- Explicit `--address` DNS names may use a whole leftmost wildcard, such as `*.ws-b.lvh.me`, to cover nested remote Route names. Defaults stay unchanged. Add a wildcard for each remote Workstation whose Routes this Workstation serves, and replace the certificate when another Peer needs coverage. Hostname verification stays on.

**Dashboard**

- With an HTTPS port set, a Route gets two Traefik routers: one on `web`, and one on `websecure` with TLS. A router with TLS serves only TLS, so one router cannot do both. With no HTTPS port, nothing changes.
- A Route Profile today is one Traefik middleware. A new kind has no middleware: it requires a Client Certificate. It is written in the profile's YAML with its own reserved key. LVH Yard does not give it to Traefik as a middleware. It makes the Route's `websecure` router use the named TLS option and drops the `web` router. A chain can name it, and the Route then needs the certificate.
- The Route Profiles page, the profile API, `lvh-yard profiles` and its profile files accept the new kind. The OpenAPI file is updated, and the CLI parity test follows it.
- A Workstation without an HTTPS port builds no Route for a Publication that needs a Client Certificate. On the Home Workstation, the Service Warning for this is an error.
- Backups move to version 3, which can hold the new kind. Version 2 files still import.
- The first setup, Status and Reachability follow the Traefik-off rules above.

## Testing Decisions

A good test checks what a user or another program can see: the files that a command writes, their modes, the output, the exit code, the answers of an HTTP call, and the properties of a certificate. It does not check the text of a template beyond the values that are a contract: the setting names, the service names, and the fixed TLS option name. Edge cases go in unit tests, and one integration test proves the parts work together ([ADR-0008](../../docs/adr/0008-unit-component-and-integration-tests.md)).

There are three seams. Two exist already.

1. **The CLI harness**: the command harness with a temp Workstation Folder and a fake process. The process seam gets one new method that runs a command, and the fake records the line and returns an exit code. Cases:
   - `ws init`: files, folder 700, files 600, the questions, the flags, `--force`, and the version of the image.
   - `ws upgrade`: image lines only for `compose.yaml`, generated files rewritten, user files in `consul.d/` and `traefik/dynamic/` kept, a refusal with no image line, and modes of an old folder fixed.
   - `ws show`: the Gossip Key hidden.
   - `ws set`: valid and invalid values for each kind of name, comments kept, `traefik` toggling `COMPOSE_PROFILES` and the dashboard setting together, other profiles kept, and a refusal of the Workstation name with the rename hint.
   - `ws join`: the key check.
   - The lifecycle commands: the recorded line with the project directory, the service names, the exit code, and a missing `docker`.
   - `ws tls`: a CA, a certificate, a client certificate and an import, read back with `crypto/x509` (the chain, the names, the key usage, the validity), a PKCS#12 file that decodes, the refusals of `import` (wrong key, wrong CA, wrong name), `--force`, key modes, and the CA key outside any mounted folder.
   - The removal of `init`, and the help and completions of `ws`.
     Prior art: `init_test.go`, `init_upgrade_test.go`, `init_reinit_test.go` and `completions_test.go`. The tests of `init` and `--reinit` move to `ws init` and `ws set` or `ws join`.
2. **The dashboard component test**: HTTP with the in-memory Docker and Consul fakes. Cases:
   - Traefik off: Status, Reachability and the first setup, including `traefik-admin`.
   - HTTPS on: a Route has the two routers, and the Route Profile with a Client Certificate gives one `websecure` router with the named TLS option.
   - A chain that names the new kind.
   - No Route without an HTTPS port, and the error Service Warning.
   - A profile with the new kind saved through the page, the API and the profile files.
   - A Backup of version 3 exported and imported, and a version 2 Backup imported.
     Prior art: the existing Route Profile, Backup, Status and Reachability component tests. Unit tests, as in the ADR, cover the profile checks and the Route choice.
3. **One integration test**: real Docker, Consul and Traefik, with the `lvh-yard` binary. It makes two test Workstations with `ws init`, makes the CA and the certificates with `ws tls`, and starts both with `docker compose up`. It checks:
   - the two Consuls federate with TLS;
   - a Route answers on HTTPS with a certificate that the Yard CA signs;
   - a Route with the Client Certificate profile refuses a request without a certificate, and accepts one with a certificate from `ws tls client create`;
   - with `traefik` off, Consul and the dashboard run and Traefik does not.
     Prior art: `TraefikAdminIT` and the integration tests of the test Yard.

## Out of Scope

- TLS from Traefik to Instances, with the Yard CA or a client certificate. It comes later.
- `ws enable` and any systemd unit. `restart: unless-stopped` covers a reboot for now.
- A DNS name of Consul for Traefik. See ADR-0012.
- A compose network in place of host networking. See ADR-0012.
- A redirect from HTTP to HTTPS.
- A way to make a browser or an operating system trust the Yard CA. `ws` only prints how.
- Renewing or revoking certificates. A new certificate is a new `ws tls cert create` and `ws tls import`.
- A `ws rename`.
- IPv6 addresses, Podman, and Vault for Route Profile secrets.
- Migration of data from older Workstation Folders. The app is not published yet.

## Further Notes

- The documents to update with the change are `docs/user-guide.md` (install, `ws`, TLS, Traefik off, the new Route Profile kind and Backup version 3), `docs/development.md` (the `config/` folder, `dev:up`, the template task and the sources of the check task), the README and the landing page where they show `lvh-yard init`, and `.env.example`.
- The learning on `init --upgrade` editing only image lines is stale after this change. Replace it with `trail note --replaces`.
- The key sizes and the validity periods are the spec author's choice. Change them if you want others.
- The new kind of Route Profile is the spec author's way to fit a client-certificate rule into a model that holds one middleware. It is the part most likely to change.
- The PKCS#12 file needs a new Go dependency, because the standard library cannot write one.
- Consul TLS is Yard-wide. Until every Workstation has it, a Workstation with TLS sees the others as Unreachable Workstations. The user guide must say to turn it on everywhere in one go.
