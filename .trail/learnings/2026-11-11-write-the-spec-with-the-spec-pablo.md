---
title: "Write the spec with the spec skill, not by hand, so seams and stories exist"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-603f3988
scope: repo
status: active
replaces: l-a1f797cc
src: agent
touches:
  - docs/agents/issue-tracker.md
  - GLOSSARY.md
---

## Watch out
- Write the spec with the spec skill, not by hand, because a hand-written spec skipped the user stories, the agreed test seams and the template, and the ticket-splitting step relies on all three. It was rewritten properly.
- The flow is /grill-with-docs, then /to-spec, then /to-tickets, then /implement. A spec goes in .scratch/<feature-slug>/spec.md and its tickets in .scratch/<feature-slug>/issues/, as docs/agents/issue-tracker.md says. The glossary is GLOSSARY.md, not CONTEXT.md.
