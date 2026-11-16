# A native Windows process needs a firewall rule to reach the LAN

Date: 2026-11-16. The maintainer approved one temporary, scoped inbound rule for this test.

## Question

In the integrated pass, a native PowerShell listener on `0.0.0.0` was not reachable from the Linux host. A container
listener on the same address was. Is Windows Firewall the cause?

## Setup

- Windows 11 Pro, display version 26H2, build 26300.9974, branch `ge_release`, at 192.168.122.117.
- Windows Firewall was on for the Domain, Private and Public profiles. The default inbound action was `NotConfigured`.
  The active network was Private.
- Inbound Allow rules that matched `powershell|docker|java|lvh`: four `com.docker.backend.exe` rules on Private and
  Public. None for `powershell.exe`.
- The listener ran as the ordinary user `yarddev`, in Windows PowerShell 5.1
  (`C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe`). It used `TcpListener` on `0.0.0.0:49899`.
- The probe ran on the Linux host (192.168.122.1) and made a TCP connection to 192.168.122.117:49899 with a 4 second
  timeout.

## Steps and results

| Step | Firewall change                                                                                                        | Result                                                    |
| ---- | ---------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| 1    | none                                                                                                                   | `NOT REACHABLE: TimeoutError`                             |
| 2    | one inbound Allow rule: TCP, local port 49899, remote address 192.168.122.1, program `powershell.exe`, Private profile | `CONNECTED`. The listener logged one accepted connection. |
| 3    | rule removed                                                                                                           | `NOT REACHABLE: TimeoutError`                             |

After step 3, no rule with that name was left (`rules left with that name: 0`).

An earlier run of the same script failed because of a bug in my probe function. It ran no probe and changed no rule.

## Conclusion

For this listener, the missing inbound rule explains the failure. With the rule the same listener was reachable, and
without it was not. A native Windows process in a Yard Scope Publication needs an inbound rule that the user adds.

This does not test every program, profile or Windows Firewall policy.
