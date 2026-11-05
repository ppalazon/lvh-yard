# A calmer dashboard: Phyxor theme, modals, live filters and a Service dashboard

Status: done

Words follow `GLOSSARY.md`. This spec adds the term Service Warning, with its two levels, error and risk.

## Problem Statement

The dashboard works, but it is hard to read. Every page is one long column. Create forms take a large part of each page, under the lists that you came to see. You see them all the time, also on a visit only to read.

The Catalog filters need a click on Apply after each change. A hostname link opens in the dashboard's own tab. If you open a Service, you lose the dashboard.

The Service page shows the Publication, Instance Priority, Route Profiles, Instances, Container Bindings and Reachability one under the other. You cannot see the state of a Service at a glance. Every warning shows in one red box, with the same weight. A Route Profile that does not exist breaks the hostname. A loopback Instance is only something to keep in mind. The page does not show this difference, and the API and the CLI do not either.

## Solution

The dashboard gets the colors, radius and shadows of the tweakcn theme "Phyxor", in light and dark. It uses only the system fonts. It loads no font from Google Fonts or from any other place, and it names no font.

Each create form moves into a modal, which is a window that opens over the page. A button above the list opens it. If the input is wrong, the page comes back with the modal open, your input kept, and the error under the field.

The Catalog filters load the list as soon as you change a select, without an Apply button. The page address keeps the filters, so a reload or a shared link shows the same list.

Every link that leaves the dashboard opens in a new tab and shows a small icon that says so.

The Service page becomes a dashboard. Red alerts at the top show what stops the hostname from working. A closed box under them holds the risks to keep in mind. A row of summary cards shows the state at a glance, and section cards hold the details.

Each Service Warning gets a level, error or risk. The API sends it, and `lvh-yard list` and `lvh-yard publish` print it before each message.

## User Stories

1. As a developer, I want the dashboard in the Phyxor colors, so that it looks calm and clear.
2. As a developer, I want the Phyxor colors in the dark theme too, so that both themes match.
3. As a developer, I want the theme toggle to keep working, so that I can choose light or dark.
4. As a developer, I want only the system fonts, so that the dashboard loads no font from the internet.
5. As a developer on a LAN with no internet, I want the same look, so that nothing breaks offline.
6. As a developer, I want the Phyxor radius and shadows, so that the pages look like one design.
7. As a developer, I want the Static Instances list first, so that no form comes before it.
8. As a developer, I want a "Register a Static Instance" button that opens a modal, so that I see the form only on demand.
9. As a developer, I want an "Add an Alias" button that opens a modal, so that the Aliases list comes first.
10. As a developer, I want the container binding form in a modal, so that the list of containers comes first.
11. As a developer, I want the Backup upload form in a modal, so that the list of Backups comes first.
12. As a developer, I want a Cancel button in each modal, so that I can stop without saving.
13. As a developer, I want the Escape key to close a modal, so that I can stop fast from the keyboard.
14. As a developer, I want the focus inside a new modal, so that I can type at once.
15. As a developer, I want the modal open again after a wrong value, so that I see what went wrong.
16. As a developer, I want my input kept after a wrong value, so that I fix only one field.
17. As a developer, I want each error under its field, so that I know what to change.
18. As a developer, I want Delete, Publish and Import to stay one-button actions, so that a quick action stays one click.
19. As a developer, I want Edit Instance and the Route Profile editor to stay pages, so that a long form has room.
20. As a developer, I want the Catalog to load on a new Workstation choice, so that I do not click Apply.
21. As a developer, I want the Catalog to load on a new state choice, so that I do not click Apply.
22. As a developer, I want the page address to keep the Catalog filters, so that a reload shows the same list.
23. As a developer, I want to share a Catalog link with its filters, so that a teammate sees the same list.
24. As a developer, I want the Catalog refresh to keep my filters, so that the list does not reset.
25. As a developer, I want the Back button to go to the previous filters, so that I can undo a filter.
26. As a developer, I want the Route Profiles "Example" select to stay as it is, so that a profile still starts from an example.
27. As a developer, I want a Service hostname to open in a new tab, so that the dashboard stays open.
28. As a developer, I want an Alias hostname to open in a new tab, so that the dashboard stays open.
29. As a developer, I want an icon on each link to a new tab, so that I know before I click.
30. As a developer, I want links between dashboard pages in the same tab, so that moving around feels normal.
31. As a developer, I want a Service page to get no access to the dashboard tab, so that it cannot change the dashboard.
32. As a developer, I want red alerts at the top of the Service page, so that I see at once what is broken.
33. As a developer, I want one alert for each problem, so that each problem is easy to read.
34. As a developer, I want the red alert "No Instances are registered" on an Unavailable Service, so that I know the cause.
35. As a developer, I want the red alert "No Instance passes its checks" on an Unhealthy Service, so that I know the cause.
36. As a developer, I want a red alert for an unknown Route Profile, so that I know that Traefik refuses the Route.
37. As a developer, I want the risks in one closed box, so that they do not take my attention all the time.
38. As a developer, I want the box to give the count, such as "2 risks to keep in mind", so that I know they exist.
39. As a developer, I want no risks box for a Service without risks, so that the page stays clean.
40. As a developer, I want an open risks box to stay open on refresh, so that it does not close every 5 seconds.
41. As a developer, I want a closed risks box to stay closed on refresh, so that the page keeps my choice.
42. As a developer, I want the risk color to differ from the button color, so that a risk does not look like a button.
43. As a developer, I want a summary card with the Service state, so that I see it at a glance.
44. As a developer, I want a Publication card with hostname, Scope and access log, so that I see how the Service is reached.
45. As a developer, I want a card with the passing Instances out of all Instances, so that I see the Health at a glance.
46. As a developer, I want a card with the Instance kind that routes now, so that I know who gets the requests.
47. As a developer, I want the Publication, Instance Priority and Route Profiles cards side by side, so that I see them together.
48. As a developer on a phone, I want the cards in one column, so that the page fits the screen.
49. As a developer, I want the Publication buttons in the Publication card, so that each action sits next to what it changes.
50. As a developer, I want the tables at full width, so that their columns have room.
51. As a developer, I want the Service page to keep its 5-second refresh, so that the cards show what runs now.
52. As a developer, I want a Remote Service without change buttons, so that I see it but cannot change it here.
53. As a script author, I want a `severity` of `error` or `risk` on each Service Warning, so that my script can tell them apart.
54. As a script author, I want the `severity` field in the OpenAPI document, so that I can generate a client.
55. As a developer, I want `lvh-yard list` to print the level of each Service Warning, so that I see it in the terminal.
56. As a developer, I want `lvh-yard publish` to print the level of each Service Warning, so that I see it after I publish.
57. As a developer, I want the user guide to explain the two levels, so that I know what each one means.

