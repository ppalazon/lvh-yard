# Route Profiles live in Consul KV, and you edit them from the dashboard and the CLI

Status: done

Words follow `GLOSSARY.md`. This spec follows ADR 0004 as rewritten on 2026-11-05. It replaces the `route-profiles.yaml` parts of `.scratch/route-profiles/spec.md`. The Backups spec in `.scratch/backups/spec.md` comes after this one and needs it.

## Problem Statement

You write the Route Profiles of a Workstation in `route-profiles.yaml`, in the Workstation Folder. The dashboard polls that file and copies each valid profile into the Consul KV of the Workstation. Only the dashboard reads Consul KV, so the file is a second copy that must stay in step with the first.

The file brings state that you must understand. When the file breaks, the dashboard keeps the last good profiles and shows an error. A refused profile stays in the list with its error, but never reaches Consul. The CLI prints "These Route Profiles, read before, still apply".

You cannot change a Route Profile from the dashboard or from the CLI. You must open a text editor on the Workstation. A planned Backup import also cannot write Route Profiles, because the dashboard sees the Workstation Folder as read-only.

## Solution

Consul KV holds the only copy of each Route Profile. The dashboard no longer reads `route-profiles.yaml`. You add, change and remove Route Profiles through the dashboard of their Home Workstation, from a new Route Profiles page or from the CLI. A Route Profile keeps Traefik's own format.

On each save, the dashboard checks the Route Profile. If the profile does not work on every Workstation, the dashboard refuses the save and changes nothing. So Consul KV holds only valid profiles, and there is no "last good" copy and no stored error.

The Route Profiles page lists each profile with its name, its Traefik type, and the Services whose Publications name it. A form has a name field and a YAML text box. A "new profile" button fills the text box with an example.

The CLI gets five commands:

- `lvh-yard profiles` lists the Route Profiles.
- `lvh-yard profiles show <name>` prints one in Traefik's file format.
- `lvh-yard profiles set <file>` adds or replaces the profiles in a file in Traefik's format. `-` reads standard input.
- `lvh-yard profiles edit <name>` opens one in `$EDITOR`. After the editor closes, the command saves the profile.
- `lvh-yard profiles remove <name>` removes one.

If a Publication names a Route Profile, the dashboard refuses to remove that profile and names the Services. `lvh-yard init` no longer writes `route-profiles.yaml`.

## User Stories

In these stories, "user" is a person who uses one Workstation through its dashboard. "CLI user" is the same person at the terminal.

