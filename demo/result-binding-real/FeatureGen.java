import java.nio.file.*;

// VENDOR build step (F4): seal the premium feature under the CORRECT license value.
// The correct value is computed from the ACTUAL checkLicense method (loaded from the original
// license-demo.jar) - NOT a plaintext re-implementation. This is what the audit's F2/F4 note flagged
// was missing: the seal is now tied to the real license computation, whose runtime form is virtualized.
public class FeatureGen {
    static final int VALID_SERIAL = 4321;
    public static void main(String[] a) throws Exception {
        int correct = Feature.checkLicense("license-demo.jar", VALID_SERIAL); // the real original method
        long seed = Feature.seed();                                           // off-machine secret (section 4.5)
        byte[] enc = Feature.encrypt("PREMIUM: all features enabled".getBytes("UTF-8"), correct, seed);
        Files.write(Path.of("feature.enc"), enc);
        System.out.println("sealed feature with checkLicense(" + VALID_SERIAL + ")=" + correct
                + " -> feature.enc (" + enc.length + "B)");
    }
}
