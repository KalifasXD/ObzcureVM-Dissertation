# 02 - Anti-debug and the live dynamic-attach gap

Portable JVM-level anti-debug: `ObzcureVM.antiDebugCheck()` scans the JVM input arguments for
jdwp/-agentlib/-agentpath/-javaagent and refuses to run, guarding the seed-fetch moment.

## What the screenshots prove
- **07** - V5: a launch-time debugger (`-agentlib:jdwp=...`) is detected and the app refuses to run.
- **08** - the same check also catches `JAVA_TOOL_OPTIONS` env-var injection; a naive thread-scan v2 is
  blind (documented negative result).
- **09** - V6, proven live on native Windows: a pure JDWP debugger cannot be attached post-launch
  (`jdwp.dll` exports no `Agent_OnAttach`), but an instrumentation agent can, and it is invisible to the
  pure-Java checks. Reliable detection of that needs native/JVMTI code (future work).
- **23** - F3: the anti-debug guard is unvirtualized plaintext; a verifier-safe 3-byte NOP patch removes
  it and the app then runs under JDWP. This falsifies the V5 "BLOCKED" cell for a tool-combining attacker
  and motivates virtualizing or result-binding the guard.

## Files
- `attach-live.EVIDENCE.log` + `attach-live.README.md` - the live V6 experiment (screenshot 09).
- `anti-debug-patch.EVIDENCE.log` + `anti-debug-patch.README.md` - the F3 NOP-patch (screenshot 23).

## Reproduce
`bash demo/attach-live/run_attach_live.sh` (needs a full JDK on native Windows);
`bash demo/anti-debug-patch/run_anti_debug_patch.sh`.

## Honest boundary
Pure-Java anti-debug covers launch-time and JDWP entirely; the residual post-launch instrumentation/native
agent is not reliably detectable without native/JVMTI = PhD future work.
