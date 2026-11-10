---
title: "Code comments say only what the code cannot: why, a hidden rule, or a value the type does not show"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-0982a478
scope: repo
status: active
replaces: l-5a3a69df
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
---

## Decided
The user approved a short comment style. A comment that repeats the name of a class, method, field or command is deleted. A command gets no comment that repeats its help text. A record gets no @param that only restates the field name. What stays: why something is done, a rule or default the code does not show (such as a field left out of the JSON when null), the values a String field can take, and facts about Consul, Docker or Traefik that a reader cannot guess.

## Watch out
Keep comments that explain a timeout, a workaround, or an order that matters. Those were the reason to keep a comment in this cleanup, not its length.

In the code: for example dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java and dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java. The dashboard went from 1442 to 621 comment lines.
