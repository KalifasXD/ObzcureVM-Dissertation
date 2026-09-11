import javax.crypto.*; import javax.crypto.spec.*; import java.security.*;
public class Vault {
    // RESULT-BINDING: the licensed computation returns a VALUE (not a boolean). 4321 = valid serial.
    static int licenseValue(int serial) { return ((serial * 31) ^ 7919) + (serial % 17); }
    static SecretKeySpec key(int v) throws Exception {
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("lic:"+v).getBytes("UTF-8")), "AES");
    }
    static byte[] encrypt(byte[] p, int v) throws Exception {
        byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(v), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(p), out = new byte[12+ct.length];
        System.arraycopy(iv,0,out,0,12); System.arraycopy(ct,0,out,12,ct.length); return out;
    }
    static byte[] decrypt(byte[] e, int v) throws Exception { // throws if v is wrong (GCM tag)
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(v), new GCMParameterSpec(128, e, 0, 12));
        return c.doFinal(e, 12, e.length-12);
    }
}