1. As a user, I want one page with all Route Profiles, so that I know what a Publication can name.
2. As a user, I want the list to show each Traefik type, so that I can tell basic auth from headers.
3. As a user, I want the list to show the Services that use each profile, so that I know what a change affects.
4. As a user, I want to add a Route Profile from the dashboard, so that I need no text editor on the Workstation.
5. As a user, I want to write the profile as YAML in Traefik's format, so that I can copy Traefik's examples.
6. As a user, I want a "new profile" button that fills in an example, so that I start from a working profile.
7. As a user, I want examples for basic auth, an IP allow list, headers, CORS and a chain, so that common cases need no search.
8. As a user, I want to change a Route Profile from the dashboard, so that I can fix it quickly.
9. As a user, I want a change to reach every Workstation that routes the Publication, so that protection is the same everywhere.
10. As a user, I want a change to reach my Aliases too, so that an Alias never skips the auth.
11. As a user, I want a profile that reads a file to be refused, so that it never breaks elsewhere.
12. As a user, I want a plugin profile to be refused, so that it never breaks on a Workstation without the plugin.
13. As a user, I want `forwardAuth` to an Out-of-range address to be refused, so that no Workstation sends requests that cannot arrive.
14. As a user, I want a chain that names a missing profile to be refused, so that a chain always works.
15. As a user, I want YAML that does not parse to be refused with its place, so that I can fix it.
16. As a user, I want an unknown middleware type to be refused, so that Traefik never gets a broken configuration.
17. As a user, I want a refused save to change nothing, so that a mistake never removes a working profile.
18. As a user, I want the form to keep my text after a refusal, so that I do not type it again.
19. As a user, I want a name that Traefik does not accept to be refused, so that every profile reaches Traefik.
20. As a user, I want to remove a profile that nothing uses, so that old profiles do not pile up.
21. As a user, I want removal of a profile in use to be refused with the Services named, so that no Route fails.
22. As a user, I want removal of a profile that a chain names to be refused with the chain named, so that the chain works.
23. As a user, I want to rename by adding, moving the Publications, then removing, so that no Route names a missing profile.
24. As a user, I want a Route Profiles link in the navigation bar, so that I reach the page from every page.
25. As a user, I want the Service page to link each named profile, so that I can read the profile behind a Publication.
26. As a CLI user, I want `lvh-yard profiles` to show types and users, so that I see what the page shows.
27. As a CLI user, I want `profiles show` to print Traefik's file format, so that I can save and apply it again.
28. As a CLI user, I want `profiles set <file>` to add or replace each profile in it, so that I can keep my own file.
29. As a CLI user, I want `profiles set -` to read standard input, so that I can pipe profiles from another command.
30. As a CLI user, I want `profiles set` to apply all profiles or none, so that no chain breaks halfway.
31. As a CLI user, I want `profiles set` to keep profiles that the file does not name, so that one file never removes others.
32. As a CLI user, I want a chain to name a profile in its file, so that one step adds both.
33. As a CLI user, I want `profiles edit` to open the profile in `$EDITOR`, so that I can change it as text.
34. As a CLI user, I want `profiles edit` to do nothing for an unchanged file, so that closing the editor is safe.
35. As a CLI user, I want `profiles edit` to show a refusal and offer to edit again, so that I keep my change.
36. As a CLI user, I want `profiles edit` to refuse a new name or a second profile, so that edit changes only one profile.
37. As a CLI user, I want `profiles remove` to act as the page does, so that both give the same refusals.
38. As a CLI user, I want a non-zero exit code for each refusal, so that scripts can stop.
39. As a CLI user, I want the help of `publish --profile` to name `lvh-yard profiles`, so that it names no file.
40. As a new user, I want `lvh-yard init` to stop writing `route-profiles.yaml`, so that no unread file sits in the folder.
41. As a user who upgrades, I want my valid profiles to stay, so that my Routes keep their auth.
42. As a user who upgrades, I want the notes to show how to apply an old file, so that I get back refused profiles.
43. As a teammate, I want profiles to reach my Traefik as `<workstation>_<profile>`, so that names never clash.
44. As an API client, I want endpoints to list, read, add, replace and remove profiles, so that I can script changes.
45. As an API client, I want each refusal as a problem answer with the reason, so that I can show it.

## Implementation Decisions

- Consul KV keeps each Route Profile under the same key as today, `lvh-yard/route-profiles/<name>`, with the same stored form. Remote Workstations read it the same way. So the Traefik configuration and the `<workstation>_<profile>` names do not change.
- Existing Workstations keep their profiles on upgrade, because the old dashboard already copied each valid one into Consul KV. No migration code runs. A refused profile never reached Consul KV. The upgrade notes tell you to apply the old file once with `lvh-yard profiles set`.
- The file reader, its polling, the "last good" copy and the stored errors go away. So does the `YARD_ROUTE_PROFILES` setting and its default path.
- The checks of the file reader move to one place that every write path uses. These paths are the page, the API, the CLI through the API, and later the Backup import.
- The checks stay the same. The dashboard refuses `basicAuth.usersFile`, plugins, and `forwardAuth` to an Out-of-range address. A profile needs a known Traefik type and a name that Traefik accepts. A chain names only profiles of this Workstation.
- A set of profiles is checked as one unit. A chain can name a profile in the same set or one already in Consul KV. If one profile of the set is refused, nothing is written.
- If a Publication of this Workstation or a chain names the profile, remove is refused with a conflict answer. The answer names each Service and chain.
- API, all under the path that exists today, `/api/workstation/route-profiles`. The path does not name the Workstation, because Route Profiles change only on their Home Workstation.
  - `GET` on the collection answers a list. Each item has the name, the Traefik type and the Services whose Publications name it. A profile with no type or several types has no type field.
  - The old answer with an `error` field and a `profiles` list goes away.
  - `GET` on one name answers the item plus its definition, as a JSON object in Traefik's format, for example `{"basicAuth": {"users": [...]}}`.
  - `PUT` on one name adds or replaces that profile. The body is its definition.
  - `PUT` on the collection adds or replaces a set. The body is an object of name to definition. Profiles that it does not name stay.
  - `DELETE` on one name removes it.
  - A refusal answers a problem with the reason, as the other endpoints do. A bad profile is a client error. A profile in use is a conflict.
