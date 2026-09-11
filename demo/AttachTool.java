import com.sun.tools.attach.VirtualMachine;

// The attacker: attaches the JDWP debugger to a SEPARATE, already-running target JVM (by PID),
// at runtime. Because it is not in the target's launch arguments, anti-debug v1 never sees it.
// v2 (runtime detection) will.
public class AttachTool {
    public static void main(String[] args) throws Exception {
        String pid = args[0];
        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgentLibrary("jdwp", "transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5015");
        vm.detach();
        System.out.println("[attacker] JDWP debugger attached to PID " + pid);
    }
}
