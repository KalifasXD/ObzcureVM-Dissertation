# 01 - Core virtualization, diversification, and direct attacks

The technical core: license-critical methods are lifted into the ObzcureVM instruction set, the opcode
encoding is permuted per build from a secret seed (Fisher-Yates), and the seed is delivered off-machine.

## What the screenshots prove
- **01, 02** - the virtualizer builds and virtualization is semantics-preserving (1,000,000 differential
  inputs, 0 mismatches).
- **03a / 03b** - V1 static decompilation (Jadx): the unprotected jar leaks the full logic including the
  constant 7919; the virtualized jar shows only the dispatch shell.
- **04** - two builds of the same method produce different `cats.meow` blobs (diversification is real).
- **05** - the shipped jar is inert without the off-machine seed and runs once the seed is supplied.
- **06** - V2 dynamic in-process recovery: an attacker running inside the licensed process reflects the
  decoded VM node array and recovers all 27 opcodes. This is why the anti-debug / anti-tamper envelope is
  needed (see `../02-anti-debug`).
- **32** - RQ1 quantitative Kerckhoffs proof (thesis section 8.4): 20 builds give 20 distinct
  blobs/seeds/permutations; same-build decode 100% vs cross-build about 0.4% per opcode (theory 1/256);
  0 of 380 build-pairs decode the whole program; P(whole program) cross-build = (1/256)^15.

## Files
- `diversification.EVIDENCE.log` - the RQ1 run (screenshot 32).
- `diversification.seeds.txt`, `diversification.blob_hashes.txt` - the 20 per-build seeds and blob hashes.

## Reproduce
`bash demo/diversification/run_diversification.sh` (RQ1); `demo/DiffTest.java` (semantics);
`jadx -d out <jar>` (V1); `demo/Attacker.java` (V2). See [`../../README-DISSERTATION.md`](../../README-DISSERTATION.md) section 3.

## Honest boundary
Diversification defeats generic/reused tooling, not a per-build dynamic in-process attacker who holds the
running seed (V2). That attacker is raised a tier by the envelope but not eliminated (Barak limit).
