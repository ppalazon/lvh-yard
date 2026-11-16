# 08: Join a TLS-protected Yard with manual certificate handoff

**What to build:** Yard members manually transfer the required certificates to or from a Windows Workstation, then join and use a mixed-platform Yard with Consul mutual TLS. Unsupported automated Windows transfers fail before remote changes.

**Blocked by:** 05: Join a mixed-platform Yard; 07: Use local certificates and HTTPS on Windows.

**Status:** done

- [x] Document and demonstrate manual Workstation certificate handoff with Windows as sender and recipient. Transfer only the required certificate material and never the Yard CA private key.
- [x] Install the transferred certificates through supported local commands. The Windows and Linux or macOS Workstations join with Consul mutual TLS enabled and retain working Service discovery.
- [x] Successfully request Yard Publications in both directions after secure federation. Verify the real endpoints and responses rather than only the presence of certificates or registrations.
- [x] On the native Windows CLI, unsupported automated certificate handoff fails before a transfer begins and explains the manual alternative.
- [x] A Unix sender identifies an unsupported Windows destination before running Unix remote preparation commands or modifying remote files.
- [x] Preserve Unix-to-Unix automated transfer behavior and existing target-validation protections. Demonstrate that unsupported transfer rejection leaves both endpoints unchanged.
- [x] Keep private-key ACLs and certificate-role isolation effective after manual import. Manual handoff must not require copying a whole private Workstation Folder.
- [x] Update secure-join and manual-transfer documentation, including sender and recipient responsibilities. Record real mixed-platform mutual TLS and request evidence.