## Implementation Decisions

### Theme

- The stylesheet source sets the Phyxor color variables over Basecoat's, for the light theme and for the dark theme. Basecoat uses the same variable names as shadcn and tweakcn, so no new library comes in.
- It also takes Phyxor's radius and shadow values.
- The sans-serif stack is `ui-sans-serif, system-ui, sans-serif`. The monospace stack is `ui-monospace, monospace`. There is no `@import` of a font, no `@font-face`, and no font name such as Inter or JetBrains Mono. The serif value of Phyxor is dropped.
- The existing green success badge stays.
- A new risk style sits next to it. It uses an orange tone that differs from the Phyxor primary amber, in the light and in the dark theme. Basecoat has no warning alert, so the stylesheet adds one.
- The built stylesheet is generated again from the source with the existing CSS build.

### Modals

- A modal is a native `<dialog>` element with Basecoat's dialog style.
- These forms move into a modal: Register a Static Instance, Add an Alias, the binding form of the Containers page, and Upload a Backup.
- A button above the list opens each modal. The modal has a title, the form, a submit button and a Cancel button that closes it.
- The page shell adds a few lines of script. A button with a data attribute that names a dialog opens that dialog with `showModal()`. On page load, the script opens with `showModal()` each dialog that the server marks as open.
- If a post fails with an input error, the server renders the page with that dialog marked as open. The form keeps the input and shows the error under the field, as it does today.
- The form posts as it does today. HTMX is not part of the modals.
- The Containers binding form keeps its CSS rule that shows only the fields of the chosen kind.
- One-button forms and full edit pages do not change.

### Catalog filters

- The Workstation and State selects send an HTMX `GET` on `change`. The answer replaces only the Catalog list region. `hx-push-url` puts the filters in the page address.
- The Apply button goes away.
- The 5-second refresh of the list uses the filters of the current address.
- A direct load of a filtered address renders the full page with those filters, as it does today.
- The Route Profiles "Example" select does not change.

### External links

- One helper in the shared views makes a link that leaves the dashboard. It sets `target="_blank"` and `rel="noopener noreferrer"`, and adds a small external-link icon from Lucide, hidden from screen readers.
- The Service hostname and the Alias hostname use this helper. Any other link to an `http://` hostname uses it too.
- Links to dashboard pages stay as they are.

