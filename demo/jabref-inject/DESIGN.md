# JabRef Injection Milestone - Design Spec

Status: DESIGN, awaiting user approval. No code written yet.
Date: 2026-08-27.

## 1. Goal and claim

Demonstrate the proposal's lead claim: **source-agnostic injection of a protected critical
method into a real, third-party application**. JabRef 4.3.1 (`jabref/JabRef-4.3.1.jar`,
Main-Class `org.jabref.JabRefMain`) is the target. JabRef is MIT-licensed and has no license
logic of its own, so the "critical part" is not extracted, it is **injected**: a license guard the
app never had. The whole protection pipeline (virtualize -> per-build diversify -> AES-GCM
encrypt-at-rest -> envelope -> off-machine seed -> result-binding) is then applied to that injected
method, and protection is verified on the real artifact with the attack tools already built.

This is the applied counterpart to the demo `checkLicense`; it reuses already-verified patterns
(M1 virtualization, F4 result-binding) rather than inventing new mechanisms.

## 2. What "done" means (the four points)

1. **Source-agnostic**: the guard is inserted into real compiled JabRef bytecode with ASM, no
   JabRef source.
2. **Pipeline applies**: the injected method is virtualized, diversified, encrypted, enveloped, and
   its seed is server-delivered.
3. **Semantics preserved**: the injected method computes identically after virtualization
   (`DiffTest` 1,000,000 / 0 on the method pulled back out of the protected jar).
4. **Valid, runnable program**: the protected jar's structure and manifest are intact; the injected
   and edited classes pass strict JVM bytecode verification (`-Xverify:all`); the injected method
   executes correctly on the real bytecode via our harness. (Full GUI launch is deliberately out of
   scope: the owned JDK8/JavaFX boundary.)

## 3. Scope decisions (locked with the user)

- **Guard-only virtualization**, not whole-app. This was always the goal (protect critical parts,
  not the entire application), and it is independently forced by the toolchain: `Obzcure.java`
  re-emits every class in the input jar with `ClassWriter.COMPUTE_FRAMES`, and
  `CustomClassWriter.getCommonSuperClass` resolves supertypes from the loaded class map only.
  JabRef's type universe is incomplete inside the jar (`JabRefMain extends
  javafx.application.Application`; JavaFX 8 lives in the JRE, not the app jar), so re-emitting the
  full ~30k-class jar would throw on unresolved types and/or OOM. We therefore virtualize only a
  minimal guard, whose types are fully resolvable.
- **Q3 mixer fix applied to the JabRef guard only.** The demo `checkLicense(4321)=36659` stays
  frozen as the M1 / ablation anchor (preserves screenshots 01-26 and all existing evidence). The
  wide injective mixer is introduced here, framed as "the `% 100000` collision boundary was
  identified on the demo (F4) and resolved in the applied milestone".
- **OpenJFX "watch the guard fire at real `main()`" stretch: OUT.** Entry-point wiring is proven
  structurally (`javap` of the inserted call) plus verification plus harness execution on real
  bytecode. Running `main()` to observe the guard is documented as the owned JDK8/JavaFX boundary.

## 4. Architecture overview

```
                 [JabRef-4.3.1.jar]  (untouched input, hashed)
                          |
       (1) INJECT (ASM) --+--> add LicenseGuard.class (pure long math, @ApplyObzcureVM)
                          |    add LicenseEnforcer.class (crypto consuming edge, NOT virtualized)
                          |    insert frame-neutral `invokestatic LicenseEnforcer.enforce()`
                          |      at the entry of JabRefMain.main (rewrite that ONE class, no
                          |      COMPUTE_FRAMES; frame-neutral so the StackMapTable stays valid)
                          v
                 [jabref-injected.jar]
                          |
       (2) SEAL ----------+--> FeatureGen-style: key = derive(LicenseGuard.checkLicense(validSerial)
                          |      from the ORIGINAL guard), AES-256-GCM seal payload -> guard.enc
                          v
       (3) VIRTUALIZE ----+--> feed a MINIMAL jar containing ONLY LicenseGuard to ObzcureVM
                          |      (COMPUTE_FRAMES trivially satisfiable). Output: virtualized
                          |      LicenseGuard + cats.meow + VM runtime classes. Seed from server
                          |      (or dev seed), per-build diversified, blob AES-GCM encrypted.
                          v
       (4) REPACK --------+--> merge virtualized LicenseGuard + cats.meow + VM runtime + guard.enc
                          |      into jabref-injected.jar (replace the plain LicenseGuard)
                          v
                 [jabref-protected.jar]
                          |
       (5) VERIFY --------+--> structural + semantics + PROTECTION (existing attack tools)
                          v
                 EVIDENCE.log  (every step above teed here, timestamped)
