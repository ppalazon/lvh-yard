# 07: Backups hold Services and their order

**What to build:** A Backup holds every Service of this Workstation with its Instance Sources in order, its toggle and its Aliases. An import gives back the same Routes. See `.scratch/service-workflow/spec.md`, user stories 74 to 77.

**Blocked by:** 03

**Status:** done

- [x] A Backup holds each Service, empty ones included, with its order and toggle.
- [x] Each Alias sits inside its Service. Foreign Instances stay out of the order.
- [x] The Backup format version goes up. An older file is refused with a clear reason.
- [x] An import restores the order and the toggle. It refuses an Alias to the importing Workstation, as today.
- [x] A round trip of export, remove everything, import and export gives an equal file, apart from `exported-from`.
- [x] A unit test of the format and component tests of export and import cover each rule above.
- [x] `docs/user-guide.md` describes the new Backup entries.
