import java.nio.file.*;
public class BoundDemo { // result-binding: the feature IS the decrypted value; no boolean gate
    public static void main(String[] a) throws Exception {
        int serial = a.length>0 ? Integer.parseInt(a[0]) : 4321;
        byte[] enc = Files.readAllBytes(Path.of("feature.enc"));
        try {
            byte[] plain = Vault.decrypt(enc, Vault.licenseValue(serial)); // correct value REQUIRED
            System.out.println("serial " + serial + " -> UNLOCKED: " + new String(plain, "UTF-8"));
        } catch (Exception e) {
            System.out.println("serial " + serial + " -> LOCKED (invalid license)");
        }
    }
}
