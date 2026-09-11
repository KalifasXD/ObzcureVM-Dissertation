import java.nio.file.Files;
import java.nio.file.Path;

// JabRef injection milestone - Component 4: the vendor seal (build-time tool, NOT injected).
//
// Mirrors the F4 FeatureGen pattern. It computes checkLicense(VALID_SERIAL) from the ORIGINAL
// (non-virtualized) LicenseGuard - a deterministic, seed-free pure-math value - and seals a
// marker payload under an AES-256-GCM key derived from that wide 64-bit value, writing guard.enc.
//
// There is no boolean gate. At runtime the injected LicenseEnforcer can unseal guard.enc only if
// the VIRTUALIZED guard reproduces the same value for the supplied serial, i.e. only if the genuine
// protected computation runs. The payload is a self-contained marker (DESIGN.md section 9): it
// demonstrates result-binding on the real application, it is not wired to a specific JabRef feature.
public class GuardSealer {

    // The vendor's licensed serial. Any other serial yields a different guard value and cannot unseal.
    static final int VALID_SERIAL = 4321;
    static final String PAYLOAD = "JABREF-PREMIUM-ENABLED";

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "guard.enc");
        long value = LicenseGuard.checkLicense(VALID_SERIAL); // original guard: deterministic, no seed
        byte[] sealed = LicenseEnforcer.encrypt(PAYLOAD.getBytes("UTF-8"), LicenseEnforcer.key(value));
        Files.write(out, sealed);
        System.out.println("sealed \"" + PAYLOAD + "\" under checkLicense(" + VALID_SERIAL + ")=" + value
                + " -> " + out + " (" + sealed.length + " bytes)");
    }
}
