# Export and import Backups of a Workstation

Status: ready-for-agent

Words follow `GLOSSARY.md`. This spec needs `.scratch/route-profiles-in-kv/spec.md` done first, because a Backup holds Route Profiles and the import writes them to Consul KV.

## Problem Statement

You set up a Workstation by hand: Static Instances, Container Bindings, Publications, Aliases and Route Profiles. There is no way to save this setup. If you move to a new computer, or a Consul data volume breaks, you must set up every Service again.

You also cannot copy a setup from one Workstation to another. A teammate who wants the same Services as you must register each one by hand. You cannot keep the setup of a Workstation in a file of your own, or try a change and go back.

## Solution

A Backup is one YAML file that holds what you set up on one Workstation. It holds the Workstation's Services, each with its Publication, Static Instances and Container Bindings. It also holds the Workstation's Aliases and Route Profiles. It never holds Bound Instances, Health or Remote Services, because those come from what runs now.

You export a Backup with `lvh-yard export`. It writes to standard output, or to a file with `-o`. You import a Backup with `lvh-yard import <file>`, and `-` reads standard input.

An import has two modes. The default mode adds only new things and skips the ones that exist. With `--update`, the import also overwrites existing things with the values from the file. Before it changes anything, the dashboard makes sure that the whole file is correct. If one entry is wrong, the import changes nothing and lists each problem. With `--dry-run`, the import shows its plan and changes nothing.

Before each import that changes something, the dashboard writes a safety Backup of the current setup. The CLI and the page print its name, so you can import it to go back.

The dashboard gets a Backups page. It lists the Backups in the `backups/` folder of the Workstation Folder. From it, you can create, download, upload, delete and import a Backup. Before an import, the page shows the plan, and you confirm it.

## User Stories

In these stories, "user" is a person who uses one Workstation. "CLI user" is the same person at the terminal.

1. As a CLI user, I want `lvh-yard export` to print a Backup, so that I can save my setup anywhere.
2. As a CLI user, I want `lvh-yard export -o <file>` to write a file, so that I need no shell redirect.
3. As a CLI user, I want the file from `-o` to be readable only by me, so that hashes in it stay private.
4. As a CLI user, I want `lvh-yard import <file>` to add what is new, so that I can restore a setup.
5. As a CLI user, I want `lvh-yard import -` to read standard input, so that I can pipe a Backup from another Workstation.
6. As a CLI user, I want the default import to skip existing things, so that an import never overwrites my changes.
7. As a CLI user, I want `--update` to overwrite existing things, so that I can bring a Workstation back to a Backup.
8. As a CLI user, I want `--dry-run` to show the plan and change nothing, so that I see the result first.
9. As a CLI user, I want each plan line to say add, update or skip, so that I know what happens to each entry.
10. As a CLI user, I want a skip to give its reason, so that I know why an entry did not change.
11. As a CLI user, I want the import to print the safety Backup name, so that I can go back.
12. As a CLI user, I want a non-zero exit code for a refused file, so that scripts can stop.
13. As a user, I want a wrong entry to stop the whole import, so that a half-applied file never confuses me.
14. As a user, I want each problem to name its place in the file, so that I can fix it quickly.
15. As a user, I want a Publication to be able to name a Route Profile in the same file, so that one import is enough.
16. As a user, I want an unknown Route Profile to stop the import with its name, so that no Route fails closed.
17. As a user, I want an unknown key in the file to stop the import, so that a typo never goes unseen.
18. As a user, I want an unknown file version to stop the import, so that an old dashboard never misreads a new file.
19. As a user, I want a Backup to hold Publications with their Scope, so that Services get their hostnames back.
20. As a user, I want a Backup to hold the Instance Priority, so that Routes choose Instances as before.
21. As a user, I want a Backup to hold the Route Profile names of each Publication, so that auth comes back too.
22. As a user, I want a Backup to hold each Static Instance with its health check, tags and meta, so that nothing is lost.
23. As a user, I want a Backup to hold each Container Binding, so that my containers get Instances again.
24. As a user, I want a Backup to hold Unpublished Services, so that they come back Unpublished.
25. As a user, I want a Backup to hold my Aliases, so that my short hostnames come back.
26. As a user, I want a Backup to hold my Route Profiles, so that one file restores everything.
27. As a user, I want a Backup to leave out Bound Instances and Health, so that the file holds only what I chose.
28. As a user, I want a Backup to leave out Remote Services, so that I never import a teammate's Services as mine.
29. As a user, I want my LAN address left out of a Static Instance, so that it fits a new computer.
30. As a user, I want a Static Instance without a host to use my LAN address, so that it works after a move.
31. As a user, I want a Static Instance to match by host and port inside its Service, so that update finds it.
32. As a user, I want a Container Binding to match by its ID, so that the import never doubles it.
33. As a user, I want an Alias and a Route Profile to match by name, so that update changes the right one.
34. As a user, I want `publication: none` to unpublish a Service in update mode, so that a Backup restores that choice.
35. As a user, I want a missing `publication` key to keep the existing Publication, so that a short file changes less.
36. As a user, I want a new Service without a `publication` key to stay Unpublished, so that nothing gets a hostname by surprise.
37. As a user, I want LVH Yard's own Services in a Backup like others, so that changes to them come back too.
38. As a user, I want to import a Backup from another Workstation, so that I can copy a teammate's setup.
39. As a user, I want the plan to show the Workstation that made the Backup, so that I know where it came from.
40. As a user, I want an Alias to this Workstation to stop the import, so that no Alias points at itself.
41. As a user, I want a safety Backup before each import that changes something, so that I can go back.
42. As a user, I want no safety Backup for an import that changes nothing, so that the folder stays small.
43. As a user, I want a Backups link in the navigation bar, so that I reach the page from every page.
44. As a user, I want the Backups page to list each Backup with its date and counts, so that I can pick one.
45. As a user, I want a button that creates a Backup now, so that I can save before a risky change.
46. As a user, I want a new Backup named after my Workstation and the time, so that names sort by date.
47. As a user, I want to download a Backup from the page, so that I can keep it elsewhere.
48. As a user, I want to upload a Backup from my browser, so that I can import a file from a teammate.
49. As a user, I want an upload that is not a Backup to be refused, so that the list holds only Backups.
50. As a user, I want an upload with an existing name to be refused, so that I never lose a Backup.
51. As a user, I want to delete a Backup after I confirm, so that I can clean the folder.
52. As a user, I want to pick a mode and see the plan before an import, so that I know what changes.
53. As a user, I want to confirm the plan before the import runs, so that nothing changes by mistake.
54. As a user, I want the page to show the result and the safety Backup name, so that I can go back.
55. As a user, I want the dashboard never to delete old Backups by itself, so that I decide what to keep.
56. As a user, I want Backups to be plain files in my Workstation Folder, so that I can copy them with normal tools.
57. As a new user, I want `lvh-yard init` to create the `backups/` folder, so that the Backups page works at once.
58. As a user who upgrades, I want `lvh-yard reinit` to add the folder and its mount, so that I get Backups too.
59. As an API client, I want an endpoint that answers a Backup, so that I can script exports.
60. As an API client, I want an endpoint that imports a Backup with a mode and a dry run, so that I can script imports.
61. As an API client, I want a refusal as a problem answer that lists each problem, so that I can show them all.

