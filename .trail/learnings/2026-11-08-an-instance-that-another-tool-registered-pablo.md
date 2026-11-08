---
title: "An Instance that another tool registered in Consul is a Foreign Instance: shown and routed, never edited, removed or backed up"
author: Pablo
date: 2026-11-08
branch: main
cost: { minutes: 7, tokens: 54712 }
id: l-ec838023
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ForeignInstanceTest.java
  - GLOSSARY.md
  - docs/user-guide.md
  - dashboard/openapi.yaml
---

## Decided
- The user found four test-svc Instances that lvh-yard listed but could not remove. Another tool had registered them straight in the Consul agent, with no lvh-yard-instance meta. The user chose the term Foreign Instance (not External, which sounds like Remote, and not Unmanaged).
- An Instance is foreign when it has no Container Binding and no lvh-yard-instance=static meta. The API marks it "foreign": true (left out when false).
- A Foreign Instance gives a Service Warning of kind foreign-instance at level risk, also for an unpublished Service. The message names the command that removes it: docker compose exec consul consul services deregister -id=<id>, on its Home Workstation.
- It counts as a Static Instance in the Instance Priority, so its Route does not change.

## Watch out
- Before this change, every Instance that was not bound was taken as a Static Instance. The Service page then showed Edit and Remove buttons that failed, the Static Instances page listed it, and a Backup exported it, so an import would have made lvh-yard own it. Use Instance.staticInstance() wherever only Static Instances count, not !bound().
- The Backup still names the Service of a Foreign Instance, with publication: none, because the export lists every Service.

In the code:
- Instance.foreign and Instance.staticInstance(); YardReader.instance sets foreign with Registrations.isStaticInstance(meta).
- ServiceWarning.Kind.FOREIGN_INSTANCE and ServiceWarning.foreign; ServicePage row actions, StaticInstancesPage.list, BackupExport use staticInstance().
- GLOSSARY.md (Foreign Instance), docs/user-guide.md (Service Warnings), dashboard/openapi.yaml.
- Tests: ForeignInstanceTest registers straight in the test Consul and deregisters after each test.
