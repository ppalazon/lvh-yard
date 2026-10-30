---
title: "The container watcher syncs first, then asks Docker for events since just before, because Docker answers the events request only once an event happens"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 19, tokens: 194834 }
id: l-7093947e
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainerBindingsTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/docker/DockerSettings.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/docker/Docker.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerBindings.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerWatcher.java
---

## Decided
- The dashboard talks to Docker through docker-java's zerodep transport only (the raw HTTP client for the Docker socket), and reads the Engine API's JSON itself. It is the transport Testcontainers already uses, without docker-java-core's heavy dependencies.
- The watcher syncs every Container Binding first, then asks for container events with `since` set 2 s before that sync. Docker replays what happened in between, so nothing is missed. An event seen twice only syncs again.

## Tried and ruled out
- Asking for events first, then syncing, then reading them: Docker sends the response headers of `/events` only once the first event happens, so the request blocks and the startup sync never runs while Docker is quiet. It looked fine in tests while other containers were starting, and hung after a dashboard restart.

## Watch out
- Busybox `nc -lk` (in the Consul image) still exits after its first connection. A Consul TCP check or the Testcontainers port check is that connection, so a test container that listens with it stops, and its Bound Instance rightly goes away. The test listener loops `nc -l` instead.
- Testcontainers' `withCommand(String)` splits on spaces, so `sh -c "<line>"` must go in the entrypoint as one argument.
- Start the watcher thread only after storing it: the thread checks that it is still the watcher, and a thread started first can see the old value and quit.

In the code: dashboard/.../services/ContainerWatcher.java (watch, CLOCK_MARGIN), dashboard/.../docker/Docker.java (containerEvents), dashboard/src/test/.../TestcontainersConfiguration.java (listener).
