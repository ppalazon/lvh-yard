# 03: Choose port and name when importing, and import from the CLI

**What to build:** Containers with several ports or none can be imported by choosing the port; the proposed name can be edited; and `lvh-yard containers` and `lvh-yard import` do the same from the terminal.

**Blocked by:** 02

**Status:** done

- [ ] The page asks for the port when a container has several or none; the API answers a client error listing the ports when it is missing
- [ ] The proposed Service name can be edited before import
- [ ] `publish: false` imports without publishing
- [ ] The unpublished-container-port warning shows for an import into a Yard-scoped Service
- [ ] `lvh-yard containers` lists the same containers and ports
- [ ] `lvh-yard import <container> [--port N] [--name S] [--no-publish]` imports; without `--port` on a multi-port container it fails and lists the ports
