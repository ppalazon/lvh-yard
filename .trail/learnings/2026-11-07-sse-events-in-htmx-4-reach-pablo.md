---
title: "SSE events in HTMX 4 reach elements only with a from:body trigger"
author: Pablo
date: 2026-11-07
branch: integration/reactive-dashboard
cost: { minutes: 1, tokens: 2019 }
id: l-db067c92
scope: repo
status: active
src: agent
---

## Decided
- Each refreshed region and the status dots listen with "<event> from:body". The body holds the SSE stream, and the hx-sse extension of HTMX 4 fires the named event there, with no sse: prefix.
- The body uses hx-sse:connect. The old sse-connect name still works but prints a warning in the console.

## Tried and ruled out
- hx-trigger "sse:<event>", as the reactive-dashboard spec says: that is HTMX 2 syntax. In HTMX 4 nothing fires an event with that name, so regions refreshed only on the 30 second fallback.

## Watch out
- An event fired on the body does not go down to child elements. A trigger without from:body never hears it.
- The closed spec in .scratch/reactive-dashboard still says sse:<event>. It records past work.

In the code:
- Layout.onEvent(event) builds the trigger. Layout.refreshed and Layout.statusDots use it.
- dist/ext/hx-sse.js in the htmx.org 4.0.0 WebJar: htmx.trigger(element, message.event) on the connecting element.
