---
status: accepted
---

# Consul reads files, Traefik keeps flags, and files are owned by name

Consul reads its fixed options and its TLS from a `-config-dir` folder in the Workstation Folder. Traefik has no `traefik.yaml`: it keeps its install options as flags in `compose.yaml`, and reads its certificates, TLS options and client-certificate rules from a watched file-provider folder. Traefik takes its install configuration from one source only, a file, flags or environment variables. A file would make it ignore the flags that carry `YARD_*` values, such as the bind addresses and ports. TLS belongs to its routing configuration, so it needs no install file.

LVH Yard owns the files it generates, and the file name says so: `consul.d/00-lvh-yard.hcl`, `consul.d/10-tls.hcl` and `traefik/dynamic/10-tls.yaml`. Each starts with a "generated, do not edit" line. You own `compose.yaml`, `.env`, `lvh-yard.yaml` and any other file you add to those folders. Consul and Traefik read their folders in name order, so your files come after the generated ones.

## Considered Options

- **A `traefik.yaml` for the fixed options, and flags or environment variables for the rest**: rejected. Traefik ignores the second source.
- **A `traefik.yaml` that the CLI renders from `.env`**: rejected. `.env` would stop being the one place to change a value, and a hand edit of `.env` would do nothing until the next render.
- **One file per tool that you and LVH Yard both edit**: rejected. LVH Yard would have to parse your edits and keep your comments.

## Consequences

- `ws upgrade` rewrites the three generated files, so a new Consul or Traefik option reaches an existing Workstation. It still edits only the image lines of `compose.yaml`, byte for byte.
- A change to one of the generated files by hand is lost at the next `ws upgrade` or `ws tls` command. To keep a change, put it in a file of your own.
- Consul runs as `YARD_UID:YARD_GID`, so it reads the files with mode 600 in the folder with mode 700. Its data is in `./consul-data` in the Workstation Folder.
