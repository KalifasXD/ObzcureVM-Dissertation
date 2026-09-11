import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// JabRef GUI demo - Component 3b: modular, source-agnostic ASM injector.
//
// Unlike the classpath Injector (which rewrote JabRefMain inside a fat jar), JabRef 5.x is
// a jpackage app-image whose classes are linked into the runtime jimage. So instead of
// repacking a jar we produce a ONE-CLASS patch jar for `--patch-module org.jabref=...`.
//
// It prepends a single frame-neutral instruction, `invokestatic dissertation/LicenseEnforcer.enforce()V`,
// at the very first instruction of the given method. The call takes no operands and returns void,
// so max_stack / max_locals are unchanged and no StackMapTable entry is added at offset 0
// (the constructive use of the F3 verifier-safety lesson). ClassWriter(0): no COMPUTE_FRAMES
// (JabRef's supertypes are absent from a bare classpath) and no COMPUTE_MAXS (the edit is neutral).
//
// args: <input .class> <output patch.jar> <internal-name e.g. org/jabref/gui/JabRefLauncher> [method=main]
public class ModularInjector {
    public static void main(String[] a) throws Exception {
        Path in = Path.of(a[0]);
        Path outJar = Path.of(a[1]);
        String internal = a[2];
        String method = a.length > 3 ? a[3] : "main";

        ClassReader cr = new ClassReader(Files.readAllBytes(in));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean done = false;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(method) && mn.desc.equals("([Ljava/lang/String;)V")) {
                InsnList pre = new InsnList();
                pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "dissertation/LicenseEnforcer", "enforce", "()V", false));
                mn.instructions.insert(pre); // insert BEFORE the current offset-0 instruction
                done = true;
                System.out.println("injected enforce() at start of " + internal + "." + method
                        + "  (max_stack=" + mn.maxStack + " max_locals=" + mn.maxLocals + " unchanged)");
            }
        }
        if (!done) throw new IllegalStateException("target method not found: " + internal + "." + method);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] outCls = cw.toByteArray();

        Files.createDirectories(outJar.toAbsolutePath().getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outJar))) {
            zos.putNextEntry(new ZipEntry(internal + ".class"));
            zos.write(outCls);
            zos.closeEntry();
        }
        System.out.println("wrote " + outJar + " (patched " + internal + ".class = " + outCls.length + " bytes)");
    }
}
