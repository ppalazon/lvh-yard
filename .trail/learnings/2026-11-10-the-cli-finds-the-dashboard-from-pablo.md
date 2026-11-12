---
title: "The CLI finds the dashboard from init's .env"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-f8276729
scope: repo
status: active
replaces: l-cbc65a15
src: agent
touches:
  - cli/internal/command/dashboard.go
  - cli/internal/command/dashboard_test.go
---

## Decided
- Options.DashboardAddress() picks the address in this order: --dashboard, YARD_DASHBOARD_URL, the .env in the Workstation Folder (YARD_CONFIG_DIR, else XDG_CONFIG_HOME/lvh-yard, else ~/.config/lvh-yard), then http://127.0.0.1:7780.
- From the .env, YARD_DASHBOARD_BIND_ADDRESS 0.0.0.0 becomes 127.0.0.1, as the dashboard BindAddress does. A missing setting falls back to 127.0.0.1 or 7780, the same defaults as config/compose.yaml. A missing or unreadable .env falls back to the default address, without an error.

## Watch out
- On Linux, a connection to 0.0.0.0 reaches loopback. So a test where a command succeeds with 0.0.0.0 in the .env would pass without the mapping. Check the address that the command uses, not only its success.
- A test must set YARD_CONFIG_DIR, or HOME, to an empty temp folder, so it never reads the real ~/.config/lvh-yard/.env.
