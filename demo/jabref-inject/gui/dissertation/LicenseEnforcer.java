package dissertation;

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

// JabRef GUI demo - Component 2 (packaged copy of the consuming edge). Identical to
// demo/jabref-inject/LicenseEnforcer.java except for `package dissertation;`.
//
// enforce() is injected at the first instruction of org.jabref.gui.JabRefLauncher.main.
// It invokes the (virtualized) LicenseGuard, derives an AES-256 key from the wide 64-bit
// return value, and AES-256-GCM-decrypts the sealed payload (guard.enc). No boolean gate:
// the application proceeds only if the genuine license value unseals the payload; any
// failure terminates the process (fail-secure) before JabRef's GUI is ever launched.
public class LicenseEnforcer {

    static final String SERIAL_PROPERTY = "license.serial";
    static final String SERIAL_ENV = "LICENSE_SERIAL";
    static final String PAYLOAD_RESOURCE = "/guard.enc";
    static final String PAYLOAD_FILE = "guard.enc";

    public static void enforce() {
        try {
            int serial = readSerial();
            long value = LicenseGuard.checkLicense(serial); // runs inside ObzcureVM; needs the off-machine seed
            byte[] payload = decrypt(readSealed(), key(value)); // throws on a wrong value (GCM tag mismatch)
            System.out.println("[license] verified: " + new String(payload, "UTF-8"));
            // success: control returns to the launcher and JabRef starts normally
        } catch (Throwable t) {
            System.err.println("[license] verification failed; refusing to run");
            System.exit(1); // fail-secure: no valid license -> terminate before the GUI opens
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

    public static byte[] key(long value) throws GeneralSecurityException {
        byte[] wide = ByteBuffer.allocate(Long.BYTES).putLong(value).array();
        return MessageDigest.getInstance("SHA-256").digest(wide);
    }

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
