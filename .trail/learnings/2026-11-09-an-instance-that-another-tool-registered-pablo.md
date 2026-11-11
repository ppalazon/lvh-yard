---
title: "An Instance that another tool registered in Consul is a Foreign Instance: shown and routed, never edited, removed or backed up"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 3, tokens: 24779 }
id: l-af029e56
scope: repo
status: active
replaces: l-fa3791e2
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Instance.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/SourceKind.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ServiceWarning.java
---

## Decided
- An Instance with no Container Binding and no lvh-yard-instance=static meta is a Foreign Instance. It is an Instance Source of its own in the Instance Priority (SourceKind.FOREIGN). It gives a risk-level Service Warning, also for an Unpublished Service, because LVH Yard cannot remove it.
## Watch out
- Use Instance.staticInstance(), not !bound(), where only Static Instances count. With !bound(), a Foreign Instance once got Edit and Remove buttons that failed, and went into a Backup.

In the code: Instance.foreign and staticInstance, SourceKind.FOREIGN, ServiceWarning.Kind.FOREIGN_INSTANCE, BackupExport.
