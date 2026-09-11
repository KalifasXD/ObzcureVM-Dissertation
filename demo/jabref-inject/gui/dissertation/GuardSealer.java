package dissertation;

import java.nio.file.Files;
import java.nio.file.Path;

// JabRef GUI demo - Component 4 (packaged copy of the vendor seal, build-time only).
// Identical to demo/jabref-inject/GuardSealer.java except for `package dissertation;`.
// Seals a marker payload under checkLicense(VALID_SERIAL) computed from the ORIGINAL
// (non-virtualized) guard - deterministic and seed-free, so guard.enc is identical to
// the headless pipeline's. The virtualized guard must reproduce the same value to unseal.
public class GuardSealer {

    static final int VALID_SERIAL = 4321;
    static final String PAYLOAD = "JABREF-PREMIUM-ENABLED";

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "guard.enc");
        long value = LicenseGuard.checkLicense(VALID_SERIAL);
        byte[] sealed = LicenseEnforcer.encrypt(PAYLOAD.getBytes("UTF-8"), LicenseEnforcer.key(value));
        Files.write(out, sealed);
        System.out.println("sealed \"" + PAYLOAD + "\" under checkLicense(" + VALID_SERIAL + ")=" + value
                + " -> " + out + " (" + sealed.length + " bytes)");
    }
}
