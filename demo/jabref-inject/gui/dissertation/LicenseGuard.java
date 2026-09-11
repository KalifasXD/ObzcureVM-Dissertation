package dissertation;

// JabRef GUI demo - Component 1 (packaged copy of the virtualized critical method).
//
// Identical logic to demo/jabref-inject/LicenseGuard.java; the only change is that it
// lives in package `dissertation` instead of the default package. This is REQUIRED for
// the modular launch: JabRef's patched launcher lives in the named module `org.jabref`,
// and a named module cannot reference a class in the default package. Moving the guard
// into a real package lets the launcher call it (via --add-reads org.jabref=ALL-UNNAMED).
//
// java.lang.* only; no invokedynamic / Strings / calls / division, so ObzcureVM can
// virtualize it cleanly. The wide, injective 64-bit output binds the result key per serial.
public class LicenseGuard {

    // ObzcureVM virtualizes methods whose annotation simple name is "ApplyObzcureVM".
    public @interface ApplyObzcureVM { }

    @ApplyObzcureVM
    public static long checkLicense(int serial) {
        long z = (serial & 0xFFFFFFFFL) ^ 0x9E3779B97F4A7C15L; // mix in a fixed nonce
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z =  z ^ (z >>> 31);
        return z; // wide, injective over the serial domain
    }
}
