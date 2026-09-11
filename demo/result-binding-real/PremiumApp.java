import java.nio.file.*;

// The REAL consuming edge (F4): the app's premium feature is derived from the VIRTUALIZED checkLicense
// output. There is NO boolean gate - nothing to flip. If the serial is licensed, checkLicense returns
// the value that decrypts the feature and the app uses it; otherwise the feature stays sealed. To unlock
// a pirate serial the attacker must make checkLicense return the licensed value = defeat the virtualized,
// diversified, AES-GCM-encrypted, off-machine-seeded, fingerprint-node-locked computation.
public class PremiumApp {
    public static void main(String[] a) throws Exception {
        int serial = a.length > 0 ? Integer.parseInt(a[0]) : 4321;

        // Runs INSIDE ObzcureVM (needs the off-machine seed): this is the genuine protected computation.
        int v = Feature.checkLicense("license-demo-vm.jar", serial);
        System.out.println("  checkLicense[virtualized](" + serial + ") = " + v);

        byte[] enc = Files.readAllBytes(Path.of("feature.enc"));
        long seed = Feature.seed();                             // off-machine secret, the same one the VM uses
        try {
            byte[] plain = Feature.decrypt(enc, v, seed);        // correct value AND seed REQUIRED (no boolean)
            System.out.println("  serial " + serial + " -> UNLOCKED: " + new String(plain, "UTF-8"));
        } catch (Exception e) {
            System.out.println("  serial " + serial + " -> LOCKED (feature stays sealed; wrong license value)");
        }
    }
}
