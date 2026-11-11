---
title: "The container watcher syncs first, then asks Docker for events since just before, because Docker answers the events request only once an event happens"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 19, tokens: 194834 }
id: l-519bce7c
scope: repo
status: active
replaces: l-7093947e
src: agent
touches:
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/ContainerBindingsComponentTest.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/docker/DockerSettings.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/docker/Docker.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerBindings.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerWatcher.java
---

## Decided
- The dashboard talks to Docker through docker-java's zerodep transport only, and reads the Engine API's JSON itself, because Testcontainers already uses that transport and docker-java-core brings heavy dependencies.
- The watcher syncs every Container Binding first, then asks for container events with since set 2 s before that sync. Docker replays what happened in between, so nothing is missed. An event seen twice only syncs again.

## Tried and ruled out
- Asking for events first, then syncing: Docker sends the headers of /events only once the first event happens, so the request blocked, and the startup sync never ran while Docker was quiet. It hung after a dashboard restart.

## Watch out
- Testcontainers' withCommand(String) splits on spaces, so sh -c "<line>" must go in the entrypoint as one argument.
- Start the watcher thread only after storing it, because the thread checks that it is still the watcher, and a thread started first can see the old value and quit.

In the code: services/ContainerWatcher.java (watch, CLOCK_MARGIN), docker/DockerEngine.java.
