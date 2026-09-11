import java.lang.management.ManagementFactory;
import com.sun.tools.attach.VirtualMachine;

// Demonstrates the DYNAMIC-ATTACH BYPASS of anti-debug v1.
//
// Anti-debug v1 only inspects the JVM's LAUNCH arguments. This program is launched cleanly
// (no debugger flag), so v1 sees nothing and lets the protected method run. Then, at runtime,
// it attaches the JDWP debugger to its OWN process via Java's Attach API. That leaves NO trace
// in the launch arguments, so v1 stays blind and the method still runs, the bypass.
//
// (v2 will close this by detecting the debugger at runtime instead of at launch.)
public class AttachDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("Launch args (all v1 can see): "
                + ManagementFactory.getRuntimeMXBean().getInputArguments());

        System.out.println("[before attach] checkLicense(4321) = " + LicenseDemo.checkLicense(4321));

        // The "attacker" attaches the JDWP debugger to THIS running JVM at runtime:
        String pid = String.valueOf(ProcessHandle.current().pid());
        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgentLibrary("jdwp", "transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5010");
        vm.detach();
        System.out.println(">>> JDWP debugger attached at runtime (invisible to launch args) <<<");

        System.out.println("[after attach] checkLicense(4321) = " + LicenseDemo.checkLicense(4321));
    }
}
