import javax.crypto.*; import javax.crypto.spec.*; import java.security.*;

// F4 result-binding crypto, bound to the REAL protected computation.
//
// The premium feature is sealed under an AES-256-GCM key derived from the OUTPUT VALUE of checkLicense.
// There is no boolean gate: the feature IS the decrypted payload, so the only way to obtain it is to
// reproduce checkLicense's exact value. checkLicense(int) is loaded and INVOKED from a jar - the vendor
// step loads the ORIGINAL jar, the app loads the VIRTUALIZED jar - so at runtime the key comes from the
// genuine virtualized+diversified+encrypted method, NOT a plaintext re-implementation.
public class Feature {

    // Off-machine 64-bit seed: the system's one secret, delivered by the license server (here supplied
    // via -Dobzcure.seed in the dev demo, the same channel the VM uses). Required to derive the feature
    // key. This realises the "one seed, three roles" design of section 4.5 (job #3, previously not in
    // code) and closes the offline brute force of the low-entropy checkLicense value.
    static long seed() {
        String s = System.getProperty("obzcure.seed");
        if (s == null || s.isEmpty()) throw new IllegalStateException("no off-machine seed available");
        return Long.parseLong(s.trim());
    }

    static SecretKeySpec key(int v, long seed) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(java.nio.ByteBuffer.allocate(8).putLong(seed).array()); // mix the off-machine secret
        md.update(("feature:" + v).getBytes("UTF-8"));
        return new SecretKeySpec(md.digest(), "AES");
    }

    static byte[] encrypt(byte[] p, int v, long seed) throws Exception {
        byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(v, seed), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(p), out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12); System.arraycopy(ct, 0, out, 12, ct.length); return out;
    }

    static byte[] decrypt(byte[] e, int v, long seed) throws Exception { // throws (GCM tag) if v OR seed is wrong
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(v, seed), new GCMParameterSpec(128, e, 0, 12));
        return c.doFinal(e, 12, e.length - 12);
    }

    // Load checkLicense(int) from a jar (original or virtualized) and invoke it. Invoking the
    // VIRTUALIZED jar runs the method inside ObzcureVM, so it needs the off-machine seed.
    static int checkLicense(String jar, int serial) throws Exception {
        java.net.URLClassLoader cl = new java.net.URLClassLoader(
            new java.net.URL[]{ new java.io.File(jar).toURI().toURL() }, ClassLoader.getPlatformClassLoader());
        java.lang.reflect.Method m = Class.forName("LicenseDemo", true, cl).getDeclaredMethod("checkLicense", int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, serial);
    }
}
