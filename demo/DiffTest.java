import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.ThreadLocalRandom;

// Differential-testing harness for M1.
//
// Idea: load the ORIGINAL checkLicense and the VIRTUALIZED checkLicense side by
// side, feed both the SAME random inputs, and assert they always agree. If they
// ever differ, virtualization changed the behavior (an ObzcureVM correctness
// bug). If they agree over many inputs, we have evidence the transform is faithful.
public class DiffTest {

    // How many random inputs to test. Start modest; raise it for stronger evidence.
    static final int ITERATIONS = 1_000_000;

    public static void main(String[] args) throws Exception {
        // The two jars we built earlier, sitting in the current directory (/work).
        File originalJar   = new File("license-demo.jar");
        File virtualizedJar = new File("license-demo-vm.jar");

        // One classloader per jar. Parent = the platform loader, which knows the
        // java.* classes but NOT our app classpath, so each loader is forced to
        // load its OWN copy of LicenseDemo from its OWN jar (no accidental sharing).
        ClassLoader parent = ClassLoader.getPlatformClassLoader();
        URLClassLoader originalCL   = new URLClassLoader(new URL[]{ originalJar.toURI().toURL() },   parent);
        URLClassLoader virtualCL    = new URLClassLoader(new URL[]{ virtualizedJar.toURI().toURL() }, parent);

        // Grab checkLicense(int) from each loaded class via reflection.
        Method originalCheck = load(originalCL, "LicenseDemo", "checkLicense");
        Method virtualCheck  = load(virtualCL,  "LicenseDemo", "checkLicense");

        int mismatches = 0;
        int shownMismatches = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            // Full int range, so negatives exercise the `if (r < 0)` branch too.
            int serial = ThreadLocalRandom.current().nextInt();

            // Both are static methods, so the receiver is null.
            int expected = (int) originalCheck.invoke(null, serial);
            int actual   = (int) virtualCheck.invoke(null, serial);

            if (expected != actual) {
                mismatches++;
                if (shownMismatches < 10) { // print the first few, don't flood
                    System.out.println("MISMATCH  serial=" + serial +
                            "  original=" + expected + "  virtualized=" + actual);
                    shownMismatches++;
                }
            }
        }

        System.out.println();
        System.out.println("Ran " + ITERATIONS + " random inputs.");
        System.out.println("Mismatches: " + mismatches);
        if (mismatches == 0) {
            System.out.println("PASS: virtualized checkLicense matches the original on every input.");
        } else {
            System.out.println("FAIL: virtualization changed behavior. Investigate the inputs above.");
        }
    }

    // Load a class from a given loader and return its named method (assumes one int param).
    static Method load(ClassLoader cl, String className, String methodName) throws Exception {
        Class<?> cls = Class.forName(className, true, cl);
        Method m = cls.getDeclaredMethod(methodName, int.class);
        m.setAccessible(true);
        return m;
    }
}
