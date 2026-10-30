# 04: Health checks and editing Static Instances

**What to build:** A developer chooses an HTTP or TCP health check for a Static Instance, with its interval and timeout, edits a Static Instance's host, port, check, tags and metadata, and reads each check's last output on the Service detail page.

**Blocked by:** 01 (Register a Static Instance and see it in the Catalog), 02 (CLI: `yard list`, `yard register`, `yard remove`)

**Status:** done

- [x] A Static Instance can have an HTTP check with a path, or a TCP check.
- [x] Each check has an interval and a timeout, defaulting to 10 seconds and 2 seconds.
- [x] Script checks are not offered, and the API refuses them.
- [x] Editing a Static Instance's host, port, check, tags or metadata keeps it the same Instance.
- [x] The Service detail page and the API show each check's last output.
- [x] An HTTP check against an Instance that answers with an error shows critical Health and that answer in its output.
- [x] `yard register` accepts the check, its interval and timeout, tags and metadata.
