# Windows 11: LAN address choice with two adapters, and the Docker Desktop settings

Date: 2026-11-19. Branch `quality-review` (the CLI at commit `1eac439`, built as version `9.8.9`). Raw output stays in
`~/.local/state/lvh-yard-windows-11/evidence/qr/`. This closes the two boxes that the maintainer accepted as unobserved
on 2026-11-16: the LAN address choice with several adapters (tickets 03 and 05), and the Docker Desktop settings (ticket 03).

## Environment

| Item           | Observed value                                                                                 |
| -------------- | ---------------------------------------------------------------------------------------------- |
| Windows        | Windows 11 Pro x64 `10.0.26300.9974`, libvirt VM `win11` at `192.168.122.117`                  |
| Account        | `t-x\yarddev`, not an administrator. The network changes below ran as the administrator `test` |
| Shell          | Windows PowerShell 5.1; the interactive run used a real console (ConPTY, `win-tty`)            |
| Docker Desktop | 4.99.0 (245822), engine 29.9.3                                                                 |

## What I changed on the VM, and put back

1. `virsh attach-interface win11 network default --model e1000e --live` added a second network card. It was live
   only, so the persistent VM definition did not change. Windows named it `Ethernet 2` and DHCP gave it
   `192.168.122.55`. The first card, `Ethernet`, kept `192.168.122.117`.
2. For the tunnel case, as `test`: `Rename-NetAdapter 'Ethernet 2' 'WireGuard Tunnel'` and
   `Set-NetIPInterface -InterfaceAlias 'WireGuard Tunnel' -InterfaceMetric 5`.
3. After the runs: automatic metric on, the adapter renamed back, and
   `virsh detach-interface win11 network --mac 52:54:00:aa:bb:01 --live`. The adapter list, the IPv4 addresses and the
   default route match the baseline again (`Ethernet`, `192.168.122.117`, next hop `192.168.122.1`, metric 25).
   Windows may keep a hidden, not present `Ethernet 2` device. I did not remove it.

Baseline before the change: adapters `vEthernet (WSL (Hyper-V firewall))` and `Ethernet`; one default route.

## A. Two eligible adapters, no tunnel

Both cards were on `192.168.122.0/24` with a default route of metric 25.

```text
Ethernet 2   192.168.122.55    default route via 192.168.122.1, interface metric 25
Ethernet     192.168.122.117   default route via 192.168.122.1, interface metric 25
```

`lvh-yard ws init --workstation ws-multi --base-domain lvh.me`, no `--address`, no terminal, three runs in fresh
folders: all three exited 0 and wrote `YARD_BIND_ADDRESS=192.168.122.117`. The CLI asks Windows for the source address
of the default route (a UDP socket to `192.0.2.1`), so Windows decides when two adapters have the same metric. It
did not stop to ask. That is the accepted rule: the default route decides, and the setup asks only when that route
is on a tunnel adapter or on no suitable adapter. I did not measure which of two equal routes Windows prefers
over time, so a different choice on another computer is possible.

## B. A tunnel adapter holds the default route

With `Ethernet 2` renamed `WireGuard Tunnel` and metric 5, Windows picked `192.168.122.55` as the source for an outside
address (a .NET socket connected to `192.0.2.1`). The adapter is not a real tunnel: only its name and its metric say
so. The CLI reads the name, so this exercises the real adapter list and the real route table of Windows.

No terminal, no `--address`:

```text
could not choose a LAN IPv4 address: the default route is on "WireGuard Tunnel", which looks like a VPN or tunnel adapter; other adapter addresses: 192.168.122.117. Use --address
Missing --address. No terminal is open: pass --address
```

Exit code 1, and no `.env` was written. With `--address 192.168.122.117` the same command exited 0 and wrote
`YARD_BIND_ADDRESS=192.168.122.117`.

In a real console, with no `--address`, the command printed the same reason, then asked
`LAN IPv4 address []:` with no default. The answer `192.168.122.117` was accepted and the setup was written
(`multi-adapter-pty-init.out`).

## C. An explicit address

- `--address 192.168.122.55` (the address of the other adapter): exit 0, `YARD_BIND_ADDRESS=192.168.122.55`,
  `YARD_INSTANCE_HOST` empty.
- `--address 10.99.99.99`, which no adapter holds: exit 0. The setup does not check that the address belongs to this
  computer. The address is the user's word, and it is neither a VPN address nor a Docker hostname that the CLI chose.

## D. Docker Desktop settings (read only)

| Item                         | Observed                                                                                                                                                   |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Version                      | Docker Desktop 4.99.0, engine 29.9.3                                                                                                                       |
| Linux containers             | `docker info`: operating system `Docker Desktop`; `EngineTypeWindows` is not set to Windows containers                                                     |
| WSL2 backend                 | the `docker-desktop` distribution is WSL version 2                                                                                                         |
| Host networking              | `settings-store.json`: `HostNetworkingEnabled = True`                                                                                                      |
| Enhanced Container Isolation | no `EnhancedContainerIsolation` key in `settings-store.json`; runtimes `runc`, `io.containerd.runc.v2` and `nvidia`, default `runc`, and no Sysbox runtime |

The settings file holds only these keys: `AutoStart`, `DisplayedOnboarding`, `EngineTypeWindows`,
`HostNetworkingEnabled`, `LastContainerdSnapshotterEnable`, `LicenseTermsVersion`, `SettingsVersion`. I changed no
Docker setting.

## What this does not show

- No real VPN or tunnel adapter ran. The tunnel case is a renamed card with the lowest metric. A real WireGuard or
  OpenVPN adapter was not installed, because the VM rules forbid installing software.
- Only Docker Desktop 4.99.0 ran. The ticket asks for 4.34 or later. No older version was run.
- The interactive prompt has no menu of the adapters. It states the reason, lists the other addresses, and asks for one.