### Service Warning level

- `ServiceWarning` gets a required `severity` field. Its JSON values are `error` and `risk`.
- Each Kind has a fixed level. `unknown-route-profile` is `error`. `loopback-instance`, `unpublished-container-port` and `route-profile-auth` are `risk`.
- The OpenAPI document shows the field and its two values.
- The Service state is not a Service Warning. The Unavailable and Unhealthy alerts come from the state on the page only, and the API does not add them to `warnings`.

### Service page

- The page keeps its 5-second refresh region and its on-demand Reachability test.
- At the top are the title, the state badge and the Home Workstation line.
- Next comes the alert stack. It holds one red alert for each `error` Service Warning, and one red alert for the Unavailable or the Unhealthy state.
- Under the red alerts is one `<details>` element for all `risk` Service Warnings, closed by default. Its summary says "1 risk to keep in mind" or "N risks to keep in mind". Each risk is one item inside. If there are no risks, the page leaves out the element.
- The page shell script remembers whether the risks box is open, and sets it again after each HTMX swap of the region. The memory lasts only while the page is open.
- Next is a row of summary cards for State, Publication, passing Instances, and the Instance kind that routes now. The Publication card says "Not published" for an Unpublished Service. The routing card follows the Instance Priority, or says that it is off.
- Next is a grid of section cards: Publication with its buttons, Instance Priority and Route Profiles. The grid has two columns on a wide screen and one on a phone. A card that has nothing to show today is left out, as today.
- The Instances table, Container Bindings and Reachability take the full width, in cards.
- A Remote Service shows the same cards and alerts without the change buttons.

### CLI

- `lvh-yard list` and `lvh-yard publish` print each Service Warning as `error: <message>` or `risk: <message>`.

### Docs

- `docs/user-guide.md` explains the two levels of a Service Warning, the new CLI output, and the risks box on the Service page.

## Testing Decisions

- A good test acts as a user or an API client. It looks only at what they can see: API answers, the HTML of a page, the stylesheet, and CLI output. It never looks at how a view builds its HTML.
- Seam 1 is the dashboard test, through `DashboardTest`, with a real Consul and Docker from Testcontainers.
- Seam 1 covers the `severity` of each Service Warning kind in the API, and the field in the OpenAPI document.
- Seam 1 covers the stylesheet: it holds the Phyxor variables for light and dark, and it has no font import, no `@font-face` and no named font.
- Seam 1 covers each modal page. The page has the open button and a closed dialog that holds the form. After a post with an input error, the dialog is marked as open, and it holds the input and the error.
- Seam 1 covers the Catalog. The selects carry the HTMX change attributes and `hx-push-url`. There is no Apply button. A filtered address still renders the filtered list.
- Seam 1 covers external links: a Service hostname and an Alias hostname have `target="_blank"` and `rel="noopener noreferrer"`. A link to a dashboard page has neither.
- Seam 1 covers the Service page. An unknown Route Profile gives a red alert. An Unavailable and an Unhealthy Service each give their red alert. Risks sit in one closed `<details>` with the right count. A Service with no risks has no risks box. The summary cards show the state, the Publication, the passing count and the routing kind. A Remote Service has no change buttons.
- Seam 2 is the CLI test `YardTest` with its stub dashboard. It covers the `error:` and `risk:` prefixes in `lvh-yard list` and `lvh-yard publish`.
- The repo has no browser test, and this spec adds none. Three things run only in a browser: a modal that opens on click, a filter that loads on change, and a risks box that stays open across the refresh. You try them by hand on the two test Workstations of `mise run dev:up`.
- Prior art: `ServicePageTest`, `CatalogPageTest`, `StaticInstancesPageTest`, `AliasesPageTest`, `ContainersTest`, `BackupsPageTest`, `NavigationBarTest`, `OpenApiTest`, and the CLI's `YardTest`.

## Out of Scope

- A browser test tool such as Playwright.
- Modals for edit pages and for one-button actions.
- New Service Warning kinds, and Service Warnings for the Service state.
- A dashboard layout for the Status page, the Catalog or other pages, apart from the modals and filters above.
- Remembering the risks box state after you leave the page.
- Any font file, served locally or from the internet.

## Further Notes

- Phyxor comes from tweakcn theme `cmq3zh8z1000104i6bwg93nqa`. Its primary color is amber, so the risk tone must stay clearly apart from buttons.
- LVH Yard is not released yet, so the new `severity` field needs no compatibility code for old clients.
