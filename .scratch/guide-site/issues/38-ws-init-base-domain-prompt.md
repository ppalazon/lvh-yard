# 38: The `ws init` prompt suggests a Base Domain whose HTTPS certificate browsers reject

**What to build:** Stop `lvh-yard ws init` and its help text from suggesting `localhost` as the Base Domain without a warning. A Base Domain of one label gets an HTTPS certificate that `curl` and Chromium reject, even when they trust the Workstation CA. Change the text, and make `lvh-yard ws tls https create` warn about it.

**Blocked by:** none

**Status:** needs-triage

**Core for v0.2.0:** no

## Problem

On 2026-11-22, on Linux, with a throwaway Workstation (Base Domain `localhost`, one Python app and one Docker container as Services):

- `lvh-yard ws tls ca create` and `lvh-yard ws tls https create` succeed. The certificate names `*.localhost`, `localhost` and `127.0.0.1`, and the chain verifies with `openssl s_client`.
- `curl --cacert ca/ca.crt https://front.localhost:<port>/` fails with "subjectAltName does not match hostname front.localhost".
- Chromium, with the CA in a temporary trust store, fails with `ERR_CERT_COMMON_NAME_INVALID`.
- Both clients refuse a wildcard that has only one label after the star.
- A Base Domain of two labels works: `lvh.me` (`*.lvh.me`) and `dev.localhost` (`*.dev.localhost`) were accepted by both clients for both Services, and a `Secure` cookie that the app set over HTTPS came back on the next request.
- A certificate with one `--address <service>.localhost` for each Service also works.
- Firefox was not tested.

The prompt text says "If you are offline, or your router blocks such answers, use localhost." The guide page "Pick your Base Domain" tells the reader to use `dev.localhost` instead, and says so after the quoted prompt.

## Where the text lives

- `cli/internal/command/init.go`: `baseDomainRule`, `baseDomainExplanation`, the `--base-domain` flag usage, and the `Note` of the help text.
- `cli/internal/command/help_text_test.go` and `help_commands_test.go`: the rules for help text. Run them after a change.
- `cli/internal/command/ws_tls_https.go`: `https create`, where a warning would go. Its help text says "Names \*.<Base Domain>, localhost, 127.0.0.1 and each --address."
- `docs/user-guide.md`, "Running on a Workstation": the same suggestion.
- `docs/development.md` and `dev/workstations.sh`: the test Workstation ws-b uses `localhost`. It can stay as the fixture for HTTP, or move to `dev.localhost`.

## Options

1. **Change the text.** Suggest `dev.localhost` in the prompt, the flag help and the user guide. Cheap, and it fixes new Workstations.
2. **Warn in `https create`.** When the Base Domain has one label, print a warning that browsers and `curl` reject the wildcard, and name the two ways out: a Base Domain of two labels, or one `--address <service>.<Base Domain>` for each Service. This also protects Workstations that already use `localhost`.
3. **Refuse a Base Domain of one label.** Rejected: it breaks HTTP-only use of `localhost`, and the ws-b test fixture.
4. **Add the Service names to the certificate automatically.** Rejected: `https create` does not know the Services that will exist later, so the certificate would go stale at each new Service.

The recommendation is options 1 and 2 together.

## Acceptance

- [ ] The maintainer chose the options.
- [ ] The `ws init` prompt, the `--base-domain` help and the `Note` of the help text name a Base Domain of two labels for offline use, and do not suggest `localhost` alone.
- [ ] `lvh-yard ws tls https create` warns when the Base Domain has one label. A test covers the warning, and a test shows that two labels print no warning.
- [ ] `mise run test:unit` passes, including the help text rules.
- [ ] `docs/user-guide.md` follows the new text.
- [ ] When this lands, the guide page "Pick your Base Domain" drops the sentence after the quoted prompt that tells the reader to enter `dev.localhost` instead.

See `../spec.md` for the decisions of the guide. This ticket comes from the verification of ticket 06.
