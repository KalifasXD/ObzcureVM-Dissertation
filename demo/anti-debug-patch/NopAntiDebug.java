import java.nio.file.*;

// JDK-only bytecode patcher (F3): locate the single `invokestatic ObzcureVM.antiDebugCheck()`
// and overwrite it with three NOPs. antiDebugCheck() takes no arguments and returns void, so
// deleting the call leaves the operand stack unchanged and shifts no bytecode offsets -> the
// StackMapTable stays valid = a VERIFIER-SAFE ~3-byte patch (no recompilation, no ASM).
//
// Models a Level-2 attacker neutering the anti-debug envelope layer, then attaching a debugger.
// usage: java NopAntiDebug <in.class> <out.class> [methodName=antiDebugCheck]
public class NopAntiDebug {

    public static void main(String[] args) throws Exception {
        String target = args.length > 2 ? args[2] : "antiDebugCheck";
        byte[] b = Files.readAllBytes(Path.of(args[0]));

        // ---- walk the constant pool, recording what we need to find the call site ----
        int p = 8;                       // skip magic(4) + minor(2) + major(2)
        int cpCount = u2(b, p); p += 2;
        String[] utf8   = new String[cpCount];  // Utf8 -> its string
        int[]    ntName = new int[cpCount];      // NameAndType -> name Utf8 index
        int[]    mrefNT = new int[cpCount];      // Methodref   -> NameAndType index (0 = not a Methodref)
        for (int i = 1; i < cpCount; i++) {
            int tag = b[p++] & 0xFF;
            switch (tag) {
                case 1:  { int len = u2(b, p); p += 2; utf8[i] = new String(b, p, len, "UTF-8"); p += len; break; } // Utf8
                case 7: case 8: case 16: case 19: case 20: p += 2; break;   // Class/String/MethodType/Module/Package
                case 15: p += 3; break;                                      // MethodHandle
                case 3: case 4: case 9: case 11: case 17: case 18: p += 4; break; // Int/Float/Fieldref/IfaceMref/Dyn/InvokeDyn
                case 10: mrefNT[i] = u2(b, p + 2); p += 4; break;            // Methodref -> name_and_type_index
                case 12: ntName[i] = u2(b, p);     p += 4; break;            // NameAndType -> name_index
                case 5: case 6: p += 8; i++; break;                          // Long/Double occupy TWO cp slots
                default: throw new IllegalStateException("unknown constant-pool tag " + tag + " at offset " + (p - 1));
            }
        }
        int codeRegion = p; // everything before here is the constant pool; scan for opcodes AFTER it

        // ---- Utf8 index of the target method name ----
        int u = -1;
        for (int i = 1; i < cpCount; i++) if (target.equals(utf8[i])) { u = i; break; }
        if (u < 0) throw new IllegalStateException("method name not in constant pool: " + target);

        // ---- for every Methodref whose NameAndType names the target, NOP its invokestatic ----
        int patched = 0;
        for (int m = 1; m < cpCount; m++) {
            if (mrefNT[m] == 0 || ntName[mrefNT[m]] != u) continue;
            byte hi = (byte) (m >> 8), lo = (byte) (m & 0xFF);
            for (int k = codeRegion; k + 2 < b.length; k++) {
                if (b[k] == (byte) 0xB8 && b[k + 1] == hi && b[k + 2] == lo) { // invokestatic #m
                    b[k] = 0; b[k + 1] = 0; b[k + 2] = 0;                      // -> nop nop nop (0x00)
                    patched++;
                    System.out.println("NOP'd invokestatic #" + m + " (" + target + ") at file offset " + k);
                }
            }
        }
        if (patched == 0) throw new IllegalStateException("no invokestatic to " + target + "() found");

        Files.write(Path.of(args[1]), b);
        System.out.println("verifier-safe: removed " + patched + " call site(s); wrote " + args[1]);
    }

    static int u2(byte[] b, int i) { return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF); }
}
