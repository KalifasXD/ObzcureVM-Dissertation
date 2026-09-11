# 07 - Source-agnostic integration on JabRef (a real third-party app)

The protection is applied to JabRef, not just the `checkLicense` demo, to show the injection is
source-agnostic. ASM injects `invokestatic LicenseEnforcer.enforce()` at the start of JabRef's main; only
a minimal guard is virtualized (whole-app virtualization is neither the goal nor feasible).

## What the screenshots prove
- **28** - JabRef 4.3.1, headless: the injected `enforce()` runs the full release path (server seed over
  TLS + node-lock + result-binding). Valid serial 4321 unlocks, pirate 9999 locks, no-license locks.
- **29** - JabRef 5.7, live GUI (modular JavaFX): injection via `--patch-module`, the guard on the
  classpath. A valid license lets the real JabRef window open under the full protection chain.
- **30** - JabRef 5.7 GUI, pirate serial: `enforce()` fails result-binding, the app refuses to run, exit 1,
  no window (fail-secure before the GUI launches).

## Files
- `jabref-inject.EVIDENCE.log` - the headless 4.3.1 run (screenshot 28).
- `jabref-inject.DESIGN.md` - the injection design (offsets, frame-neutrality, module handling).

## Reproduce
`bash demo/jabref-inject/run_jabref_inject.sh` (headless);
`bash demo/jabref-inject/gui/run_jabref_gui.sh {valid|pirate|nolicense}` (GUI, native Windows only).

## Honest boundary
The attack x configuration matrix is measured on the `checkLicense` demo method; JabRef shows that the
injection and the whole chain apply to a real application. The sealed payload is a self-contained marker,
not wired to a specific JabRef feature.
