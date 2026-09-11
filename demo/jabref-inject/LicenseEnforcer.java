import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

// JabRef injection milestone - Component 2: the consuming edge.
//
// This class is PLAIN bytecode and is NOT virtualized. It is injected into JabRef
// and a call to enforce() is inserted at the very first instruction of
// org.jabref.JabRefMain.main (DESIGN.md sections 5.2 and 7).
//
// enforce() invokes the (virtualized) LicenseGuard, derives an AES-256 key from
// the wide 64-bit return value, and AES-256-GCM-decrypts the sealed payload
// (guard.enc, repacked into the jar). There is NO boolean gate: the application
// proceeds only if the genuine license value produces a key that unseals the
// payload; any failure terminates the process (fail-secure). To unlock a pirate
// serial an attacker must make the virtualized guard reproduce the licensed
// value, i.e. defeat the real protected computation, not flip a branch.
//
// It uses javax.crypto and Strings, so it deliberately stays OUT of the
// virtualized jar (that keeps invokedynamic/crypto frames away from ObzcureVM's
// COMPUTE_FRAMES pass). This mirrors the F4 demo split: LicenseGuard is the
// virtualized method, LicenseEnforcer is the plain consuming edge.
public class LicenseEnforcer {

    // Runtime serial source for the proof-of-concept (system property, else env var).
    static final String SERIAL_PROPERTY = "license.serial";
    static final String SERIAL_ENV = "LICENSE_SERIAL";
    // The sealed payload lives at the jar root after repack; a working-directory
    // file is the offline fallback used before the repack step.
    static final String PAYLOAD_RESOURCE = "/guard.enc";
    static final String PAYLOAD_FILE = "guard.enc";

    public static void enforce() {
        try {
            int serial = readSerial();
            long value = LicenseGuard.checkLicense(serial); // runs inside ObzcureVM; needs the off-machine seed
            byte[] payload = decrypt(readSealed(), key(value)); // throws on a wrong value (GCM tag mismatch)
            System.out.println("[license] verified: " + new String(payload, "UTF-8"));
            // success: control returns to main() and the application proceeds normally
        } catch (Throwable t) {
            System.err.println("[license] verification failed; refusing to run");
            System.exit(1); // fail-secure: no valid license -> terminate, never a half-open state
        }
    }

    static int readSerial() {
        String s = System.getProperty(SERIAL_PROPERTY);
        if (s == null) s = System.getenv(SERIAL_ENV);
        if (s == null) throw new IllegalStateException("no license serial supplied");
        return Integer.parseInt(s.trim());
    }

    static byte[] readSealed() throws Exception {
        try (InputStream in = LicenseEnforcer.class.getResourceAsStream(PAYLOAD_RESOURCE)) {
            if (in != null) return in.readAllBytes();
        }
        return Files.readAllBytes(Path.of(PAYLOAD_FILE));
    }

    // AES-256 key = SHA-256 of the 8-byte big-endian license value (DESIGN.md 5.2).
    // The wide, injective guard output makes this key unique per serial (fixes the
    // demo's "% 100000" collision boundary).
    public static byte[] key(long value) throws GeneralSecurityException {
        byte[] wide = ByteBuffer.allocate(Long.BYTES).putLong(value).array();
        return MessageDigest.getInstance("SHA-256").digest(wide);
    }

    // AES-256-GCM, layout [12-byte IV][ciphertext + 16-byte tag] (same as the F4 demo).
    // Used by the build-time GuardSealer; kept here so the crypto has a single home.
    public static byte[] encrypt(byte[] plain, byte[] key) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    public static byte[] decrypt(byte[] sealed, byte[] key) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, sealed, 0, 12));
        return c.doFinal(sealed, 12, sealed.length - 12);
    }
}
