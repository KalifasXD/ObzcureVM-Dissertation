// Probe to CAPTURE THE GAP between anti-debug v1 and v2.
// v1's data source = getInputArguments() (a fixed snapshot of LAUNCH flags).
// v2's data source = the live thread list (the CURRENT runtime state).
// If a debugger can be active WITHOUT appearing in the launch flags, then v1 is blind and v2 is
// needed. We test the JAVA_TOOL_OPTIONS injection vector, which enables JDWP via an environment
// variable rather than the command line.
public class ArgProbe {
    public static void main(String[] args) {
        System.out.println("v1 sees (getInputArguments): "
                + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        boolean jdwp = false; String found = null;
        for (Thread t : Thread.getAllStackTraces().keySet())
            if (t.getName().toLowerCase().contains("jdwp")) { jdwp = true; found = t.getName(); }
        System.out.println("v2 sees (a live JDWP thread?): " + jdwp
                + (found != null ? " -> \"" + found + "\"" : ""));
    }
}
