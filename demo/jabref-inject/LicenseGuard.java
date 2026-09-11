// JabRef injection milestone - Component 1: the virtualized critical method.
//
// This is the ONLY class fed to ObzcureVM. It contains a single tagged method,
// a fixed 64-bit bijective mixer (SplitMix64 finalizer style). Dependencies:
// java.lang.* only. No invokedynamic, no Strings, no method calls, no division/
// modulo, so its frames are trivially computable and ObzcureVM can virtualize it
// cleanly (DESIGN.md sections 5.1 and 6).
//
// The wide, injective output fixes the demo's F4 "% 100000" collision boundary:
// the result-binding key is now unique per serial.
public class LicenseGuard {

    // ObzcureVM only virtualizes methods carrying an annotation whose simple
    // name is "ApplyObzcureVM" (same marker the M1 demo uses).
    public @interface ApplyObzcureVM { }

    @ApplyObzcureVM
    public static long checkLicense(int serial) {
        long z = (serial & 0xFFFFFFFFL) ^ 0x9E3779B97F4A7C15L; // mix in a fixed nonce
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z =  z ^ (z >>> 31);
        return z; // wide, injective over the serial domain: no % 100000, no collisions
    }
}
