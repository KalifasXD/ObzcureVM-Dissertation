# F4 - result-binding on the REAL consuming edge

**Audit finding F4 (HIGH):** result-binding was absent from the real path. In the base demo `main` just
printed `checkLicense` and nothing consumed it, and the standalone `demo/result-binding` keyed its feature
off `Vault.licenseValue` - a *plaintext re-implementation* of the license arithmetic that never used the
seed or the virtualized method. So "one seed does three jobs" was really two: the result-bound value (job
#3) was not tied to the protected computation.

This demo closes that: the premium feature is sealed under the value of the **actual** `checkLicense`, and
the app derives its decrypt key from the **virtualized** `checkLicense` loaded out of `license-demo-vm.jar`.

## What runs (`run_result_binding_real.sh` -> `EVIDENCE.log`)

- **`FeatureGen`** (vendor build step): loads `checkLicense` from the *original* `license-demo.jar`, computes
  `checkLicense(4321)` = the licensed value, and AES-256-GCM-seals `"PREMIUM: all features enabled"` under a
  key derived from it -> `feature.enc`. No re-implementation: the seal is tied to the real method.
- **`PremiumApp`** (the consuming edge): loads `checkLicense` from the *virtualized* `license-demo-vm.jar`,
  invokes it (this runs inside ObzcureVM and needs the off-machine seed), derives the key from the returned
  value, and decrypts `feature.enc`. There is **no boolean gate** - the feature *is* the decrypted payload.
  - valid serial `4321` -> `checkLicense[virtualized]` returns the licensed value -> **UNLOCKED**.
  - pirate serial `9999` -> a different value -> wrong key -> **LOCKED** (feature stays sealed).

## Why this is the fix (composition)

Result-binding removes the single cheap point of failure. There is no `if (isLicensed)` to flip: to unlock a
pirate serial the attacker must make `checkLicense(9999)` return `checkLicense(4321)` - i.e. reproduce the
output of the method that virtualization + per-build diversification + AES-GCM-at-rest + off-machine seed +
fingerprint node-lock all protect. Patching the VM blob only corrupts the decode; it never produces the
licensed value. So F4 forces the attacker off the cheap edge and onto the hard virtualized target - and it
also shows the anti-debug guard's fix direction (F3): make the guard's outcome *feed* a value the app needs,
rather than being a boolean a NOP can delete.

## Ablation row (Chapter 6 - extends the V4 gate-vs-bound row onto the real path)

| target | tier | boolean gate (base) | result-bound on the REAL virtualized method (F4) |
|---|---|---|---|
| unlock a pirate serial | 2 | flip one branch byte -> unlocked | must reproduce `checkLicense`'s licensed value = defeat the virtualized/diversified/encrypted/seeded/node-locked computation; a patch only breaks decode, never unlocks |

## Boundaries (eval chapter)

- **checkLicense is modular** (`% 100000`), so serials can collide on the same output value; this demonstrates
  the *result-binding mechanism*, not a collision-resistant licensing scheme (a real deployment would use a
  wider, injective licensed value / a MAC over the serial).
- **Dev build only to supply the seed.** `DEV_MODE=true` + `-Dobzcure.seed` lets `PremiumApp` invoke the
  virtualized method offline (standing in for a licensed user's server-delivered seed); the binding itself is
  unchanged in a release build (the seed then arrives from the license server).
- **Dynamic in-process attacker** with a valid license still sees the decrypted feature in memory at runtime
  (Level-3 / native boundary, unchanged). F4 removes the cheap boolean-patch bypass; it does not defeat a
  runtime memory dump by an already-licensed attacker.
- Reproduce: dev build (`DEV_MODE=true`, `mvn clean package -DskipTests`), then
  `bash demo/result-binding-real/run_result_binding_real.sh && cat demo/result-binding-real/EVIDENCE.log`.
