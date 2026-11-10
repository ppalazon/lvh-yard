# 13: `init --upgrade`

Status: done

Blocked by: 12

Spec: `../spec.md`.

## What to build

`lvh-yard init --upgrade` moves the images of consul, traefik and dashboard in an existing `compose.yaml` to the versions of this CLI, and changes nothing else. It reads the file line by line and swaps only the image value, so indent, quotes, trailing comments and hand edits stay byte for byte. When one of the three has no `image:` line, nothing is written and the exit code is 1. `--force`, `--upgrade` and `--reinit` exclude each other. Follow `Upgrade.java` and `ComposeImages.java`.

Port the cases of `InitTest` that test `--upgrade`.

## Acceptance criteria

- [ ] After `--upgrade`, a file with hand edits differs from before only on the three image lines.
- [ ] A file that already has this version's images says so and is not changed.
- [ ] A missing image line gives an error, writes nothing and exits 1.
- [ ] `--upgrade` with `--force` or `--reinit` is refused.
- [ ] `mise run check` passes.
