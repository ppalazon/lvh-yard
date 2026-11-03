---
title: "Code comments say only what the code cannot: why, a hidden rule, or a value the type does not show"
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 1, tokens: 0 }
id: l-5a3a69df
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
---

## Decided
The user approved a short comment style. A comment that repeats the name of a class, method, field or command is deleted. A command class gets no Javadoc that repeats its picocli description. A record gets no @param that only restates the field name. What stays: why something is done, a rule or default the code does not show (such as a field left out of the JSON when null), the values a String field can take, and facts about Consul, Docker or Traefik that a reader cannot guess.

## Watch out
Keep comments that explain a timeout, a workaround, or an order that matters. Those were the reason to keep a comment in this cleanup, not its length.

In the code: for example cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java (the ANSWER_TIMEOUT comment stays, the comments on ownService and unbind went), dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java and dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java. The CLI went from 341 to 156 comment lines, the dashboard from 1442 to 621.
