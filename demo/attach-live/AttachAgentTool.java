import com.sun.tools.attach.VirtualMachine;
// Attacker variant that loads a JAVA INSTRUMENTATION AGENT into a running JVM post-launch.
public class AttachAgentTool {
    public static void main(String[] a) throws Exception {
        String pid = a[0], jar = a[1];
        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgent(jar);
        vm.detach();
        System.out.println("[attacker] instrumentation agent loaded into PID " + pid
                + " post-launch (no -javaagent launch flag => invisible to v1)");
    }
}
