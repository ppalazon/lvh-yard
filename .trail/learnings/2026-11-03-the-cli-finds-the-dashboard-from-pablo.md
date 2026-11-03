---
title: "The CLI finds the dashboard from init's .env"
author: Pablo
date: 2026-11-03
branch: init-05
cost: { minutes: 15, tokens: 49542 }
id: l-cbc65a15
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/ConfigFolder.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
---

## Decided
- Yard.dashboard() picks the address in this order: --dashboard, YARD_DASHBOARD_URL, the .env in ConfigFolder.forReading (YARD_CONFIG_DIR, else the default folder), then http://127.0.0.1:7780. --dashboard has no picocli defaultValue any more, because picocli cannot read a file for a default.
- From the .env, YARD_DASHBOARD_BIND_ADDRESS 0.0.0.0 becomes 127.0.0.1, as the dashboard BindAddress does. A missing setting falls back to 127.0.0.1 or 7780, the same defaults as compose.yaml. A missing or unreadable .env falls back to the default address, without an error.

## Watch out
- On Linux, a connection to 0.0.0.0 reaches loopback. So a test where list succeeds with 0.0.0.0 in the .env would pass without the mapping. The test points the .env at a free port and checks the URL in the error instead.
- yardProcess in YardTest sets HOME to an empty temp folder, so no test reads the real user ~/.config/lvh-yard/.env. A test without a .env cannot tell the default from a real dashboard on 7780, so it accepts either success or an error naming 127.0.0.1:7780.
