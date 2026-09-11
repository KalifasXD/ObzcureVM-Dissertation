# F3 - the anti-debug guard is a patchable plaintext gate (V5 falsified for a tool-combining attacker)

**Audit finding F3 (HIGH), measured as an attack row.** The envelope's anti-debug layer is
`ObzcureVM.antiDebugCheck()` - it scans the JVM's launch arguments for `jdwp / -xdebug / -agentlib /
-agentpath / -javaagent` and throws if any are present, and it is called by a single `invokestatic`
at the top of `fetchSeed()`. `ObzcureVM` is the interpreter and is **excluded from virtualization**,
so that guard ships as ordinary, readable bytecode. A Level-2 attacker deletes the one `invokestatic`
with a verifier-safe ~3-byte NOP and the guard is gone.

This is the anti-debug analogue of the `demo/result-binding` gate-vs-bound contrast: a boolean-style
gate is a single point of failure a cheap patch removes, whereas a result-bound check cannot be
patched to "yes".

## What the demo shows (`run_anti_debug_patch.sh` -> `EVIDENCE.log`)

1. **Plaintext.** `javap -p -c` of the *shipped* `ObzcureVM.class` (extracted from `license-demo-vm.jar`)
   shows `antiDebugCheck()` in cleartext and its `invokestatic` at the top of `fetchSeed`.
2. **V5 as claimed.** Launching the run under `-agentlib:jdwp=...` triggers the guard:
   `IllegalStateException: Debugger or instrumentation agent detected; refusing to run` ->
   `Failed loading .meow data` (locked). This is screenshot 07 / the V5 "BLOCKED" cell.
3. **Attack.** `NopAntiDebug` (JDK-only, ~70 lines: parses the constant pool, finds the
   `antiDebugCheck` Methodref, NOPs its `invokestatic`) patches `ObzcureVM.class` and it is re-inserted
   into the jar. The patch is **verifier-safe**: the call takes no arguments and returns void, so
   deleting it changes no stack heights and shifts no bytecode offsets, leaving the StackMapTable valid.
4. **V5 falsified.** The *exact same* `-agentlib:jdwp` launch now completes: DiffTest 1,000,000 / 0
   PASS, with the JDWP transport still listening on 5005. The debugger the layer was meant to block is
   now free to attach, and the protected method runs.

## Ablation row (Chapter 6 - a new attack row, "V5-patch")

| attack | tier | claimed (V5) | tool-combining attacker |
|---|---|---|---|
| launch-time debugger vs the anti-debug guard | 2 | anti-debug **BLOCKS** (guard throws) | **guard NOP'd** -> same launch RUNS; V5 "BLOCKED" cell is falsified because the guard is an unvirtualized, verifier-safe patch target |

So the honest V5 story is two-sided: anti-debug v1 blocks a debugger *against an attacker who does not
patch* (screenshot 07), but it does **not** hold against an attacker who first removes the guard - and
removing it costs ~3 bytes because the guard is plaintext. The layer raises the bar (the attacker must
now edit bytecode, not just pass a flag) but is not a standalone barrier.

## Why this is honest, not a contradiction of the earlier anti-debug work

The Session 10/11 result was about *detection reach* (v1 catches launch-time jdwp/agents; a post-launch
instrumentation agent and native debuggers are out of reach = the PhD boundary). F3 is a different,
cheaper break: you do not need to evade detection if you delete the detector. Both are real; together
they say anti-debug v1 is a bar-raiser, not a guarantee.

## How the design would close it (fix direction, for the eval chapter - not built here)

Mirror the result-binding lesson: remove the single point of failure.
- **Virtualize the guard.** Fold the anti-debug check into a virtualized method so there is no plaintext
  `invokestatic` to NOP (the check becomes VM opcodes, protected by diversification + encryption).
- **Result-bind the guard.** Make the guard's outcome *feed the decode* (e.g. mix its result into the
  key derivation) so that removing or short-circuiting it yields the wrong key rather than a bypass -
  then a patch can only break, never unlock, exactly like `BoundDemo`.
Either turns "delete the detector" into "defeat the real computation".

## Notes / boundaries

- **Dev build only to supply the seed.** The demo uses `BlobCrypto.DEV_MODE = true` + `-Dobzcure.seed`
  so the seed arrives offline (standing in for a licensed user's server-delivered seed). `DEV_MODE`
  gates only the F2 seed/fingerprint overrides; `antiDebugCheck()` is byte-identical in a release
  build, so the patch is faithful to the shipped artifact.
- **JDK-only, reproducible in the container.** No ASM, no native tools. `NopAntiDebug` reads the class
  file directly; the patch is put back with `jar uf`.
- Reproduce: build dev (`DEV_MODE=true`, `mvn clean package -DskipTests`), then
  `bash demo/anti-debug-patch/run_anti_debug_patch.sh && cat demo/anti-debug-patch/EVIDENCE.log`.
