---
title: "A headless Chromium profile caches app.css, so screenshots of the dashboard need a fresh profile"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-1be1100e
scope: repo
status: active
replaces: l-65f75806
src: agent
touches:
  - dashboard/src/main/css/app.css
---

## Watch out
- A headless Chromium profile caches app.css, so use a fresh one for screenshots.

In the code: dashboard/src/main/css/app.css, which mise run css builds into the static app.css.
