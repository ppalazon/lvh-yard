---
title: "A failed dashboard request logs one line, and YARD_STACK_TRACES turns on the stack trace"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 10, tokens: 43679 }
id: l-588419a5
scope: repo
status: active
replaces: l-b0777abc
src: agent
touches:
  - ".env.example"
  - compose.yaml
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/Problems.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ApiProblems.java
  - dashboard/src/main/resources/application.yaml
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/ProblemsTest.java
  - docs/user-guide.md
---

## Decided
- The user did not want stack traces in the dashboard log for expected failures, such as a Consul timeout. One global advice logs one line for each failed request. If Consul does not answer, it answers 503. Any other exception answers 500.
- YARD_STACK_TRACES=true turns the stack trace on, because the Spring logger variable is long and shows Java class names to users.

## Watch out
- The catch-all handler for Exception must stay last, and ApiProblems must come first (@Order(0)), or the catch-all turns the API's own exceptions into 500.
- Exceptions that already carry a status, and a client that went away, are thrown again from the handler, so Spring handles them and logs no warning.
- Only Consul uses a RestClient, so ResourceAccessException always means that Consul did not answer. If another RestClient is added, the 503 text must change.

In the code: Problems.java, services/ApiProblems.java, application.yaml lvh-yard.stack-traces, ProblemsTest.java.
