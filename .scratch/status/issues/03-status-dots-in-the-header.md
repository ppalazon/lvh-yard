# 03: Status dots in every page's header

**What to build:** Every page's header shows a small dot for each status part, so the user notices a problem without opening `/status`.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] A `/status/summary` fragment renders one dot per part
- [ ] The layout's header loads it through HTMX every 5s; an error answer keeps the last dots
- [ ] Each dot has an accessible label and a title with its part's name and reason, and its state shows without colour
- [ ] The dots link to `/status`
