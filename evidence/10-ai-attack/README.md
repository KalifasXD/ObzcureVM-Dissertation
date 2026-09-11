# 10 - Independent AI-assisted Level-2 attack (thesis section 8.9)

An autonomous large-language-model reverse-engineering agent (Claude, Opus 4.8), given a fresh isolated
context and no knowledge of the design, attacked the artifacts with off-the-shelf tools it drove itself.
It substitutes for the human external-attacker step promised in the proposal (a human study remains future
work). The method, isolation and honest limits are in `SETUP_AND_METHOD.md`.

## What the screenshots prove
- **34** - `run-A-baseline`: the unprotected build falls (the agent reads `checkLicense` from `javap`), and
  a diversified-but-unencrypted build falls (the agent self-extracts the plaintext blob and reads all
  constants).
- **35** - `run-B2-hardened`: the shipped hardened product resists all three goals. The agent recovers only
  the virtualize shell, sees the blob is ciphertext, disassembles `Feature.class`, realises the feature key
  needs a 64-bit off-machine seed, writes its own `BruteForce.java`, tries 1,600,016 combinations, and has
  every one rejected by GCM authentication ("a 2^64 search is infeasible ... the hardening holds").

## Files
- `run-A-baseline/` - the agent's own `EVIDENCE.log` (raw commands + output) and `REPORT.md` (its verdict).
- `run-B2-hardened/` - the agent's own `EVIDENCE.log`, `REPORT.md`, and the `BruteForce.java` it wrote.
- `SETUP_AND_METHOD.md` - setup, isolation, the weakness-then-fix record, and honest reading.

## Integrity note
The `REPORT.md` and `BruteForce.java` files are the attacking agent's own output, reproduced without
edits. The `EVIDENCE.log` files are the agent's own command/output transcripts; the only change in this
public copy is cosmetic: in `run-B2-hardened/EVIDENCE.log` the absolute sandbox path (a temporary working
directory on the author's machine) has been shortened to `<sandbox>/`, which is noted at the top of that
file. No command, output, disassembly, verdict, or measurement has been altered; the unmodified raw log is
retained by the author and available on request. An earlier pre-patch run that first exposed the
low-entropy result-binding weakness is retained privately as the internal record of why the fix was made;
it is not published because the thesis presents and evaluates the hardened system.

## Honest boundary
The evidence is asymmetric: a SUCCESS strongly proves a weakness; a FAIL is evidence of resistance only
within the Level-2 constraints, not a proof of absolute security. This complements, and does not replace,
a human-subject reverse-engineering study.
