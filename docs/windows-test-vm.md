# The Windows test VM

Use this VM when a check needs a real Windows system with Docker Desktop. The deterministic tests cannot show Docker Desktop behavior, native listeners, ACLs or console prompts. See [Windows runtime development](development.md#windows-runtime-development).

The VM is a libvirt guest on the maintainer's computer. These notes describe that setup. Another computer needs its own VM with the same accounts.

## The setup

| Item    | Value                                                                                                              |
| ------- | ------------------------------------------------------------------------------------------------------------------ |
| Name    | `win11`, on `qemu:///system`                                                                                       |
| System  | Windows 11 Pro x64, build 26300                                                                                    |
| Size    | 8 vCPUs, 16 GiB                                                                                                    |
| Address | `192.168.122.117`, on the libvirt network `virbr0`                                                                 |
| Host    | `192.168.122.1`, on the same network. It serves as the second LAN computer and as the Linux Workstation of a Yard. |
| Access  | SSH with a password. `sshd_config` has only the default settings.                                                  |

Check that the VM runs with `virsh -c qemu:///system domstate win11`.

## Accounts

| Account     | Role          | Use it for                                                    |
| ----------- | ------------- | ------------------------------------------------------------- |
| `test`      | Administrator | Test infrastructure only. It is the helpers' default account. |
| `yarddev`   | Ordinary user | Product acceptance. Docker Desktop runs in this profile.      |
| `yardother` | Ordinary user | The second user of a privacy check.                           |

Select the account with `WIN_USER=yarddev`. Docker Desktop installs once for each user, so the images and the engine of `yarddev` do not exist for `test`.

The helpers hold the test passwords. Keep passwords out of this repository.

## The helpers

The helpers are in `~/.local/state/lvh-yard-windows-11/bin/`, outside the repository. The examples below call them by name, so put that folder on your `PATH` or call them by full path. `win-env` sets the host, the user and the password prompt for the others.

| Helper                | Does                                                                                                                             |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `win-ps [file.ps1]`   | Runs a PowerShell script from a file or from standard input. It returns the exit code of the script. `WIN_PS` selects the shell. |
| `win-ssh '<command>'` | Runs one `cmd.exe` command line.                                                                                                 |
| `win-scp <src> <dst>` | Copies files. Write a remote path as `win:C:/path`.                                                                              |
| `win-tty <command>`   | Opens a session with a real Windows console. Drive it with Python `pexpect` to test prompts, terminal detection and Ctrl+C.      |

`win-ps` uses Windows PowerShell 5.1. To use PowerShell 7, set `WIN_PS` to its full path. PowerShell 7 is portable and not on the `PATH`. For `yarddev` it is `C:/Users/yarddev/lvh-dev/01/pwsh7/pwsh.exe`.

## Before you test

Run the probe from the repository root, as the account that you test with:

```
WIN_USER=yarddev win-ps dev/windows-probe.ps1
```

The probe changes nothing. It prints:

- The account, its rights, PowerShell and the Windows build.
- The IPv4 addresses and the firewall policy.
- Docker's version, CPUs, memory, containers and images.
- The Docker Desktop settings and `.wslconfig`.
- The listening ports from 1024 up, with the process of each.
- Whether `lvh-yard.exe` is on the `PATH`, and the folders in `lvh-dev`.

Read the probe first. It replaces many one-off checks.

When `docker.exe` fails, Docker Desktop is not running in the desktop session of that user. SSH cannot start it. Ask the maintainer to start it.

## Run a check

1. Build on Linux. The VM has no Go, Git or shell.
   ```
   cd cli
   GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -o target/lvh-yard.exe ./cmd/lvh-yard
   ```
2. Make a folder in the `lvh-dev` folder of the account, then copy the files into it.
   ```
   WIN_USER=yarddev win-ssh 'mkdir C:\Users\yarddev\lvh-dev\mine'
   WIN_USER=yarddev win-scp target/lvh-yard.exe win:C:/Users/yarddev/lvh-dev/mine/lvh-yard.exe
   ```
3. Run them with `win-ps` or `win-ssh`.
   ```
   WIN_USER=yarddev win-ssh 'C:\Users\yarddev\lvh-dev\mine\lvh-yard.exe --version'
   ```
4. Remove the containers, networks and volumes that you made, unless a later step needs them.

## What the VM cannot do

- `docker pull` fails in an SSH session: "A specified logon session does not exist". The credential helper needs a desktop logon. Run `docker pull` and `docker save -o x.tar` on Linux, copy the file, then run `docker.exe load -i x.tar` on the VM. Do this for each image that Compose would pull.
- `Get-CimInstance`, `Get-NetIPAddress` and `Get-NetFirewallProfile` answer "Access denied" to an ordinary user over SSH. Use .NET, `netsh.exe` and `netstat.exe`, as the probe does.
- `bash.exe` on the `PATH` is only the WSL launcher. There is no `sh`, `vi` or Git.

## Rules

- Put files only under the `lvh-dev` folder of the account that you test with.
- Stop and ask the maintainer before you do any of these: install software, create or change an account, change Docker Desktop settings, change a firewall rule, change the execution policy, or change the `PATH`.
- Give your Compose project its own name and its own ports. Do not stop a container that you did not start.
- Run at most two Workstation stacks at the same time. The limit comes from a measurement with 2 CPUs and 3.9 GB in the WSL VM of `yarddev`. With three stacks, Docker calls took seconds and one dashboard needed seven minutes to start. The WSL VM now has 4 CPUs and 6 GB, set by `.wslconfig`, and nobody measured the limit again.
- Shutting down the guest stops every test that runs. Do it only between waves, with the agreement of the maintainer.

## A Yard with the Linux host

The host is the second computer of a mixed Yard. Give the host Workstation the address `192.168.122.1`. The VM reaches it on `virbr0`. Keep the Gossip Key out of notes and captured output.

## What to write down

Record only what you observed: the account, the shell, the command and the result. Record a failed run the same way.