```

## 5. Components

Each is a small, independently testable unit with a clear input -> output.

1. **`LicenseGuard`** (the virtualized critical method). Contains only
   `@ApplyObzcureVM static long checkLicense(int serial)` = the 64-bit injective mixer (Section 6).
   Dependencies: `java.lang.*` only. No `invokedynamic`, no Strings, no method calls, so its frames
   are trivially computable and ObzcureVM can virtualize it cleanly. This is the ONLY class fed to
   ObzcureVM.
2. **`LicenseEnforcer`** (the consuming edge, plain, NOT virtualized). `static void enforce()`:
   reads the serial (system property / env for the PoC), calls `LicenseGuard.checkLicense`, derives
   an AES-256 key = SHA-256 of the returned wide value (8-byte big-endian long), AES-256-GCM-decrypts
   `guard.enc`; on success the app
   proceeds, on failure it terminates. Uses `javax.crypto` and Strings (so it must stay OUT of the
   virtualized jar; it is injected as ordinary bytecode). This split keeps crypto/`invokedynamic`
   away from the ObzcureVM `COMPUTE_FRAMES` pass. Mirrors the demo's LicenseDemo(virtualized) vs
   PremiumApp/FeatureGen(plain) separation.
3. **Injector** (new ASM tool). Inputs: JabRef jar, compiled `LicenseGuard`/`LicenseEnforcer`.
   Adds the two classes; inserts `invokestatic LicenseEnforcer.enforce()` at the first instruction
   of `JabRefMain.main`, writing JabRefMain back WITHOUT `COMPUTE_FRAMES` (frame-neutral no-arg void
   call preserves the existing StackMapTable). Output: `jabref-injected.jar` + a log of the exact
   insertion (class, method, offset, javap before/after).
4. **Vendor seal** (reuse F4 `FeatureGen` pattern). Computes `checkLicense(validSerial)` from the
   ORIGINAL (pre-virtualization) guard, seals the payload under a key derived from that value ->
   `guard.enc`. No boolean gate.
5. **Virtualize** (reuse existing ObzcureVM CLI). Runs over the minimal guard jar; produces the
   virtualized guard, `cats.meow`, and the VM runtime, with the seed registered to the license
   server.
6. **Repack** (new, small). Merges virtualized guard + `cats.meow` + VM runtime + `guard.enc` into
   `jabref-injected.jar` -> `jabref-protected.jar`. Verifies no package collisions (VM runtime is
   `obzcu.re.*`, disjoint from `org.jabref.*`) and manifest intact.
7. **Verification phase** (reuse existing attack tools; Section 8).
8. **Driver + logging** (`run_jabref_inject.sh`). Orchestrates 1-7; tees every step, input hash, and
   result into `EVIDENCE.log`. Fails loud and stops on any step error. This is the primary cited
   artifact; screenshots are captured from this run.

## 6. The guard method (mixer spec)

A single fixed 64-bit bijective mixer (SplitMix64 finalizer style), all ops confirmed present in the
interpreter (`VMInsnNode.java`: `LMUL`, `LXOR`, `LUSHR`, ...):

```java
@ApplyObzcureVM
public static long checkLicense(int serial) {
    long z = (serial & 0xFFFFFFFFL) ^ 0x9E3779B97F4A7C15L; // mix in a fixed nonce
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    z =  z ^ (z >>> 31);
    return z; // wide, injective over the serial domain: no % 100000, no collisions
}
```

Rationale (from Q3): virtualization removes the logic regardless of arithmetic complexity, and V2
recovers operands wholesale, so complexity buys no security. What matters is a **wide, injective
output** that fixes the F4 `% 100000` collision boundary and makes the result-binding key unique per
serial. Multiply-by-odd is invertible mod 2^64 and xorshift finalizers are bijections, so the output
is injective with good avalanche. No `invokedynamic`, no Strings, no calls, no division/modulo.

## 7. Injection point

`org.jabref.JabRefMain.main(String[])` is the canonical, guaranteed-executed entry point (it stashes
args and calls JavaFX `launch()`). We insert the enforce() call as the first instruction. The call
is frame-neutral (no-arg, void), so JabRefMain is rewritten without `COMPUTE_FRAMES` and its existing
StackMapTable remains valid (the F3 verifier-safe insertion lesson, used constructively).

Note: JabRefMain cannot be class-loaded under bare JDK 17 (its JavaFX superclass is absent), so we do
not execute `main` here; the wiring is proven by `javap` + `-Xverify:all`, and method execution is
proven via the harness on the extracted guard.

## 8. Evidence and verification plan (what proves which point)

Structural and semantic:
- **Injection point (point 1)**: `javap` of JabRefMain shows the inserted `invokestatic` at offset 0.
- **Transformation (point 2)**: `javap` before/after shows `LicenseGuard.checkLicense` is now a VM
  dispatch shell (`ObzcureVM.virtualize(...); execute()`) inside `jabref-protected.jar`.
- **Semantics (point 3)**: `DiffTest` 1,000,000 / 0 on `checkLicense` loaded from
  `jabref-protected.jar` (original vs virtualized), with the server-delivered seed.
- **Valid program (point 4)**: `-Xverify:all` classload of LicenseGuard + LicenseEnforcer + the
  edited JabRefMain-adjacent path passes; manifest and entry count intact.

Protection, on the real artifact, using tools we already built (a focused slice of the RQ1 ablation
run against JabRef):
- **V1 (Jadx)**: decompile `jabref-protected.jar`; `LicenseGuard.checkLicense` = dispatch shell,
  mixer gone. Contrast with the unprotected guard showing the full mixer. Static protection on JabRef.
- **V3 (`BlobInspect`)**: inspect the `cats.meow` now inside `jabref-protected.jar`; AES-GCM
  ciphertext, no plaintext constants/names. Anti-dump on JabRef.
- **V4 / F4 (result-binding)**: valid serial reproduces the wide value -> payload unlocks; pirate
  serial -> wrong value -> payload stays sealed; a bytecode patch corrupts decode, never unlocks.
- **Off-machine seed**: the guard in `jabref-protected.jar` is inert without the server-delivered
  seed (fetch-then-run vs no-seed lock).

All of the above tee into `EVIDENCE.log`; the thesis screenshots are captured from this run and
extend Chapter 5.7 (injector) and the Chapter 8 JabRef rows from plan (ΣΧΕΔΙΟ) to results.

## 9. Boundaries and honest limitations

- Whole-app virtualization is impractical with this toolchain (Section 3); we protect a focused
  injected guard, consistent with the "only critical methods are virtualized" framing. Stated as
  design intent, not a defeat.
- GUI not launched (JDK8/JavaFX). Entry-point wiring proven structurally + by verification, method
  execution proven on real bytecode via the harness.
- Result-bound payload is guard-internal (self-contained), not wired into a specific JabRef feature.
  Demonstrates the mechanism on the real app, not a productized feature gate.
- Dynamic in-process attacker with a valid license still reads the decrypted value from memory
  (Level-3 / native boundary, unchanged).
- PoC seed/serial supplied via property for the offline run; the release model delivers the seed
  from the license server.

## 10. Feasibility risks to validate during implementation

1. **ObzcureVM on the minimal guard jar**: confirm the guard-only jar virtualizes cleanly (expected,
   pure `java.lang` math). If `enforce()` were ever co-located in the virtualized jar, its
   `javax.crypto` frames could stress `COMPUTE_FRAMES`; the two-class split avoids this by design.
2. **Repack classloading**: confirm the virtualized guard's `VMLoader` resolves `cats.meow` and the
   VM runtime from inside JabRef's jar/classloader (paths preserved on repack).
3. **Frame-neutral insertion**: confirm the edited JabRefMain passes `-Xverify:all` (no stackmap
   drift from the inserted call).
4. **Seed/server**: reuse the existing license-server flow; confirm auto-register works for the
   guard build.

## 11. Reproducibility (target commands)

```bash
# one command runs inject -> seal -> virtualize -> repack -> verify, writing EVIDENCE.log
bash demo/jabref-inject/run_jabref_inject.sh
cat demo/jabref-inject/EVIDENCE.log
```
Concrete sub-steps (inside the container, /work) are enumerated in the implementation plan.
