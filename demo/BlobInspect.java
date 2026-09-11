import java.util.zip.*;
import java.io.*;

// Anti-dump verification probe (JDK-only; no unzip/xxd/strings needed).
// Reads obzcure/cats.meow straight out of a jar and checks whether the sensitive material that
// used to leak in plaintext (the secret constant 7919, and the class/method metadata) is still
// visible. After encrypt-at-rest, all of these must be ABSENT and the bytes should look random.
public class BlobInspect {
    public static void main(String[] args) throws Exception {
        String jar = args[0];
        try (ZipFile zf = new ZipFile(jar)) {
            ZipEntry e = zf.getEntry("obzcure/cats.meow");
            if (e == null) { System.out.println("cats.meow NOT FOUND in " + jar); return; }
            byte[] b = zf.getInputStream(e).readAllBytes();
            System.out.println("cats.meow length = " + b.length + " bytes");

            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(32, b.length); i++) hex.append(String.format("%02x ", b[i]));
            System.out.println("first 32 bytes : " + hex.toString().trim());

            System.out.println("contains 7919 (00 00 1e ef) ? " + contains(b, new byte[]{0,0,0x1e,(byte)0xef})
                    + "   <- want false");
            String ascii = new String(b, "ISO-8859-1");
            for (String s : new String[]{"Meow", "LicenseDemo", "checkLicense"})
                System.out.println("contains ASCII \"" + s + "\" ? " + ascii.contains(s) + "   <- want false");

            boolean[] seen = new boolean[256]; int distinct = 0;
            for (byte x : b) if (!seen[x & 0xff]) { seen[x & 0xff] = true; distinct++; }
            System.out.println("distinct byte values = " + distinct + "/256   <- high (~230+) = looks encrypted");
        }
    }

    static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }
}