## Implementation Decisions

- The dashboard does all the work: it builds a Backup, parses it, makes sure that it is correct, makes the plan and applies it. The CLI only sends or receives the YAML. The CLI, the page and the API share one path.
- The Backup format, version 1, is a YAML document with these top-level keys. Any other key is refused, at every level.
  - `version`: the number 1. Any other value is refused.
  - `exported-from`: the Workstation name and the time of the export. The import shows it and never acts on it.
  - `route-profiles`: a map of name to definition. Each definition is a middleware in Traefik's format, as under `http.middlewares`.
  - `services`: a map of Service name to an entry. An entry has `publication`, `static-instances` and `container-bindings`, each one optional.
  - `publication`: either `none`, or an object with `scope`, `priority` and `route-profiles`.
  - A Static Instance has `host`, `port`, `check`, `tags` and `meta`. `port` is required. The export leaves out empty tags and meta.
  - A Container Binding has `container` and `port`, or `compose` with `project` and `service`, and `port`.
  - `aliases`: a map of Alias name to its `workstation` and `service`.
- If `host` equals the LAN address of the exporting Workstation, the export leaves it out. On import, a missing host means the LAN address of the importing Workstation, as `lvh-yard register static` does.
- Matching for both modes:
  - A Service matches by name.
  - A Static Instance matches by host and port, inside its Service only. A missing host compares as the importing Workstation's LAN address.
  - A Container Binding matches by its ID, `<container>:<port>` or `compose:<project>:<service>:<port>`.
  - An Alias and a Route Profile match by name.
- In add mode, a match is skipped. In update mode, a match is overwritten. An overwrite changes the Publication of a Service. It changes the health check, tags and meta of a Static Instance. It changes the target of an Alias and the definition of a Route Profile. A Container Binding has nothing else to change, so a match is always a skip. A match with the same values is a skip in both modes.
- For a Service in the file, `publication: none` unpublishes it in update mode. A missing `publication` key keeps the existing Publication. A new Service without the key stays Unpublished, which differs from the `publish` default of the register endpoints.
- The import has no mode that removes things that the file does not hold.
- The checks run on the whole file before any write. They use the same rules as the API: Service and Alias names, the refused name `consul`, ports, Scope, Instance Priority, Route Profile checks, and Alias targets. A Publication can name a Route Profile from the file or one in Consul KV. An Alias whose target is this Workstation is refused. Each problem names its place in the file, for example `services.grafana.static-instances[0].port`.
- The plan is a list of entries. Each entry has the kind (route profile, Service, Publication, Static Instance, Container Binding, Alias), the name, and the action: add, update or skip. A skip has a reason.
- The import writes in this order: Route Profiles, then Static Instances and Container Bindings, then Publications, then Aliases. So a Publication never names a missing Route Profile, and a Service exists before its Publication.
- The writes go to Consul, which has no transaction across the catalog and KV. If a write fails halfway, the import stops and reports what it applied. The safety Backup is the way back.
- Before the first write of an import that changes something, the dashboard writes a safety Backup to the Backups folder, named `<workstation>-<yyyyMMdd-HHmmss>-before-import.yaml`.
- API, under `/api/workstation/backup`. The path does not name the Workstation, because only this Workstation's setup is in it.
  - `GET` answers the Backup as `application/yaml`.
  - `POST` takes a Backup as `application/yaml`, with `mode` set to `add` or `update` and an optional `dry-run`. It answers the plan. If the import wrote a safety Backup, the answer also gives its name.
  - A refused file answers a client error problem with the list of problems.
