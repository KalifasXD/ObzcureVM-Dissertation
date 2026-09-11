# 05 - License server, node-lock, and TLS

A Spring Boot license server delivers the per-build seed only after a valid license, over TLS, bound to the
requesting machine. Server-first and fail-secure: the jar alone (diversified + encrypted, no seed) is inert.

## What the screenshots prove
- **13, 14, 15** - seed delivered after a valid license (DiffTest pass); wrong/no license -> refused ->
  locked; the build auto-registers its own seed end-to-end.
- **16** - Vlic node-lock: machine A activates and works, machine B with the same key is refused (403),
  machine A still works. License sharing is blocked.
- **17a / 17b** - V8 TLS: the seed exchange over HTTPS passes; without the pinned truststore the handshake
  fails (real certificate validation, not trust-all); plaintext to the TLS port is refused.
- **25** - F5: `/register` requires a vendor token (missing/wrong gives 403), so an attacker cannot reset a
  license's node-lock binding.
- **26** - F6+F7: a release build enforces HTTPS with a pinned truststore and suppresses the failure cause
  (no failure-mode oracle).
- **27** - the three distinct refusal reasons all return an identical opaque 403 to the client (no oracle).

## Reproduce
The seed/TLS/node-lock behaviour is exercised by the JabRef and RQ2 drivers, which start the server
themselves. See [`../../README-DISSERTATION.md`](../../README-DISSERTATION.md) sections 3 and 3a (TLS material is regenerated with
`keytool`, not committed).

## Honest boundary
Self-signed cert, `changeit` keystore, client-computed fingerprint = proof-of-concept (production
externalizes the keystore/CA cert and would use attestation/TPM or fingerprint-in-key). TLS closes the
network-sniff vector only; an on-machine memory attacker is unaffected.
