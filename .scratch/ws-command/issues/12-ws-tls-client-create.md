# 12: `ws tls client create`

**What to build:** The user gets a Client Certificate as a `.p12` file for a person, signed by the Yard CA, and installs it in a browser.

**Blocked by:** 09: `ws tls ca create` and `ws tls cert create`

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] `ws tls client create <name>` signs a certificate with the Yard CA key and writes a PKCS#12 file with the key and the chain. The key usage is client only.
- [x] `--password` sets the password. With none, the command makes a random one and prints it once.
- [x] The command fails with a clear message on a Workstation that has no CA key.
- [x] A CLI unit test decodes the file with the password and checks the chain and the key usage. A wrong password does not decode it.
- [x] The new Go dependency for PKCS#12 is in the module files. `docs/user-guide.md` shows how to install the file in a browser.
