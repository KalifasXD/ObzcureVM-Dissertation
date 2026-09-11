# Live dynamic-attach proof (native Windows, JDK 18)

Proves, live and reproducibly, the boundary of anti-debug **v1** (the ObzcureVM
`antiDebugCheck` that scans `getRuntimeMXBean().getInputArguments()`), which the
container could not demonstrate because its JVM Attach handshake timed out.

## Result (verified 2026-08-24, JDK 18.0.2.1, native Windows)

1. **Target launched clean** -> v1 = clean, v2 = clean, gate ALLOWED (iters 0-4).
2. **JDWP debugger, post-launch, try A: FAILS at the mechanism.**
   `jdwp.dll` exports only `Agent_OnLoad` / `Agent_OnUnload`, **no `Agent_OnAttach`**,
   so `VirtualMachine.loadAgentLibrary("jdwp", ...)` throws
   `AgentLoadException: _Agent_OnAttach@12 is not available in jdwp`.
   => a **pure JDWP debugger cannot be dynamically attached** through the Attach API;
   the only way in is at launch, which v1 (command-line AND env-var) already blocks.
   **For the JDWP vector specifically, v1 is sufficient.**
3. **Instrumentation agent, post-launch, try B: SUCCEEDS = the real gap.**
   `instrument.dll` exports `Agent_OnAttach`, so `VirtualMachine.loadAgent(agent.jar)`
   loads a Java agent into the clean-launched target at runtime. Its `agentmain` runs
   INSIDE the target (PID confirmed), with `canRedefine=true` / `canRetransform=true`
   over ~960 loaded classes = a full devirtualization foothold. The `-javaagent` vector
   is one of v1's own keywords, yet attached post-launch it is **invisible to v1**
   (iters 5-8 still report clean / ALLOWED).
4. **Would-be v2 (pure-Java thread-scan) stays blind** the entire run.
   => reliable runtime detection of a dynamically-attached agent needs **native / JVMTI**
   code = PhD future work.

## Refined claim (supersedes the earlier "v1 misses a post-launch JDWP debugger")

v1 fully covers the JDWP debugger (launch-time only; late-attach is impossible on this JVM).
The genuine residual dynamic-attach threat is a **post-launch instrumentation / JVMTI agent**,
which v1 cannot see and pure-Java runtime checks cannot reliably detect.

## Re-run (Windows, Git Bash or WSL-with-Windows-JDK)

    bash run_attach_live.sh && cat EVIDENCE.log

Files: `AntiDebugTarget.java` (target, v1 verbatim from ObzcureVM.java:55 + naive v2),
`AttachTool.java` (jdwp attacker), `AttachAgentTool.java` + `InjectedAgent.java` + `manifest.txt`
(instrumentation attacker), `run_attach_live.sh` (orchestration), `EVIDENCE.log` (captured run).
