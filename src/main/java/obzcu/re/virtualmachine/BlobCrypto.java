package obzcu.re.virtualmachine;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Anti-dump / encrypt-at-rest for the virtualized method blob (obzcure/cats.meow).
 *
 * The diversification pass permutes OPCODES only, so a static dump of cats.meow still leaks the
 * OPERANDS (e.g. the secret constant) and the class/method metadata in plaintext. This helper
 * encrypts each per-method blob at build time and decrypts it at load time, so a static dump yields
 * only ciphertext.
 *
 * Key management: the AES-256 key is derived from the off-machine seed that seeds the opcode
 * permutation AND the local hardware fingerprint (key = SHA-256("obzcure-blob:" + seed + "|" +
 * fingerprint)). The seed is delivered off-machine by the license server; the fingerprint is computed
 * on the running machine. So one secret decodes the diversified opcodes AND decrypts the blob, but only
 * on the machine whose fingerprint was present at encryption time (F2 cryptographic node-lock): a seed
 * leaked or replayed to a different machine produces the WRONG key and GCM decryption fails fail-secure,
 * rather than relying on a server-side 403 an attacker can sidestep by not asking.
 *
 * Cipher: AES/GCM/NoPadding. GCM is authenticated, so a tampered blob fails decryption with
 * AEADBadTagException = blob integrity / anti-tamper for free.
 *
 * On-disk layout of an encrypted blob: [12-byte random IV][ciphertext || 16-byte GCM tag].
 */
public final class BlobCrypto {

    private static final int IV_LEN = 12;    // GCM standard nonce length (96 bits)
    private static final int TAG_BITS = 128; // GCM authentication tag length

    // F2: developer-only overrides (-Dobzcure.seed, -Dobzcure.fingerprint) are compiled in ONLY when
    // this is true. Because it is a compile-time constant, javac strips the guarded override branches
    // from the bytecode whenever it is false, so the seed and fingerprint cannot be forced on the command
    // line - closing the offline one-flag bypass of server + fingerprint + TLS. The value is inlined at
    // compile time, so it cannot be re-enabled at runtime by a flag or by reflection, and a clean rebuild
    // (mvn clean package) is required after changing it.
    //
    // SECURE BY DEFAULT: this is false, so a plain build is hardened and "forgetting to flip it" yields
    // the SAFE artifact, not an exploitable one. Set it to true ONLY for a dev/test build, where the
    // differential-test / demo harness runs offline via -Dobzcure.seed and can simulate a second machine
    // via -Dobzcure.fingerprint. Never ship an artifact built with DEV_MODE = true.
    public static final boolean DEV_MODE = false;

    private BlobCrypto() {}

    /**
     * AES-256 key derived from the off-machine seed AND the local hardware fingerprint (F2 node-lock):
     * key = SHA-256("obzcure-blob:" + seed + "|" + fingerprint). The seed is delivered off-machine by the
     * license server; the fingerprint is computed on the running machine. A blob encrypted with one
     * fingerprint decrypts ONLY where that fingerprint reproduces - a seed moved to a different machine
     * yields the wrong key and GCM decryption fails (fail-secure), so node-locking is cryptographic.
     */
    private static SecretKeySpec keyFromSeed(long seed) throws Exception {
        byte[] material = ("obzcure-blob:" + seed + "|" + fingerprint()).getBytes("UTF-8");
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(material); // 32 bytes -> AES-256
        return new SecretKeySpec(hash, "AES");
    }

    /**
     * Canonical hardware fingerprint = the machine identity a license binds to. Single source of truth:
     * both the decode key (above) and the value the client sends to the license server come from HERE, so
     * the machine bound at activation is exactly the machine whose fingerprint must reproduce the key.
     * Computed from stable machine attributes (PoC: client-side, so spoofable by an attacker who controls
     * the client - documented boundary). The -Dobzcure.fingerprint override (used to simulate a second
     * machine in the demo) is honoured ONLY in DEV_MODE; a release build computes it from hardware alone
     * and the override branch is compiled out (F2).
     */
    static String fingerprint() {
        if (DEV_MODE) {
            String override = System.getProperty("obzcure.fingerprint");
            if (override != null)
                return override;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(System.getProperty("os.name")).append('|');
            sb.append(System.getProperty("os.arch")).append('|');
            sb.append(Runtime.getRuntime().availableProcessors()).append('|');
            try { sb.append(java.net.InetAddress.getLocalHost().getHostName()); } catch (Exception ignore) {}
            sb.append('|');
            try { sb.append(new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of("/etc/machine-id")), java.nio.charset.StandardCharsets.UTF_8).trim()); }
            catch (Exception ignore) {}
            sb.append('|');
            try {
                java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
                while (ifs.hasMoreElements()) {
                    byte[] mac = ifs.nextElement().getHardwareAddress();
                    if (mac != null) { for (byte b : mac) sb.append(String.format("%02x", b)); break; }
                }
            }
            catch (Exception ignore) {}
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : h) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Build time: returns [12-byte IV][ciphertext + GCM tag]. Throws unchecked on failure. */
    public static byte[] encrypt(byte[] plain, long seed) {
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, keyFromSeed(seed), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("blob encryption failed", e);
        }
    }

    /**
     * Load time: input is [12-byte IV][ciphertext + tag]. Throws unchecked (RuntimeException wrapping
     * AEADBadTagException) if the blob was tampered or the seed is wrong = fail-secure at the caller.
     */
    public static byte[] decrypt(byte[] enc, long seed) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, keyFromSeed(seed), new GCMParameterSpec(TAG_BITS, enc, 0, IV_LEN));
            return c.doFinal(enc, IV_LEN, enc.length - IV_LEN);
        } catch (Exception e) {
            throw new RuntimeException("blob decryption failed (tampered blob or wrong seed)", e);
        }
    }
}
