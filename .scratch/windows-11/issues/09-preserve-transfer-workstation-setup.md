# 09: Preserve and transfer Workstation setup

**What to build:** A Windows developer maintains a Workstation without losing its setup, transfers the setup covered by a Backup between platforms, and performs a destructive reset only when explicitly requested.

**Blocked by:** 03: Set up a private local Windows Workstation.

**Status:** ready-for-agent

- [ ] Create persistent setup, then stop/start, recreate, upgrade images and restart Docker Desktop. Services and their saved setup remain available after each operation. (Stop/start, recreate and upgrade observed; the Docker Desktop restart waits for the maintainer: `bin/09-post-restart.sh`.)
- [x] Normal lifecycle operations preserve the project-scoped Consul volume. The long-lived Consul process remains non-root and can use the retained state after recreation.
- [x] Image upgrades preserve user-owned Compose networking and storage edits, environment edits and other user configuration. Existing generated-file ownership rules remain intact.
- [x] Export a Backup into the protected Workstation Folder and import it into a freshly configured Workstation on another supported platform. Demonstrate the reverse direction as well.
- [x] Verify the setup defined by the Backup contract survives transfer. Do not add Bound Instances, Health, Remote Services, private certificates or raw Consul data to the Backup format.
- [x] Cross-platform movement uses fresh setup and Backup import. Documentation does not promise portable Compose files or automatic raw Consul-state migration.
- [x] Provide a documented explicit destructive-reset procedure with a data-loss warning. It removes only the selected project's Consul state and leaves another project's state intact.
- [x] Explain that deleting the Workstation Folder alone leaves Docker-managed state. Distinguish reset, Backup transfer and complete state removal without implying that a Backup archives private keys.
- [ ] Verify persistence and transfer through observable CLI/API results and actual running Workstations. Record the real Windows lifecycle, Backup write and project-isolation evidence. (All recorded in `evidence/09-preserve-transfer-workstation-setup.md` except the Docker Desktop restart.)
