# Test release v0.1.1-rc.1

Date: 2026-11-16. The maintainer chose the tag and approved the push.

## What ran

- Tag `v0.1.1-rc.1` at commit `1a9834e`, the tip of `windows-11`, pushed to the public repository `ppalazon/lvh-yard`.
  The tag was the only ref pushed. `main` and `windows-11` were not pushed.
- The `Release` workflow (run 43849475401) finished with success. Its three jobs, `dashboard-image`, `cli` and
  `release`, all passed.

## Found before the push

The release job did not mark a hyphenated tag as a pre-release, and `install.sh` and `install.ps1` download from
`releases/latest`. A test release would have become the latest release. Commit `1a9834e` makes the job add
`--prerelease` when the tag holds a hyphen. `actionlint` accepted the workflow files.

## Observed on GitHub

- The release `v0.1.1-rc.1` is a pre-release and not a draft. Its assets are `lvh-yard-0.1.1-rc.1-linux-amd64.tar.gz`,
  `lvh-yard-0.1.1-rc.1-linux-arm64.tar.gz`, `lvh-yard-0.1.1-rc.1-macos-arm64.tar.gz`,
  `lvh-yard-0.1.1-rc.1-windows-amd64.zip` and `SHA256SUMS`.
- `sha256sum -c SHA256SUMS` reported `OK` for all four archives.
- The Windows ZIP holds `lvh-yard.exe` and `LICENSE`.
- The API's latest release was still `v0.1.0` after the run.
- The image `ghcr.io/ppalazon/lvh-yard-dashboard:0.1.1-rc.1` has its own digest (`sha256:d44ca5c1...`). The tags `latest`
  and `0.1.0` still share the digest `sha256:7fb28189...`. I could not list the package versions, because the token has
  no `read:packages` scope, so I compared the digests of the tags.
- The Linux amd64 binary from the release printed `lvh-yard 0.1.1-rc.1`.

## Observed on the Windows 11 computer

As the ordinary user `yarddev`, in Windows PowerShell 5.1.26100.9818 and in PowerShell 7.7.0, with `docs/site/install.ps1`
from the branch, `LVH_YARD_VERSION=0.1.1-rc.1` and a test install folder:

- The installer downloaded `SHA256SUMS` and the ZIP from `github.com/ppalazon/lvh-yard/releases/download/v0.1.1-rc.1`.
- The installed `lvh-yard.exe` had the SHA-256 of the executable inside the published ZIP
  (`f1ddf112ea45d3e2cf4317e94a88971573a9be3d05d27374033521ea12dce352`) in both shells.
- `lvh-yard.exe --version` printed `lvh-yard 0.1.1-rc.1`.
- The folder was in the user PATH after the first install. A second install left one entry.
- The test restored the user PATH value and its kind byte for byte and removed its folder.

## Not observed

- The site was not deployed, so `https://lvhy.cc/install.ps1` does not serve the installer. The documented one-line
  command was not run. The test used the installer file from the branch.
- The macOS arm64 and Linux arm64 executables were not run, because no such computer was used.
- The `windows` CI job has not run on GitHub. It starts on a pull request or a push to `main`, not on a tag.
- The test image was not deleted, because the token cannot delete packages. The release and the tag still exist.
- The release job builds its own ZIP. The ZIPs tested in earlier tickets were built locally with other version numbers,
  so their executables differ by design. This pass tested the published ZIP itself.
- SmartScreen and the Mark of the Web were not tested.
