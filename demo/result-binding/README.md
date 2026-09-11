# Result-binding: why a boolean gate is fragile and a bound value is not

Envelope layer (Tier 1). Demonstrates, with a real 1-byte bytecode patch, why the license decision
must NOT be a boolean the app branches on.

## The two designs

- **`GateDemo`** — a boolean gate: `if (isLicensed(serial)) unlock; else lock;`. The decision is a
  single `if_icmpne` / `ireturn`. One point of failure.
- **`BoundDemo` + `Vault`** — result-binding: the licensed computation returns a VALUE
  (`licenseValue(serial)`), and that value derives the AES key that decrypts the premium feature. The
  feature IS the decrypted payload; there is no boolean.

## Result (verified, JDK-only, reproducible)

Run: `bash run_result_binding.sh && cat EVIDENCE.log`

| | valid 4321 | pirate 9999 | after attacker's 1-byte patch |
|---|---|---|---|
| boolean gate      | UNLOCKED | LOCKED | pirate 9999 -> **UNLOCKED** (attacker wins) |
| result-bound      | UNLOCKED: PREMIUM... | LOCKED | 4321 AND 9999 -> **LOCKED** (patch only breaks it) |

- Gate attack: flip `if_icmpne`(0xA0) -> `if_icmpeq`(0x9F), one byte, verifier-safe. The pirate serial
  unlocks. The attacker never touched the license logic.
- Result-bound attack: the same class of cheap patch (change the constant 7919 -> 7918) only produces
  a WRONG value -> wrong key -> GCM decryption fails -> LOCKED. To unlock an invalid serial the
  attacker must make `licenseValue(9999)` return the exact correct value (136659), i.e. reproduce the
  real computation.

## Why it matters (composition)

Result-binding converts the attack from "find and flip the boolean" (trivial, logic-independent) into
"reproduce the exact secret-dependent value" (requires understanding the computation). That value
computation is exactly what the rest of the envelope protects (virtualization hides the operations,
diversification hides the opcode mapping, encrypt-at-rest hides the operands). So result-binding
removes the cheap single-point-of-failure and forces the attacker onto the hard, virtualized target.

## Honest boundary

This is the design *principle* demonstrated standalone. Integrating it into the real protected app =
wiring the virtualized method's return value into a feature the app needs (as `BoundDemo` does with
the vault), which pairs naturally with the license server phase (the server delivers the value/key
after auth). The standalone demo is the ablation evidence; full app integration is Phase B.

## Files
`GateDemo.java` (boolean gate), `Vault.java` (result-bound value + AES-GCM), `VaultGen.java` (vendor
seal step), `BoundDemo.java` (runtime unlock), `ClassPatch.java` (JDK-only bytecode patcher modelling
the attacker), `run_result_binding.sh` (orchestration), `EVIDENCE.log` (captured run).
