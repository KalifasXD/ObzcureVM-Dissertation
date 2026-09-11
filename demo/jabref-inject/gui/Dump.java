import java.nio.file.*;
import java.net.URI;
import java.util.Map;

// Surgically copy one class out of the running runtime's jrt image. Run with JabRef's
// bundled JDK, whose image has the org.jabref module linked in, to extract
// org/jabref/gui/JabRefLauncher.class without unpacking the whole 173 MB jimage.
// args: <module/internal/path.class>  <output-file>
public class Dump {
    public static void main(String[] a) throws Exception {
        FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), Map.of());
        Path src = fs.getPath("/modules/" + a[0]);
        Path dst = Paths.get(a[1]);
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("dumped " + src + " -> " + dst + " (" + Files.size(dst) + " bytes)");
    }
}
