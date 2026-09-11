# AI-assisted independent attack - setup and method

This file documents the SETUP and METHOD (metadata) of the independent attack described in the thesis
(section 8.9). The EVIDENCE is each attacking agent's own output under `run-A-baseline/` and
`run-B2-hardened/`: `EVIDENCE.log` is the raw command + output stream the agent produced itself,
`REPORT.md` is the agent's own verdict, and `run-B2-hardened/BruteForce.java` is a helper the agent
wrote during the run. The thesis author did not author these evidence files; they are copied verbatim.

## Attacker

- An autonomous large-language-model reverse-engineering agent (Claude, Opus 4.8), run on 2026-09-09
  with a fresh, isolated context and no knowledge of the protection design. It was agentic: it drove
  `java`, `javap`, `unzip`, `xxd` and `python` itself and decided its own steps.
- Level-2 threat profile enforced by the brief: off-the-shelf tools, static analysis, running the app,
  small patches, and (in the hardened run) a self-written brute-force helper. Forbidden: a custom
  devirtualizer/emulator, native debugging, and runtime reflection into the VM internals.

## Why two isolated runs (per-target isolation)

The unprotected build (C0) and the protected core (C1) share the same `checkLicense` algorithm, so an
attacker holding C0 in the same sandbox as the protected target could simply read the algorithm off C0
and forge the value - a contamination that would not exist for a real attacker of the shipped product.
Each build is therefore attacked in its own sandbox containing only what a real attacker of that build
would have:

- **run-A-baseline** - only `license-demo.jar` (C0, unprotected) plus `build-a.jar` (a real virtualized
  build made before the anti-dump layer, i.e. a plaintext VM blob inside the jar). This measures the
  unprotected case and the diversification-without-encryption case.
- **run-B2-hardened** - only the shipped hardened product: `license-demo-vm.jar` (virtualized +
  diversified + anti-dump + node-locked), the compiled `PremiumApp.class`/`Feature.class`, and the sealed
  `feature.enc`, produced by the vendor `FeatureGen` step. No C0 and no `build-a` are present (verified:
  the agent's log references no out-of-sandbox path). This is result-binding on the real virtualized
  method.

## Independence and its limits (threat to validity)

The sandboxes were placed outside the code repository and the agent was instructed to stay inside them;
after each run the log was checked to confirm it referenced no out-of-sandbox path. The isolation is
procedural (fresh context, no design knowledge, restricted sandbox), not a hardware air-gap: the agent
ran in an isolated working directory on the author's machine. Runs are non-deterministic; the full
command/output transcripts are committed here so the runs can be inspected and re-attempted.

## How the hardened run came to be (honest record)

An initial run against the pre-patch product revealed a real weakness: the result-binding value was
low-entropy because `checkLicense` returns its result modulo 100000, so the feature key
`SHA-256("feature:" + value)` was brute-forceable - about 100000 candidates were tried against the
AES-GCM tag in under a second, recovering the value and unlocking a pirate serial. Virtualization and
anti-dump themselves held (the algorithm and constants stayed hidden); only the low-entropy binding fell.

That finding motivated a code fix in `demo/result-binding-real/`: the feature key is now
`SHA-256( 8-byte off-machine seed || "feature:" + value )`, where `Feature.seed()` reads the 64-bit seed
the license server delivers. The key therefore depends on the off-machine seed - the "one seed, three
roles" of thesis section 4.5, which had not previously been realised in code. `checkLicense` and its
`%100000` are unchanged (the residual serial-collision point stays a minor documented limitation).

The two runs published here are the unprotected baseline (**run-A-baseline**) and the hardened product
(**run-B2-hardened**). The pre-patch run is retained privately as the internal record of why the fix was
made; it is not part of this public evidence set because the thesis presents and evaluates the hardened
system.

## Results (agent-driven; see each EVIDENCE.log / REPORT.md)

- **run-A-baseline** - C0 SUCCESS (`javap` reads `checkLicense` directly); `build-a` SUCCESS (the agent
  self-extracted the plaintext `cats.meow` and read all constants 7919 / 31 / 17 / 100000). The
  unprotected build and diversification-without-anti-dump both fall.
- **run-B2-hardened** - T-logic FAIL (only the `ObzcureVM.virtualize/execute` shell is recoverable),
  T-blob FAIL on constants (the blob is AES-GCM ciphertext; only version/length/IV structure is visible),
  T-unlock FAIL. The agent disassembled `Feature.class`, saw the key now needs a 64-bit off-machine seed,
  wrote its own `BruteForce.java`, tried 1,600,016 combinations (candidate values x eight seed guesses),
  and had every one rejected by GCM authentication. Its own conclusion: a 2^64 search is infeasible and a
  fake local server cannot help, so under the Level-2 constraints the hardening holds. The node-lock also
  caused the jar to refuse to run on the attacker host.

## What this demonstrates (honest reading)

The baseline confirms, as concrete attacks, that an unprotected build and a diversified-but-unencrypted
build both leak their logic. The hardened run shows the shipped product resisting a static Level-2
attacker across all three goals. The evidence is asymmetric by nature: a SUCCESS is strong proof of a
weakness, whereas a FAIL is only evidence of resistance within the stated Level-2 constraints, not a
proof of absolute security. A licensed or Level-3 attacker who already holds the seed can recompute the
value (the Barak boundary, out of scope). This independent attack complements, and does not replace, a
human-subject reverse-engineering study.
