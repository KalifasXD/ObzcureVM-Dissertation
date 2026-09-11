import java.lang.instrument.Instrumentation;

// Post-launch instrumentation agent = the REAL dynamic-attach threat.
// agentmain() is the Attach-API entry point (backed by instrument.dll's Agent_OnAttach).
// It runs INSIDE the clean-launched target, with full Instrumentation power (redefine/retransform
// any class = the devirtualization enabler), yet it was never in the target's launch flags, so
// anti-debug v1 (getInputArguments scan for -javaagent/-agentlib/jdwp) can never see it.
public class InjectedAgent {
    public static void agentmain(String args, Instrumentation inst) {
        long pid = ProcessHandle.current().pid();
        System.out.println();
        System.out.println(">>> [INJECTED AGENT] now running INSIDE target PID " + pid
                + "  (attached POST-LAUNCH via Attach API) <<<");
        System.out.println(">>> [INJECTED AGENT] Instrumentation granted: canRedefine="
                + inst.isRedefineClassesSupported()
                + "  canRetransform=" + inst.isRetransformClassesSupported());
        System.out.println(">>> [INJECTED AGENT] can see " + inst.getAllLoadedClasses().length
                + " loaded classes (incl. the protected app's own) = full devirtualization foothold");
        System.out.flush();
    }
}
