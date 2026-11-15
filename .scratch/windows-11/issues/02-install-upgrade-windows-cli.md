# 02: Install and upgrade the Windows CLI

**What to build:** A Windows user installs the CLI through a per-user PowerShell installer or a ZIP archive, then upgrades by rerunning the installer. Installation checks downloads, updates only user PATH, and preserves the working executable after failure.

**Blocked by:** 01: Run native Windows CLI workflows.

**Status:** ready-for-human

- [ ] Release packaging produces a Windows x64 ZIP containing the executable and license. Archive upload, release publication and SHA256SUMS include that ZIP.
      Open: `dev/package-cli.sh` built the ZIP locally. `release.yml` never ran, so no upload, release or `SHA256SUMS` that lists the ZIP was observed.
- [ ] The PowerShell installer reaches the published installation assets through the existing site build. Verify the installer against actual packaged artifacts before the final support announcement.
- [x] Installation works as an ordinary user under Windows PowerShell 5.1 and PowerShell 7. The installed executable reports its version from a newly opened terminal.
- [x] The installer verifies the selected archive against SHA256SUMS before installing it. A mismatch rejects the archive without replacing the installed executable.
- [x] Preserve the existing version and destination override conventions. Default installation uses a separate bin folder in the application's LocalAppData folder.
- [x] Add the executable folder to user PATH without duplicate entries. Preserve machine PATH and explain when a new terminal is needed.
- [x] Reinstall and upgrade work. Download, checksum and replacement failures leave the working executable intact, including when Windows locks the destination.
- [x] Installation works with spaces, apostrophes and Unicode in destination paths. The manual ZIP workflow is documented and usable.
- [x] The installer does not install Docker, change Docker settings, alter firewall rules or relax PowerShell execution policy.
- [x] Document unsigned-binary warnings and distinguish checksum integrity from Authenticode publisher authentication.
- [x] Run native installer acceptance under both supported PowerShell versions. Update installation and release documentation, reserving the full Windows support claim for final acceptance.

## Acceptance evidence

[Native installation evidence](../evidence/02-install-upgrade-windows-cli.md)
records both shell runs, production artifacts, the real Windows lock failure,
and user PATH restoration. The new-terminal check used newly launched native
shell processes that loaded PATH from the registry, not an observed
Explorer-launched terminal window.

The site build copies the tested installer into its published-assets directory.
The release workflow includes the ZIP in archive upload, checksums and release
upload. Live Pages deployment and GitHub publication remain unobserved and
are held for the maintainer's release gate; the public-assets item stays open.
