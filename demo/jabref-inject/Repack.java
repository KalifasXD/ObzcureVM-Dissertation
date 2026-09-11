import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// JabRef injection milestone - Component 6: repack.
//
// Produces the final jabref-protected.jar by overlaying the virtualized guard bundle onto the
// injected jar. From guard-vm.jar it takes: the virtualized LicenseGuard.class (a VM dispatch
// shell), the ObzcureVM runtime (obzcu/re/**) and the encrypted VM program (obzcure/**, i.e.
// cats.meow). It also adds the sealed payload guard.enc at the jar root. Everything else in
// jabref-injected.jar - the ~41k JabRef entries, the rewritten JabRefMain, LicenseEnforcer and the
// manifest (Main-Class) - is copied byte-for-byte. VM runtime is obzcu/re/*, disjoint from
// org/jabref/*, so there are no package collisions (DESIGN.md sections 4 and 5.6).
public class Repack {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: Repack <injected.jar> <guard-vm.jar> <guard.enc> <output.jar>");
            System.exit(2);
        }
        Path injected = Path.of(args[0]), guardVm = Path.of(args[1]), guardEnc = Path.of(args[2]), out = Path.of(args[3]);

        // Collect the overlay entries from the virtualized guard bundle.
        Map<String, byte[]> overlay = new LinkedHashMap<>();
        try (JarFile g = new JarFile(guardVm.toFile())) {
            Enumeration<JarEntry> en = g.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                String n = je.getName();
                if (je.isDirectory()) continue;
                if (n.equals("LicenseGuard.class") || n.startsWith("obzcu/re/") || n.startsWith("obzcure/")) {
                    try (InputStream in = g.getInputStream(je)) { overlay.put(n, in.readAllBytes()); }
                }
            }
        }

        int copied = 0, replaced = 0, addedRuntime = 0;
        Set<String> seen = new HashSet<>();
        try (JarFile in = new JarFile(injected.toFile());
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<JarEntry> en = in.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                String n = je.getName();
                if (!seen.add(n)) continue;
                zout.putNextEntry(new ZipEntry(n));
                if (!je.isDirectory()) {
                    byte[] ov = overlay.remove(n); // replace the plain LicenseGuard with the virtualized one
                    if (ov != null) { zout.write(ov); replaced++; }
                    else try (InputStream is = in.getInputStream(je)) { is.transferTo(zout); }
                }
                zout.closeEntry();
                copied++;
            }
            for (Map.Entry<String, byte[]> e : overlay.entrySet()) { // VM runtime + cats.meow not already present
                if (!seen.add(e.getKey())) continue;
                zout.putNextEntry(new ZipEntry(e.getKey()));
                zout.write(e.getValue());
                zout.closeEntry();
                addedRuntime++;
            }
            if (seen.add("guard.enc")) { // the sealed payload
                zout.putNextEntry(new ZipEntry("guard.enc"));
                zout.write(Files.readAllBytes(guardEnc));
                zout.closeEntry();
            }
        }
        System.out.println("copied " + copied + " entries (replaced " + replaced + " = virtualized LicenseGuard), added "
                + addedRuntime + " VM-runtime/cats.meow entries + guard.enc -> " + out);
    }
}
