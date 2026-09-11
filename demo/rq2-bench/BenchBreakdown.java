import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.SecureRandom;
import java.util.Enumeration;

// RQ2 / EE2 breakdown (§8.5, axis 2 + the explanation for axis 1): where does the
// per-call cost of the virtualized method actually go? Times the sub-operations that
// run on EVERY call inside ObzcureVM's per-call decode (fingerprint node-lock, the
// hostname/NIC lookups it performs, and a standalone AES-256-GCM decrypt of a
// blob-sized payload). Each is warmed then timed over N calls; prints mean ns/us.
public class BenchBreakdown {
    static long time(String label, int reps, Runnable r) {
        for (int i = 0; i < Math.min(reps, 50); i++) r.run();       // warmup
        long t0 = System.nanoTime();
        for (int i = 0; i < reps; i++) r.run();
        long t1 = System.nanoTime();
        double ns = (double) (t1 - t0) / reps;
        System.out.printf("BREAKDOWN %-26s %12.1f ns  (%9.3f us)%n", label, ns, ns / 1000.0);
        return (long) ns;
    }

    public static void main(String[] a) throws Throwable {
        String vmJar = a[0];
        int reps = a.length > 1 ? Integer.parseInt(a[1]) : 200;

        // 1) BlobCrypto.fingerprint() from the shipped runtime (the node-lock identity)
        URLClassLoader cl = new URLClassLoader(new URL[]{ new java.io.File(vmJar).toURI().toURL() },
                                               ClassLoader.getPlatformClassLoader());
        Class<?> bc = Class.forName("obzcu.re.virtualmachine.BlobCrypto", true, cl);
        Method fp = bc.getDeclaredMethod("fingerprint"); fp.setAccessible(true);
        final int[] sink = {0};
        time("BlobCrypto.fingerprint()", reps, () -> {
            try { sink[0] ^= ((String) fp.invoke(null)).length(); } catch (Exception e) { throw new RuntimeException(e); }
        });

        // 2) the hostname sub-lookup, measured directly (shows it is NOT the hostname:
        //    the ~35 ms in fingerprint() is the NIC / MAC enumeration it also performs).
        time("InetAddress getHostName()", reps, () -> {
            try { sink[0] ^= InetAddress.getLocalHost().getHostName().length(); } catch (Exception e) {}
        });

        // 3) a standalone AES-256-GCM decrypt of a blob-sized (~640 B) payload
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        byte[] iv = new byte[12];  new SecureRandom().nextBytes(iv);
        byte[] plain = new byte[640]; new SecureRandom().nextBytes(plain);
        Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = enc.doFinal(plain);
        time("AES-256-GCM decrypt(640B)", reps, () -> {
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
                sink[0] ^= c.doFinal(ct).length;
            } catch (Exception e) { throw new RuntimeException(e); }
        });

        System.out.println("SINK " + sink[0]);
    }
}
