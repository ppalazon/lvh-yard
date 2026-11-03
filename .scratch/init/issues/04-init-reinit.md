# 04: lvh-yard init --reinit backs up and rewrites, keeping values

**What to build:** `lvh-yard init --reinit` gives the user the latest templates' options without losing their settings: the old files are backed up and their values carried over.

**Blocked by:** 01

**Status:** done

- [x] `compose.yaml` and `.env` are renamed to `<file>.<yyyyMMdd-HHmmss>.bak` before the new ones are written
- [x] Every `KEY=value` of the old `.env` whose key exists in the new template is carried over
- [x] New settings take their default
- [x] Old keys that no longer exist are kept at the end, commented, under a note
- [x] `route-profiles.yaml` is untouched
- [x] The backups are named in the output
