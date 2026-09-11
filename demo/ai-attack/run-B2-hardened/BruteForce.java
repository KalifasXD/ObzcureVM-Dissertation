import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

public class BruteForce {
  static byte[] key(int lic, long seed) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update(ByteBuffer.allocate(8).putLong(seed).array());
    md.update(("" + lic).getBytes("UTF-8"));
    return md.digest();
  }
  static boolean tryDec(byte[] enc, int lic, long seed) {
    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(lic,seed),"AES"),
             new GCMParameterSpec(128, enc, 0, 12));
      byte[] pt = c.doFinal(enc, 12, enc.length-12);
      System.out.println("SUCCESS lic="+lic+" seed="+seed+" plaintext="+new String(pt,"UTF-8"));
      return true;
    } catch (Exception e) { return false; }
  }
  public static void main(String[] a) throws Exception {
    byte[] enc = Files.readAllBytes(Path.of("feature.enc"));
    long[] seeds = {0L,1L,4321L,9999L,42L,123456789L,-1L,1234567890123456789L};
    long tries=0; boolean hit=false;
    for (long s : seeds)
      for (int lic=-1; lic<=200000; lic++){ tries++; if(tryDec(enc,lic,s)){hit=true;} }
    System.out.println("done tries="+tries+" success="+hit);
  }
}
