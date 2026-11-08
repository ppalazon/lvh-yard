---
title: "An Instance that another tool registered in Consul is a Foreign Instance: shown and routed, never edited, removed or backed up"
author: Pablo
date: 2026-11-08
branch: main
cost: { minutes: 15, tokens: 29879 }
id: l-fa3791e2
scope: repo
status: active
replaces: l-ec838023
src: agent
touches:
  - GLOSSARY.md
  - dashboard/openapi.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupExport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Instance.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/InstanceKind.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceWarning.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/StaticInstancesPage.java
---

## Decided
- An Instance with no Container Binding and no lvh-yard-instance=static meta is a Foreign Instance. The API marks it "foreign": true. It gives a risk-level Service Warning, foreign-instance, also for an unpublished Service. The message says to deregister it from the Consul agent of its Home Workstation through the HTTP API (PUT /v1/agent/service/deregister/<id>).
- It counts as a Static Instance in the Instance Priority.

## Watch out
- Use Instance.staticInstance(), not !bound(), wherever only Static Instances count. With !bound(), a Foreign Instance got Edit and Remove buttons that failed, was listed on the Static Instances page, and went into a Backup.

In the code:
- Instance.foreign, YardReader.instance with Registrations.isStaticInstance(meta), ServiceWarning.foreign, ServicePage, StaticInstancesPage, BackupExport. Tests: ForeignInstanceTest.
