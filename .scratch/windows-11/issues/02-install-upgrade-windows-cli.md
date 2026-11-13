# 02: Install and upgrade the Windows CLI

**What to build:** A Windows user installs the CLI through a per-user PowerShell installer or a ZIP archive, then upgrades by rerunning the installer. Installation checks downloads, updates only user PATH, and preserves the working executable after failure.

**Blocked by:** 01: Run native Windows CLI workflows.

**Status:** ready-for-agent

- [ ] Release packaging produces a Windows x64 ZIP containing the executable and license. Archive upload, release publication and SHA256SUMS include that ZIP.
- [ ] The PowerShell installer reaches the published installation assets through the existing site build. Verify the installer against actual packaged artifacts before the final support announcement.
- [ ] Installation works as an ordinary user under Windows PowerShell 5.1 and PowerShell 7. The installed executable reports its version from a newly opened terminal.
- [ ] The installer verifies the selected archive against SHA256SUMS before installing it. A mismatch rejects the archive without replacing the installed executable.
- [ ] Preserve the existing version and destination override conventions. Default installation uses a separate bin folder in the application's LocalAppData folder.
- [ ] Add the executable folder to user PATH without duplicate entries. Preserve machine PATH and explain when a new terminal is needed.
- [ ] Reinstall and upgrade work. Download, checksum and replacement failures leave the working executable intact, including when Windows locks the destination.
- [ ] Installation works with spaces, apostrophes and Unicode in destination paths. The manual ZIP workflow is documented and usable.
- [ ] The installer does not install Docker, change Docker settings, alter firewall rules or relax PowerShell execution policy.
- [ ] Document unsigned-binary warnings and distinguish checksum integrity from Authenticode publisher authentication.
- [ ] Run native installer acceptance under both supported PowerShell versions. Update installation and release documentation, reserving the full Windows support claim for final acceptance.
