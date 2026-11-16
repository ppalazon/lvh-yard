# 10: Verify and publish Windows support

**What to build:** Users receive a documented Windows installation and Workstation support contract backed by reviewed native CI and real Windows 11 acceptance. The support announcement ships only after every approved routing, privacy and persistence requirement passes.

**Blocked by:** 02: Install and upgrade the Windows CLI; 04: Route containers without published ports; 06: Route published container ports across scopes; 08: Join a TLS-protected Yard with manual certificate handoff; 09: Preserve and transfer Workstation setup.

**Status:** ready-for-human

**Why ready-for-human:** the agent pass is done. What is left needs the maintainer: review of the evidence, the release and site publication, the ADR decision, the README, landing-page and user-guide wording, and the CI and firewall policy decisions. [The aggregate evidence](../evidence/10-windows-acceptance.md) lists what was observed. [The maintainer checklist](../evidence/10-maintainer-checklist.md) lists what is open. Only the boxes that were observed are ticked. Do not set this ticket to `done` before the maintainer reviews.

- [x] Confirm every blocking ticket has its own behavioral evidence. Final acceptance aggregates and checks the complete user journey; it does not defer earlier tickets' proof to this ticket.
- [x] Native CI runs the actual Windows executable and installer under Windows PowerShell 5.1 and PowerShell 7. Ensure administrator-only conditions or preinstalled Unix tools do not conceal prerequisites. (Observed on pull request #3, run 43857640970, after two failed runs found two defects: `evidence/10-github-ci-run.md`. The runner is an administrator and the output says so. The script drops Unix tools from its PATH.)
- [x] Complete the approved acceptance scenarios on real Windows 11 x64 as an ordinary user with Docker Desktop's supported WSL2 configuration. A Windows Server CI run cannot substitute for this runtime evidence. (Observed as `yarddev`. The maintainer approved the results with their limits on 2026-11-16.)
- [x] Exercise the full installed-product journey: install/upgrade, interactive setup, direct Compose startup, native-loopback and container routing, mixed-platform federation, TLS, private files, lifecycle persistence and Backup transfer. (Observed in one pass as `yarddev`; Backup export there, import in ticket 09; PowerShell 7 ran a smoke test only.)
- [x] Record environment details, commands or scenarios, and observed results for each acceptance requirement. Verify local-only listener exposure from a second LAN computer and effective privacy from a second ordinary user. (The second LAN computer is a Linux host.)
- [x] Run relevant existing Unix regression checks, template parity checks, repository unit checks and the narrow affected integration checks. Run required repository checks before committing implementation.
- [x] Obtain maintainer review of the real Windows evidence. A failed runtime requirement blocks the support claim; do not weaken tests or silently require published ports or reduced Yard participation. (The maintainer read `10-windows-acceptance.md` and approved its results on 2026-11-16. The limits listed there and in ADR-0014 stay recorded.)
- [ ] Publish the tested installer and artifacts through the existing release and site workflows. Public installation instructions resolve to those tested assets.
- [ ] Update the user guide for prerequisites, setup, install/upgrade, storage/reset, firewall access, shell behavior and manual certificate handoff. Update development documentation for the release matrix and verification process.
- [ ] Align short README and landing-page support/install entry points with the guide without duplicating it. State the approved exclusions, unsigned-binary warnings and manual Windows acceptance process.
- [x] Reconcile the proposed runtime ADR with the proven implementation through the repository's decision-recording process. Preserve accepted historical decisions and do not describe unverified behavior as accepted or shipped. (ADR-0014 was accepted by the maintainer on 2026-11-16. It lists the gates that stayed open and claims no support.)
