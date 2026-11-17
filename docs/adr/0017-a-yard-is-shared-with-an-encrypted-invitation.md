---
status: accepted
---

# A Yard is shared with an encrypted invitation

A Workstation starts alone. Four commands change that, and they are the only ones that open Consul to other computers: `ws tls ca create`, `ws yard init`, `ws yard share` and `ws yard join`.

- `ws yard init` opens this Workstation. It needs the CA of `ws tls ca create`: without one, it stops and tells the user to make it first. With one, it signs the Consul certificate of this Workstation, installs it, turns Consul TLS on, sets the LAN address that Consul listens on, and prints the `compose recreate` command. It is the only command that opens a Workstation as the start of a Yard, and running it twice changes nothing. An earlier design did this inside the first `share`. A separate command makes the change visible and lets the user open ports without inviting anyone.
- `ws yard share <workstation>` signs a Consul certificate for the name that it gets, with the CA of this Workstation ([ADR-0016](0016-each-workstation-has-its-own-ca-and-one-is-the-main-ca.md)), and writes one zip file, the Invitation. The Invitation holds the Gossip Key, the CA certificate, the certificate and key of the joining Workstation, the address of this Workstation, and a `README.txt`. The whole zip file is encrypted with AES-256 and a password that travels by another way. It refuses a Workstation that `ws yard init` did not open, and it changes nothing else.
- `ws yard join <invitation>` asks for the password without echo, refuses an Invitation for another name, installs the certificate and key, turns Consul TLS on, and sets the Gossip Key, the Peer and the LAN address.
- `ws yard standalone` makes a new Gossip Key, clears the Peers, turns Consul TLS off, deletes `tls/consul/` and makes Consul listen on `127.0.0.1` again. The CA and what it signed stay.

A new Workstation sets `YARD_BIND_ADDRESS` to `127.0.0.1`, so its Consul ports are closed to the LAN until it runs `ws yard init` or joins. Before this decision, an empty value made Consul listen on the interface of the default route from the first start. Native Windows keeps its LAN address, because Docker Desktop needs it. Every connection between Workstations is encrypted: the Gossip Key encrypts the gossip, and Consul TLS checks each Workstation by its certificate.

These commands are the only way that LVH Yard sets up Consul for a Yard. The earlier manual path is removed: `ws init --gossip-key`, `ws init --peers`, `ws yard join --gossip-key --peers`, `ws tls install consul`, `ws tls ca issue consul`, `ws tls ca export consul` and `ws tls enable|disable consul`. Two ways to do the same job made the setup harder to explain and to test. A setup that these commands do not make, such as a company CA for Consul, is made by editing `tls/consul/`, `consul.d/` and `.env` by hand. `ws init` always makes a new Gossip Key and no Peers.

## Considered Options

- **Keep `share` read-only, with its printed invitation**: replaced. The printed text carried the Gossip Key on a terminal, and the certificate travelled in a second zip file with a second password. One encrypted file carries all three, and a joining Workstation needs one command.
- **A separate `ws yard open` before `share`**: not done. It would split one decision in two. The first share says what it changed and prints `compose recreate`.
- **Create the CA on the first share**: not done. A CA key is a long-lived secret, so it is made by a command of its own, with its own password question.

## Consequences

- `share` changes this Workstation once, and Consul restarts to apply it. The test that said `share` never changes settings is gone.
- Only the Workstation that holds the CA key can share. A joined Workstation has only the public CA certificate, so it cannot invite a third one. A Workstation that trusts the CA of another Yard refuses to share, and asks for `ws yard standalone` first.
- Standalone is final for the old members: they keep the old Gossip Key and a certificate that is still valid, and nothing revokes it. Evicting a member without its cooperation needs a new Gossip Key and a new CA on every remaining Workstation, which this decision does not automate.
- An Invitation is read by 7-Zip, WinZip or Keka, and by `ws yard join`, which needs no unzip. The Windows file explorer cannot open it.
- `ws yard share`, `ws yard join` and `ws yard standalone` ran between a Windows 11 Workstation (an ordinary account, Docker Desktop) and a Linux one, in both directions, with real Consul TLS federation. The hidden password questions ran in a real Windows console too. macOS has not run them.
