# 05: The CLI finds the dashboard from init's .env

**What to build:** After changing `YARD_DASHBOARD_PORT` or its bind address in the `.env` that `init` wrote, `lvh-yard list` still reaches the dashboard without `--dashboard`.

**Blocked by:** 01

**Status:** done

- [x] Order: `--dashboard`, `YARD_DASHBOARD_URL`, the `.env` in `YARD_CONFIG_DIR` or the default config folder, `http://127.0.0.1:7780`
- [x] A bind address of `0.0.0.0` is read as `127.0.0.1`
- [x] A missing or unreadable `.env` falls through to the default
- [x] `--help` shows the order
