---
title: "Import folds into register container, so the word import is free for YAML files"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 13, tokens: 84893 }
id: l-bfa26ee3
scope: repo
status: active
src: agent
touches:
  - GLOSSARY.md
  - ".scratch/navigation-and-register/spec.md"
---

## Decided
- The user wanted `import` removed if it was the same as `register --container`. It was not the same. Import needs a running container that no Binding follows, guesses the Binding kind, port and Service name, and publishes a new Service. So Import was folded into `register container <container>` instead of removed.
- Both kinds of register (static and container) now publish a new Service with Local Scope unless the user opts out, so the two behave the same.
- The word "import" is kept free for a later export/import of Services in YAML files.

## Ruled out
- A hidden alias for the bare `lvh-yard register` or for `lvh-yard import`: LVH Yard has no release yet.

## Watch out
- Neither Tailwind nor Basecoat 1.1.0 has a success badge. The user asked for one, so `app.css` adds its own `success` variant.

In the code: spec and tickets in .scratch/navigation-and-register/. Endpoint becomes POST /api/workstation/containers/{container}/register.
