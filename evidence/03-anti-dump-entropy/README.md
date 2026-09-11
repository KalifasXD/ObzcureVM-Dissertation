# 03 - Anti-dump (encrypt-at-rest) and blob entropy

The VM program `cats.meow` is encrypted at rest with AES-256-GCM (authenticated, so tampering is detected
for free). The key = SHA-256 over the off-machine seed (and, after F2, the hardware fingerprint).

## What the screenshots prove
- **10** - anti-dump is behaviour-neutral (1,000,000 differential inputs, 0 mismatches after encryption).
- **11** - V3 closed: a raw blob dump that previously leaked 7919 and the class/method names now shows
  only ciphertext.

## Files
- `entropy.EVIDENCE.log` - the rigorous replacement for the "distinct byte count" smell test (thesis
  section 7.2): the encrypted blob measures about 7.98 bits/byte with a chi-square below the 0.05 critical
  value (statistically indistinguishable from uniform), versus 4.28 bits/byte and a very large chi-square
  for the plaintext VM program. No screenshot; reported as prose in the thesis.

## Reproduce
`bash demo/entropy/run_entropy.sh`; `demo/BlobInspect.java` for the before/after leak check.

## Honest boundary
Security rests on AES-256-GCM being a standard authenticated cipher; the entropy figure characterises the
output, it is not itself a proof of cryptographic strength.
