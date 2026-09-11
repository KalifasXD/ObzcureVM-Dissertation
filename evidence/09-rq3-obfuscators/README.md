# 09 - RQ3: virtualization vs commodity obfuscators

ProGuard 7.10 and Allatori 9.9 (demo) obfuscate the same `license-demo.jar`; a javap-based static analysis
checks whether the license logic (the constant 7919 and the arithmetic) survives.

## What the screenshot proves
- **33** - ProGuard hides nothing (7919 and the arithmetic are fully recoverable; the jar just shrinks);
  Allatori hides the literal 7919 but leaves the arithmetic structure recoverable; only virtualization
  lifts the whole logic into the encrypted blob, leaving the method body as an interpreter dispatch. The
  difference is of class, not degree. Cost = size (about 67 KB of bundled interpreter runtime).

## Files
- `rq3-compare.EVIDENCE.log` - the comparison run with the per-tool static analysis.

## Reproduce
`bash demo/rq3-compare/run_rq3_compare.sh` (needs ProGuard and Allatori on Windows paths; see
[`../../README-DISSERTATION.md`](../../README-DISSERTATION.md)).
