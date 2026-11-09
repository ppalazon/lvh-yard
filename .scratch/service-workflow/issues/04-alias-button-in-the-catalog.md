# 04: The Alias button in the catalog

**What to build:** On a Remote Service row of the catalog, you click "Alias". You then link that Remote Service to a new or existing Service of this Workstation. See `.scratch/service-workflow/spec.md`, user stories 30 to 34.

**Blocked by:** 03

**Status:** done

- [x] Each Remote Service row with Yard Scope has an "Alias" button. Rows with Local Scope have none.
- [x] The form offers the Services of this Workstation and accepts a new name. The default is the Remote Service's name.
- [x] A new name creates the Service with a Local Scope Publication, and adds the Alias.
- [x] An existing Service keeps its Publication as it is, and the Alias goes to the end of its order.
- [x] The refusals of 03 show at their field.
- [x] `lvh-yard alias add` with a new Service name also creates and publishes it with Local Scope.
- [x] Component tests cover the button, both cases of the form and the refusals. CLI tests cover the new Service case.
- [x] `docs/user-guide.md` explains the Alias button.
