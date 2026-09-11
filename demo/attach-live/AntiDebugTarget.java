import java.lang.management.ManagementFactory;

// LIVE DYNAMIC-ATTACH DEMO -- the "target".
// Launched CLEANLY (no debugger in its launch args), so anti-debug v1 passes at startup.
// It re-runs BOTH candidate anti-debug checks once per second, forever, so we can watch the exact
// iteration at which an externally-attached debugger does (or does not) change each verdict.
public class AntiDebugTarget {

    // ---- anti-debug v1 : VERBATIM from ObzcureVM.antiDebugCheck (ObzcureVM.java:55-62).
    // Data source = getRuntimeMXBean().getInputArguments() = a SNAPSHOT of the JVM LAUNCH flags.
    static boolean v1_detectsDebugger() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            String a = arg.toLowerCase();
            if (a.contains("jdwp") || a.contains("-xdebug") || a.contains("-agentlib")
                    || a.contains("-agentpath") || a.contains("-javaagent"))
                return true;
        }
        return false;
    }

    // ---- would-be anti-debug v2 : the naive PURE-JAVA runtime check (scan live threads for JDWP).
    static boolean v2_detectsDebugger() {
        for (Thread t : Thread.getAllStackTraces().keySet())
            if (t.getName().toLowerCase().contains("jdwp"))
                return true;
        return false;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("TARGET_PID=" + ProcessHandle.current().pid());
        System.out.println("Launch args (everything v1 can ever see): "
                + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.flush();
        for (int i = 0; ; i++) {
            boolean v1 = v1_detectsDebugger();
            boolean v2 = v2_detectsDebugger();
            // fail-secure, exactly like ObzcureVM.fetchSeed(): the protected method runs only if v1 is clean.
            String gate = v1 ? "BLOCKED (v1 tripped)" : "ALLOWED (seed released, protected method runs)";
            System.out.println("[iter " + i + "]  v1=" + (v1 ? "DEBUGGER" : "clean")
                    + "  v2=" + (v2 ? "DEBUGGER" : "clean") + "   -> " + gate);
            System.out.flush();
            Thread.sleep(1000);
        }
    }
}
