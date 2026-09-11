# 06 - Core hardening (F1, F2) and portability

## What the screenshots prove
- **18** - the preview-version lock was removed: the protected output is major-61/minor-0 and runs on any
  JVM >= 17 with no `--enable-preview` flag.
- **19** - ingest ceiling: a Java-21 class is accepted and virtualized while a Java-23 class is rejected in
  `ClassReader` (the active reader is ASM 9.6; Java 23+ needs an ASM bump).
- **20** - F1: the seed was widened from a 32-bit `Random` to a 64-bit `SecureRandom` value, moving the
  offline brute-force target from 2^32 to 2^64; behaviour-neutral (1,000,000/0).
- **21** - F2 (part 1): the hardware fingerprint is mixed into the AES key, so a seed used on the wrong
  machine yields the wrong key -> `AEADBadTagException` (a cryptographic node-lock at the cipher layer).
- **22** - F2 (part 2): in a release build the `-Dobzcure.seed`/`-Dobzcure.fingerprint` overrides are
  compiled out, so the offline one-flag bypass is gone from the shipped bytecode.

## Reproduce
DiffTest with the 64-bit seed (F1); `-Dobzcure.fingerprint=EVIL-MACHINE` (F2 crypto node-lock); a release
build with `DEV_MODE=false` (F2 override strip). See [`../../README-DISSERTATION.md`](../../README-DISSERTATION.md).

## Honest boundary
The blob is bound to the fingerprint present at ENCRYPTION time (build and run share the machine in this
PoC); a build-once/activate-later model needs a server-side seed-wrapping envelope (future work).
