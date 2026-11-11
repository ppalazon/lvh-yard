# Issue tracker: Local Markdown, and GitHub Issues for users

Specs and tickets for planned work live as markdown files in `.scratch/`. Bugs and feature requests from users live in [GitHub Issues](https://github.com/ppalazon/lvh-yard/issues). When the maintainer plans work from a GitHub issue, the spec or ticket in `.scratch/` names the issue number, and the pull request closes the issue.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`
- The spec is `.scratch/<feature-slug>/spec.md`
- Implementation issues are one file per ticket at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01`, never a single combined tickets file
- Triage state is recorded as a `Status:` line near the top of each issue file (see `triage-labels.md` for the role strings)
- Comments and conversation history append to the bottom of the file under a `## Comments` heading
- When a spec and all its tickets are `done`, delete the feature folder. Git keeps it, and old plans in the tree mislead searches with words the glossary no longer uses.

## When a skill says "publish to the issue tracker"

If `.scratch/<feature-slug>/` does not exist, create it. Then create a new file in it.

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. The user will normally pass the path or the issue number directly.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a file with one **child** file per ticket.

- **Map**: `.scratch/<effort>/map.md` (the Notes / Decisions-so-far / Fog body).
- **Child ticket**: `.scratch/<effort>/issues/NN-<slug>.md`, numbered from `01`, with the question in the body. A `Type:` line records the ticket type (`research`/`prototype`/`grilling`/`task`). A `Status:` line records `claimed`/`resolved`.
- **Blocking**: a `Blocked by: NN, NN` line near the top. If every file that a ticket lists is `resolved`, the ticket is unblocked.
- **Frontier**: scan `.scratch/<effort>/issues/` for files that are open, unblocked, and unclaimed. The first by number wins.
- **Claim**: set `Status: claimed` and save before any work.
- **Resolve**: append the answer under an `## Answer` heading, set `Status: resolved`, then append a context pointer (gist + link) to the map's Decisions-so-far in `map.md`.
