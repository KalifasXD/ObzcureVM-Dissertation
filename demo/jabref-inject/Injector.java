import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// JabRef injection milestone - Component 3: the source-agnostic ASM injector.
//
// Adds the two guard classes to a third-party jar and inserts a call to the
// license enforcer at the program entry point, WITHOUT recompiling or re-emitting
// any of the target's own classes. Only org/jabref/JabRefMain.class is rewritten;
// every other entry is copied byte-for-byte, so the ~30k JabRef classes and the
// manifest (Main-Class) are untouched. This is what sidesteps ObzcureVM's
// COMPUTE_FRAMES pass, which cannot resolve JabRef's JavaFX supertypes (absent
// from the jar). See DESIGN.md sections 3, 5.3 and 7.
//
// Usage: Injector <input.jar> <output.jar> <classesDir>
//   classesDir holds the compiled LicenseGuard/LicenseEnforcer .class files.
public class Injector {

    static final String TARGET_CLASS = "org/jabref/JabRefMain.class"; // entry-point class to rewrite
    static final String ENFORCER = "LicenseEnforcer";                 // owner of enforce()
    static final String[] ADDED = {                                   // classes injected at the jar root
            "LicenseGuard.class", "LicenseGuard$ApplyObzcureVM.class", "LicenseEnforcer.class"
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: Injector <input.jar> <output.jar> <classesDir>");
            System.exit(2);
        }
        Path inJar = Path.of(args[0]), outJar = Path.of(args[1]), classesDir = Path.of(args[2]);

        int copied = 0, added = 0;
        try (JarFile jf = new JarFile(inJar.toFile());
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outJar))) {

            // Rewrite the entry-point class (frame-neutral insertion).
            byte[] rewritten = insertEnforceCall(readEntry(jf, TARGET_CLASS));

            // Copy every original entry verbatim, replacing only JabRefMain.
            Set<String> seen = new HashSet<>();
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                String name = je.getName();
                if (!seen.add(name)) { System.out.println("  skip duplicate entry: " + name); continue; }
                zout.putNextEntry(new ZipEntry(name));
                if (!je.isDirectory()) {
                    if (name.equals(TARGET_CLASS)) zout.write(rewritten);
                    else try (InputStream in = jf.getInputStream(je)) { in.transferTo(zout); }
                }
                zout.closeEntry();
                copied++;
            }

            // Add the guard + enforcer classes at the jar root.
            for (String name : ADDED) {
                if (!seen.add(name)) { System.out.println("  WARNING: " + name + " already present, skipping"); continue; }
                zout.putNextEntry(new ZipEntry(name));
                zout.write(Files.readAllBytes(classesDir.resolve(name)));
                zout.closeEntry();
                added++;
            }
        }
        System.out.println("copied " + copied + " entries, replaced " + TARGET_CLASS
                + ", added " + added + " classes -> " + outJar);
    }

    static byte[] readEntry(JarFile jf, String name) throws Exception {
        JarEntry e = jf.getJarEntry(name);
        if (e == null) throw new IllegalStateException("entry not found in jar: " + name);
        try (InputStream in = jf.getInputStream(e)) { return in.readAllBytes(); }
    }

    // Prepend `invokestatic LicenseEnforcer.enforce()V` at offset 0 of main([Ljava/lang/String;)V.
    // The call is no-arg and void, so it is stack-, locals- and frame-neutral: max_stack/max_locals
    // are unchanged and there is no StackMapTable to invalidate. We therefore write the class with
    // ClassWriter(0) - no COMPUTE_FRAMES (which would need the absent JavaFX supertype) and no
    // COMPUTE_MAXS (unnecessary). This is the F3 verifier-safe patch lesson, used constructively.
    static byte[] insertEnforceCall(byte[] classBytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(classBytes).accept(cn, 0);
        boolean found = false;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("main") && m.desc.equals("([Ljava/lang/String;)V")) {
                InsnList pre = new InsnList();
                pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENFORCER, "enforce", "()V", false));
                m.instructions.insert(pre);
                found = true;
                System.out.println("  inserted invokestatic " + ENFORCER + ".enforce()V at "
                        + cn.name + ".main offset 0");
            }
        }
        if (!found) throw new IllegalStateException("main([Ljava/lang/String;)V not found in " + cn.name);
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
