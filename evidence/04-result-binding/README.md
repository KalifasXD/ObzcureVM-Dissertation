# 04 - Result-binding

Principle: the license decision must not be a boolean the app branches on. The licensed computation returns
a value the app consumes (it derives the key that decrypts a needed feature), so there is no gate to flip.

## What the screenshots prove
- **12** - V4: a boolean gate is defeated by a 1-byte `if_icmpne` to `if_icmpeq` patch (pirate serial
  unlocked), whereas the result-bound version cannot be unlocked by patching - the patch only breaks
  decryption. Unlocking requires reproducing the exact licensed value.
- **24** - F4: result-binding wired onto the REAL virtualized `checkLicense`. The feature is sealed under
  `checkLicense(4321)` and the app derives its decrypt key from the virtualized method; the valid serial
  unlocks, the pirate serial locks.

## Files
- `result-binding.EVIDENCE.log` + `result-binding.README.md` - the gate-vs-bound contrast (screenshot 12).
- `result-binding-real.EVIDENCE.log` + `result-binding-real.README.md` - F4 on the virtualized method
  (screenshot 24).

## Reproduce
`bash demo/result-binding/run_result_binding.sh`; `bash demo/result-binding-real/run_result_binding_real.sh`.

## Honest boundary
The feature key is `SHA-256( off-machine seed || value )`, so a Level-2 attacker without the 64-bit seed
cannot forge it (see `../10-ai-attack`). A licensed / Level-3 attacker who already holds the seed can
recompute the value (Barak boundary, out of scope).
