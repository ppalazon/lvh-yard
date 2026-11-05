# 01: Phyxor theme with system fonts

**What to build:** The dashboard shows the colors, radius and shadows of the tweakcn theme Phyxor, in light and dark. It uses only the system fonts and loads no font. See `.scratch/dashboard-ux/spec.md`, user stories 1 to 6 and 42.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] The stylesheet source sets the Phyxor color variables over Basecoat's, for the light theme and for the dark theme.
- [ ] The stylesheet takes Phyxor's radius and shadow values.
- [ ] The sans-serif stack is `ui-sans-serif, system-ui, sans-serif`. The monospace stack is `ui-monospace, monospace`.
- [ ] The stylesheet has no font `@import`, no `@font-face`, and no font name such as Inter, JetBrains Mono or Source Serif.
- [ ] The green success badge stays.
- [ ] A risk alert style exists. Its orange tone differs from the Phyxor primary amber, in the light and in the dark theme.
- [ ] The theme toggle still works.
- [ ] The built stylesheet is generated again with the CSS build.
- [ ] A dashboard test reads the served stylesheet. It finds the Phyxor variables for both themes, and no font import, `@font-face` or named font.
