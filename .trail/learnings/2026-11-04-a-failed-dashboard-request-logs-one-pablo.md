---
title: "A failed dashboard request logs one line, and YARD_STACK_TRACES turns on the stack trace"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 10, tokens: 43679 }
id: l-b0777abc
scope: repo
status: active
src: agent
touches:
  - ".env.example"
  - compose.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/Problems.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ApiProblems.java
  - dashboard/src/main/resources/application.yaml
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ProblemsTest.java
  - docs/user-guide.md
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/ConsulProblems.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/consul/ConsulProblemsTest.java
---

## Decided
The user did not want full stack traces in the dashboard log for expected failures, such as a Consul timeout, or for any other failure. One global advice logs one line for each failed request. If Consul does not answer, it answers 503. Any other exception answers 500.
The user found the Spring logger variable (LOGGING_LEVEL_DEV_PALAZON_...) too long and internal. A short YARD_ setting in .env turns the stack trace on, like the other settings.

## Tried and ruled out
Stack traces at DEBUG, turned on through the Spring logging.level variable: the name is long and shows the Java class name to users.

## Watch out
The catch-all handler for Exception must stay last in order, and ApiProblems must come first (@Order(0)). Otherwise the catch-all takes the API's own exceptions and turns them into 500.
Exceptions that already carry a status (ErrorResponse, such as ResponseStatusException or a missing page) and a client that went away are thrown again from the handler. Spring then passes them to its default handling, and logs no "Failure in @ExceptionHandler" warning, because it is the same exception.
Only Consul uses a RestClient, so ResourceAccessException always means that Consul did not answer. If another RestClient is added, the 503 text must change.

In the code: dashboard/.../dashboard/Problems.java, services/ApiProblems.java (@Order(0)), application.yaml lvh-yard.stack-traces: ${YARD_STACK_TRACES:false}, compose.yaml passes YARD_STACK_TRACES, .env.example has #YARD_STACK_TRACES=true, docs/user-guide.md.
