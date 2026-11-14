# Windows CLI installation evidence

The packaged installer passes native ordinary-user checks in Windows
PowerShell 5.1 and PowerShell 7. Public Pages deployment and release upload
remain behind the maintainer's publication gate. No release or push was made.
These results do not establish full Windows Workstation support.

## Environment and seam

Observed on 2026-11-14:

- Windows 11 x64, build 26300, account `yarddev`.
- Windows PowerShell `5.1.26100.9818` and portable PowerShell `7.7.0`.
- The test asserted that the account's token was not an administrator.
- Windows fixture folder: `C:\Users\yarddev\lvh-dev\02`.
- PowerShell 7 executable: `C:\Users\yarddev\lvh-dev\01\pwsh7\pwsh.exe`.

The approved seam is the complete installer, evaluated as PowerShell source,
with only `Invoke-WebRequest` controlled by `dev/test-install-windows.ps1`.
The download boundary supplies actual production archives and manifests.
Native Windows code performs ZIP extraction, hashing, file replacement,
file locking, process creation and user PATH registry changes.
No production URL override or test switch was added.

The VM helper starts its shell with process execution policy `Bypass`.
That is existing test infrastructure, not an installer action. The native
suite compared all execution-policy scopes before and after installation.
A separate observation found CurrentUser and LocalMachine policy `Undefined`.
The installer source runs through `Invoke-Expression`, as the documented
installation command does; it does not require a policy change.

## Production packaging and site asset

From the ticket worktree, both versions were built with:

```sh
mise exec -- sh dev/package-cli.sh 9.8.7
mise exec -- sh dev/package-cli.sh 9.8.8
```

Each version's archives were retained in its own directory under
`~/.local/state/lvh-yard-windows-11/artifacts/02/`. The manifest command in
each directory matched the release job:

```sh
sha256sum lvh-yard-*.tar.gz lvh-yard-*.zip > SHA256SUMS
sha256sum -c SHA256SUMS
```

All eight archives passed checksum verification. For each version, the three
Unix archives contained `lvh-yard` and `LICENSE`. Each Windows ZIP contained
exactly `lvh-yard.exe` (10,848,768 bytes) and `LICENSE` (11,358 bytes).
`unzip -p <archive> LICENSE | cmp - LICENSE` passed against the repository's
Apache License. The packaging script also ran the Linux amd64 executable
and checked its exact version for each build.

The observed Windows ZIP checksums were:

```text
4a36f305ee66478986c89db5b1e1035e49c6d3becde35eaa86b9fca27fcf0a98  lvh-yard-9.8.7-windows-amd64.zip
8c644fccefe21c09fe0603b4310538c64d042d40e50f34c4fe1478aa5da168d8  lvh-yard-9.8.8-windows-amd64.zip
```

`mise run site:build` passed and copied `install.ps1` into
`docs/site/dist`. The first build's `cmp` matched source and output. After
the native compatibility fix below, the site build ran again, and its output
was copied to Windows for both final native runs. The existing Pages workflow
uploads that directory and deploys it. This proves the local build path;
it does not prove a live Pages deployment.

`.github/workflows/release.yml` calls the production packaging script,
uploads both `.tar.gz` and `.zip` artifacts, and includes both extensions in
the checksum and `gh release create` commands. Those upload commands were
not executed against GitHub because publication needs maintainer approval.

## Native scenarios

The two final commands were run with `WIN_USER=yarddev`, first through the
PowerShell 5.1 VM helper and then with `WIN_PS` set to the portable PowerShell
7 executable:

```powershell
$root = "$env:USERPROFILE\lvh-dev\02"
& "$root\test-install-windows.ps1" -Installer "$root\install.ps1" -Artifacts "$root\artifacts" -Root "$root\acceptance-ps51"
# In PowerShell 7:
& "$root\test-install-windows.ps1" -Installer "$root\install.ps1" -Artifacts "$root\artifacts" -Root "$root\acceptance-ps7"
```

Both final runs exited successfully and printed each PASS below:

