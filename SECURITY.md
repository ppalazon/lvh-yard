# Security policy

## Report a vulnerability

Do not open a public issue for a vulnerability. Use GitHub's private report
instead: open the **Security** tab of this repository and choose **Report a
vulnerability**.

Say which version of `lvh-yard` and of the dashboard image you use, and how to
reproduce the problem. You get an answer within 7 days. Fixes go into the next
release, and the release notes name the problem once a fix is out.

Only the latest release gets fixes.

## What LVH Yard does not protect

LVH Yard is a development tool for a network that you trust. Read these facts
before you use it:

- The dashboard has no login. Anyone who reaches it can change the Services of
  that Workstation, and can reach its containers through the Docker socket.
  By default it listens on `127.0.0.1` only.
- Traefik listens on `127.0.0.1` by default. A Scope of Yard gives a Route to
  every Workstation, but it does not limit who can connect to the Instances.
- The Workstations of a Yard share one Gossip Key. Anyone who has it and can
  reach a Workstation on the LAN can join the Yard.
- The reference setup does not turn on Consul ACLs.
- A Backup holds the basic-auth hashes of your Route Profiles. Keep it private.

A report about one of these facts is not a vulnerability. A report about a
way around them is, for example a request that reaches the dashboard when it
listens on loopback only.
