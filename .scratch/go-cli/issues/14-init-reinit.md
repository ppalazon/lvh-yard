# 14: `init --reinit`

Status: ready-for-agent

Blocked by: 12

Spec: `../spec.md`.

## What to build

`lvh-yard init --reinit` writes `compose.yaml` and `.env` again from the new template, and keeps what the user set. Settings are taken in this order: the template, the uncommented settings of the old `.env`, what `init` finds, then the flags. What `init` finds fills only empty settings. The old files are moved to `.bak` only after every flag check, so a usage error changes nothing. This is how a Workstation joins a Yard later, with `--peers` and `--gossip-key`. Follow `Reinit.java`.

Port the cases of `InitTest` that test `--reinit`.

## Acceptance criteria

- [ ] After `--reinit --peers ... --gossip-key ...`, the `.env` keeps the old Workstation name and Base Domain and has the new Peer and Gossip Key.
- [ ] An old empty Docker group setting does not wipe the group that `init` finds.
- [ ] A bad flag exits 1 and leaves the old files and no `.bak`.
- [ ] `mise run check` passes.