- The Backups folder is `backups/` in the Workstation Folder. `compose.yaml` mounts it read-write into the dashboard and sets `YARD_BACKUPS` to its path in the container. The read-only mount of the whole Workstation Folder goes away, because after the Route Profiles spec nothing reads it.
- `lvh-yard init` and `reinit` create the `backups/` folder before Compose starts. Otherwise Docker creates it, owned by root. The dashboard already runs as `YARD_UID:YARD_GID`, so the files that it writes belong to the user.
- The dashboard writes each Backup file with mode `0600`. `lvh-yard export -o` does the same.
- The Backups page lives at its own path with its own navigation link. It reads the folder and lists only `.yaml` and `.yml` files. It shows the name, the date from `exported-from`, and the number of Services, Aliases and Route Profiles. A file that does not parse shows its error in the list and cannot be imported.
- A new Backup from the page is named `<workstation>-<yyyyMMdd-HHmmss>.yaml`. An upload keeps its file name. The page refuses a name with a path separator, a name that exists, or a file that is not a Backup. Delete asks for a confirmation.
- The import on the page has two steps. First you pick the mode and the page shows the dry-run plan. Then you confirm, and the page shows the result and the safety Backup name.
- The Backups folder belongs to the page. The CLI has no command for it, but `lvh-yard export -o` can write into it.
- `docs/user-guide.md` explains export, import, the modes, the safety Backup, the Backups page and the secrets in a Backup.

## Testing Decisions

- A good test acts as a user or an API client. It looks only at what they can see: API answers, page text, files in the Backups folder, and CLI output.
- Seam 1 is the dashboard, with a real Consul and Docker from Testcontainers and the two-datacenter Yard. A test points `YARD_BACKUPS` at its own temporary folder.
- Seam 1 covers export: each kind of entry, `publication: none`, the left-out LAN address, empty tags and meta, and no Bound Instances or Remote Services.
- Seam 1 covers a round trip. It exports, removes everything, imports, and exports again to an equal file, apart from `exported-from`.
- Seam 1 covers import in add mode and in update mode, and each matching rule. It covers a Static Instance with the same address in two Services, and a missing `publication` key. It covers a Publication that names a Route Profile of the same file.
- Seam 1 covers each refusal and shows that a refused file changes nothing. It covers the dry run, and the safety Backup with its file mode. It shows that no safety Backup appears for an import that changes nothing.
- Seam 1 covers an import of a Backup made on the remote Workstation, and an Alias to this Workstation that is refused.
- The page tests cover the Backups page: the list, create, download, upload and its refusals, delete with confirmation, the two-step import, and the navigation link.
- The OpenAPI test covers the new endpoints.
- Seam 2 is the CLI test with its stub dashboard. It covers `export` to standard output and to `-o` with mode `0600`, and `import` from a file and from `-`. It covers `--update`, `--dry-run`, the plan lines, the safety Backup name and the exit codes. It also covers `init` and `reinit`, which create `backups/` and write the mount.
- Prior art: `StaticInstancesTest`, `ContainerBindingsTest`, `PublicationsTest`, `AliasesTest`, `RouteProfilesTest`, `RemoteServicesTest`, `AliasesPageTest`, `NavigationBarTest`, `OpenApiTest`, and the CLI's `YardTest`.

## Out of Scope

- An import mode that removes things that the file does not hold.
- An export of only some Services. The format leaves room for a filter later.
- Automatic Backups on a schedule, and automatic removal of old Backups.
- CLI commands for the Backups folder.
- Changing hosts or ports on import, for example to fit another computer, apart from the LAN address rule.
- Encryption of Backup files. Basic-auth hashes in a Backup stay readable by whoever can read the file.

## Further Notes

- A Backup holds basic-auth hashes from Route Profiles. Treat a Backup like any file with secrets in it.
- The word "import" is free for this feature: the old one-click Import on the Containers page became `register container`.
- `GLOSSARY.md` has the Backup entry. A safety Backup is an ordinary Backup, so it has no entry of its own.
