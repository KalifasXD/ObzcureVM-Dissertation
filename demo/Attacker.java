import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import obzcu.re.virtualmachine.ObzcureVM;
import obzcu.re.virtualmachine.types.VMNode;

// DYNAMIC ATTACK (Level 2). Threat: an attacker who can run code inside the licensed
// process (e.g. via -javaagent, a modified launcher, or simply being the licensed user)
// and therefore has the runtime seed the server delivered.
//
// Per-build diversification hides opcodes in the STORED blob. But to actually run, ObzcureVM
// de-shuffles them back to REAL opcodes in memory (the private `instructions` array). So the
// attack is trivial: trigger the decode via the public virtualize() API, then read the
// `instructions` array by reflection and print the recovered program.
//
// Run with -Dobzcure.seed=<the build seed> (a licensed machine has this at runtime).
public class Attacker {
    public static void main(String[] args) throws Exception {
        // Decode virtualized method #0 (our checkLicense). This needs the seed.
        ObzcureVM vm = ObzcureVM.virtualize(0, 3, 3, MethodHandles.lookup());

        // Reach into the private, already-de-diversified instruction array.
        Field f = ObzcureVM.class.getDeclaredField("instructions");
        f.setAccessible(true);
        VMNode[] insns = (VMNode[]) f.get(vm);

        System.out.println("Recovered " + insns.length + " VM instructions (real, de-diversified opcodes):");
        for (int i = 0; i < insns.length; i++) {
            VMNode n = insns[i];
            System.out.println("  [" + i + "] " + n.getClass().getSimpleName() + "  opcode=" + n.opcode);
        }
    }
}