- The CLI reads and writes Traefik's file format: a YAML document with `http.middlewares.<name>`. `profiles show` prints that format, so its output works as input to `profiles set`. `profiles set` turns the file into the set body.
- `profiles edit` writes the `profiles show` output to a temporary file. It runs `$EDITOR` on it, else `vi`. If the text changed, it sends it with `PUT` on the name. It refuses a file with another name or with more than one profile.
- The Route Profiles page lives at its own path with its own navigation link. The form uses a plain text box in a monospace font. The "new profile" button picks one example from a short list. The examples are the ones that `route-profiles.yaml` holds today.
- `lvh-yard init` and `reinit` no longer write `route-profiles.yaml`. `compose.yaml` no longer sets `YARD_ROUTE_PROFILES`. The comment over the Workstation Folder mount no longer names the file. The Backups spec changes that mount.
- `docs/user-guide.md` explains the page, the five commands and the upgrade step. `docs/development.md` loses the test notes about the shared `route-profiles.yaml`.

## Testing Decisions

- A good test acts as a user or an API client. It looks only at what they can see: API answers, page text, the Traefik configuration that the dashboard serves, and CLI output.
- Seam 1 is the dashboard, with a real Consul from Testcontainers and the two-datacenter Yard. Tests add profiles through the API instead of writing a temporary `route-profiles.yaml`.
- Seam 1 covers the list with types and users, and each refusal. It shows that a refused save changes nothing. It covers a set with a chain, and remove with its two conflicts.
- Seam 1 also covers the profiles in the Traefik configuration of the Home Workstation and of the remote one. It covers Aliases that apply a profile, and a change that reaches the remote configuration.
- The page tests cover the Route Profiles page, its form and its examples. They cover the kept text after a refusal, the navigation link and the link from the Service page.
- The one end-to-end test with a real Traefik stays. Its basic-auth profile now comes from the API. It answers 401 without credentials and passes with them.
- The OpenAPI test covers the new endpoints and the new list answer.
- Seam 2 is the CLI test with its stub dashboard. It covers the five commands, standard input for `set`, the exit codes, and `init` without `route-profiles.yaml`. For `edit`, the test sets `$EDITOR` to a small script that changes the file, and a second script that leaves it as it is.
- Prior art: `RouteProfilesTest`, `RemoteRouteProfilesTest`, `RouteProfilesOnRoutesTest`, `RouteProfilesThroughTraefikTest`, `AliasesTest`, `NavigationBarTest`, `ServicePageTest`, `OpenApiTest`, and the CLI's `YardTest`.
- Some tests check the file reader: a broken file, an unknown type that keeps the last good profiles, and a profile removed from the file. They become tests of refused saves.

## Out of Scope

- Backups, and Route Profiles in a Backup. They come in `.scratch/backups/spec.md`.
- A code editor with syntax colors. A plain text box comes first.
- Rename in one step.
- Keeping secrets such as basic-auth hashes outside Consul KV. That stays a later study in `.scratch/vault-secrets/`.
- Automatic reading of an old `route-profiles.yaml` on upgrade.

## Further Notes

- The glossary entry for Route Profile stays true: it is written on the Home Workstation, and every Workstation that routes the Publication applies it.
- Basic-auth hashes in Consul KV stay readable by the whole Yard, as ADR 0004 says.
