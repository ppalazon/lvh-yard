# Java socket measurements on Docker Desktop for Windows

[ADR-0014](../../../docs/adr/0014-windows-workstations-use-docker-desktop.md) now holds these measurements, so it cites no
file under `.scratch/`. Each one was observed on 2026-11-14. The raw outputs stay on the measurement host as
`03-java-ipv4-probe.out`, `03-java-ipv4-corrected.out` and `03-java-ipv6-impact.out` under
`~/.local/state/lvh-yard-windows-11/evidence/`. The longer account is in
[the ticket 03 evidence](03-private-local-windows-workstation.md#measured-java-transport-failure-and-ipv4-scope).

## Environment

- Windows 11 Pro x64, build 26300, account `yarddev` (ordinary user).
- Docker Desktop 4.99.0, Linux engine 29.9.3, WSL2 backend, host networking enabled.
- The dashboard image of the Workstation, running as uid 1000 with `--network host`. Java comes from that image.
- Target: the Consul HTTP API of the ticket 03 Workstation at `127.0.0.1:49182`, an IPv4 listener.

## Results

| Client in the same container   | Command line                                          | Observed result                                                                    |
| ------------------------------ | ----------------------------------------------------- | ---------------------------------------------------------------------------------- |
| curl                           | no Java option                                        | HTTP 200 from the Consul leader URL and the Traefik ping URL.                      |
| Java `Socket`                  | default JVM, `preferIPv4Stack=null`                   | `java.net.ConnectException: Connection refused (connect failed)`.                  |
| Java `HttpClient`              | default JVM                                           | `java.net.ConnectException`, caused by `java.nio.channels.ClosedChannelException`. |
| Java `Socket` and `HttpClient` | `-Djava.net.preferIPv4Stack=true`                     | `Socket connected /127.0.0.1:57848`, then `HTTP 200 "192.168.122.117:49186"`.      |
| Java client, unchanged JVM     | IPv6 loopback listener `[::1]:49192`                  | `IPv6 response=42`.                                                                |
| Java client                    | same listener, with `-Djava.net.preferIPv4Stack=true` | `java.net.SocketException: IPv6 protocol family unavailable`.                      |

The first attempt with the property failed with `Could not find or load main class .net.preferIPv4Stack=true`.
Windows PowerShell split the unquoted `-D...` argument at its dots. That was a quoting error, not a measurement. The
measured run quoted the argument: `java '-Djava.net.preferIPv4Stack=true'`.

The IPv6 listener ran in a temporary container, `lvhy-win03-ipv6`, which was removed afterwards.
