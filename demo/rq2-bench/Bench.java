import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

// RQ2 / EE2 microbenchmark harness (§8.5, axis 1: per-call execution time of the
// virtualized vs the original checkLicense). One target per JVM fork, so JIT state
// is independent per fork; the driver runs several forks and aggregates.
//
// Method: load checkLicense(int) via reflection from the given jar, then bind it to a
// MethodHandle and call it with invokeExact in the hot loop (near-native dispatch, so
// the harness overhead is negligible and the same for both targets). A changing input
// (an LCG) and an accumulated sink prevent constant-folding / dead-code elimination.
// After a warmup phase (lets C2 compile the path, and for the virtualized target lets
// the one-time seed fetch + cache happen), it times `batches` batches of `batch` calls
// and prints per-call nanoseconds as machine-readable SAMPLE lines.
//
// args: <target-label> <jar> <warmupCalls> <batchCalls> <batches>
public class Bench {
    public static void main(String[] a) throws Throwable {
        String target = a[0];
        String jar    = a[1];
        int warmup    = Integer.parseInt(a[2]);
        int batch     = Integer.parseInt(a[3]);
        int batches   = Integer.parseInt(a[4]);

        ClassLoader parent = ClassLoader.getPlatformClassLoader();
        URLClassLoader cl = new URLClassLoader(new URL[]{ new File(jar).toURI().toURL() }, parent);
        Class<?> cls = Class.forName("LicenseDemo", true, cl);
        Method m = cls.getDeclaredMethod("checkLicense", int.class);
        m.setAccessible(true);
        MethodHandle mh = MethodHandles.lookup().unreflect(m); // static -> type (int)int

        long sink = 0;
        int serial = 12345;

        // warmup (also triggers the virtualized target's one-time seed fetch + cache)
        for (int i = 0; i < warmup; i++) {
            int r = (int) mh.invokeExact(serial);
            sink ^= r;
            serial = serial * 1103515245 + 12345;
        }

        for (int b = 0; b < batches; b++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < batch; i++) {
                int r = (int) mh.invokeExact(serial);
                sink ^= r;
                serial = serial * 1103515245 + 12345;
            }
            long t1 = System.nanoTime();
            double perCallNs = (double) (t1 - t0) / batch;
            System.out.println("SAMPLE " + target + " " + perCallNs);
        }
        System.out.println("SINK " + sink); // keep the JIT honest (observable result)
    }
}
