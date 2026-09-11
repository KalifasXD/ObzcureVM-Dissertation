package dissertation;

// Headless gate check: calls the injected enforce() directly (no JavaFX), so the
// license decision can be verified without opening a window. Mirrors what the patched
// JabRefLauncher.main does as its first instruction. Valid -> prints "verified" and
// returns; pirate/no-license -> enforce() calls System.exit(1) (fail-secure).
public class Probe {
    public static void main(String[] args) {
        LicenseEnforcer.enforce();
        System.out.println("[probe] enforce() returned -> JabRef GUI would now launch");
    }
}