| Scenario                             | Observed result in both shells                                                                                                                                                                                                                                                                                                            |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Default directory and latest version | With both overrides unset, the installer requested the latest-release manifest and installed version `9.8.7` under `Local AppData\lvh-yard\bin`. The test redirected `LOCALAPPDATA` into its owned acceptance root; it did not write the real default application folder.                                                                 |
| Manual ZIP installation              | The actual ZIP matched its manifest, `Expand-Archive` extracted it, the executable reported `lvh-yard 9.8.7`, and `LICENSE` contained the Apache License.                                                                                                                                                                                 |
| Version override without `v`         | `LVH_YARD_VERSION=9.8.7` requested `/releases/download/v9.8.7/SHA256SUMS`.                                                                                                                                                                                                                                                                |
| Destination override                 | Installation and execution passed from `O'Brien space café\bin`, preserving spaces, an apostrophe and Unicode.                                                                                                                                                                                                                            |
| Reinstall                            | Running the installer again preserved the exact user PATH string without adding an entry.                                                                                                                                                                                                                                                 |
| PATH deduplication                   | After adding an uppercase duplicate with a trailing backslash, reinstall removed that duplicate and preserved the pre-duplicate PATH string.                                                                                                                                                                                              |
| Upgrade and prefixed version         | `LVH_YARD_VERSION=v9.8.8` requested `/releases/download/v9.8.8/SHA256SUMS`; the installed executable reported `lvh-yard 9.8.8`.                                                                                                                                                                                                           |
| Manifest download failure            | A controlled network exception rejected installation; the installed executable's hash and working version remained unchanged.                                                                                                                                                                                                             |
| Archive download failure             | A controlled network exception preserved the installed executable's hash and working version.                                                                                                                                                                                                                                             |
| Checksum mismatch                    | A manifest with the Windows archive hash replaced by zeros rejected the actual ZIP before replacement. The installed hash and working version remained unchanged.                                                                                                                                                                         |
| Locked destination                   | A separate native PowerShell process opened the installed file with a real Windows handle denying delete sharing. Replacement failed with `The process cannot access the file because it is being used by another process.` The old executable's hash and working version remained unchanged. The test stopped only its own lock process. |
| Destination collision                | A real directory at `lvh-yard.exe` caused the native move to fail with `Cannot create a file when that file already exists.` Its marker file survived; the saved working executable was restored by test cleanup and still reported `9.8.8`.                                                                                              |
| Failure side effects                 | Every failure left user PATH unchanged and left no `.lvh-yard-*` staging directory.                                                                                                                                                                                                                                                       |
| New shell resolution                 | A newly launched instance of each shell resolved the exact overridden executable path and reported `lvh-yard 9.8.8`. It loaded machine and user PATH from the registry because the SSH service retains an older environment. This was a new shell process, not an observed Explorer-launched terminal window.                             |
| Policy preservation and cleanup      | Machine PATH and every execution-policy scope matched their pre-test values. The original user PATH was restored exactly, including its unrelated WindowsApps and Docker Desktop entries. Each acceptance root was removed.                                                                                                               |

The installer does not invoke Docker, firewall tools or security-policy
commands. The native suite observed no machine PATH or execution-policy
change. It did not audit unrelated Docker or firewall state.

`Get-AuthenticodeSignature` on the actual `9.8.8` packaged executable returned
`NotSigned`. This supports the unsigned-binary guidance. No SmartScreen dialog
was observed or bypassed. Checksum verification tests download integrity;
it does not establish an Authenticode publisher identity.

## Compatibility defect found and fixed

The first PowerShell 5.1 run passed initial installation and manual ZIP
extraction, then failed on reinstall. `File.Replace` received a null backup
name and threw `The path is not of a legal form.` PowerShell 5.1 converted
that string argument to an empty path.

The installer supplies a real backup name inside its same-volume staging
directory. It uses one native `File.Replace` call and has no delete-then-move
fallback. Both final native runs passed reinstall, upgrade and the real lock
scenario after this change. The failed run's cleanup also restored the
original user PATH and removed its acceptance root.

Raw native logs and the actual packaged artifacts remain outside the
repository at `~/.local/state/lvh-yard-windows-11/artifacts/02/`.

## Publication boundary

The local site build, actual release archives, checksum processing and native
installer scenarios are observed. The public installer URL, GitHub artifact
upload and public release assets have not been observed from this branch.
The maintainer owns that publication gate. The user guide marks these
Windows public commands as awaiting publication and reserves the full
Windows Workstation support announcement for the remaining acceptance.

## Repository checks

`mise run test:unit` and `mise run check` passed in the ticket worktree.
The latter ran the dashboard's clean verification, Go vet and lint
(`0 issues`), Go unit tests, and the CLI executable smoke tests.
