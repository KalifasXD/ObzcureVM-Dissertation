import com.sun.tools.attach.VirtualMachine;

// The attacker: attaches the JDWP debugger to a SEPARATE, already-running target JVM (by PID) at
// runtime, via Java's Attach API. It is NOT in the target's launch arguments, so v1 never sees it.
public class AttachTool {
    public static void main(String[] args) throws Exception {
        String pid = args[0];
        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgentLibrary("jdwp", "transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5015");
        vm.detach();
        System.out.println("[attacker] JDWP debugger attached to PID " + pid + " on 127.0.0.1:5015");
    }
}
