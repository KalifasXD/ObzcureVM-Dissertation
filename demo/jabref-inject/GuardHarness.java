// JabRef injection milestone - step-7 verification harness (NOT part of the protected artifact).
//
// Exercises the exact injected code path against jabref-protected.jar: it invokes the same
// LicenseEnforcer.enforce() that the injector wired into JabRefMain.main. JabRef's GUI cannot launch
// under a bare JDK (its JavaFX superclass is absent, DESIGN.md section 7), so this harness stands in
// for main() to prove the guard/enforcer path on the real bytecode. Run with jabref-protected.jar on
// the classpath so LicenseGuard/LicenseEnforcer resolve to the VIRTUALIZED, repacked versions.
//
// Usage: java -cp "jabref-protected.jar;<harnessDir>" -Dlicense.serial=<n> [-Dobzcure.seed=<seed>] GuardHarness
public class GuardHarness {
    public static void main(String[] args) {
        int serial = Integer.getInteger("license.serial", -1);
        // Diagnostic (mirrors the F4 PremiumApp print): run the virtualized guard directly and show its
        // wide value. This both demonstrates result-binding (value is unique per serial) and, if the guard
        // fails to run, surfaces the real cause that enforce() deliberately suppresses (fail-secure, F7).
        try {
            long v = LicenseGuard.checkLicense(serial);
            System.out.println("[harness] checkLicense[virtualized](" + serial + ") = " + v);
        } catch (Throwable t) {
            System.out.println("[harness] the virtualized guard did not run: " + t);
        }
        LicenseEnforcer.enforce(); // the real injected edge: unseals guard.enc iff the value is correct
        System.out.println("[harness] enforce() returned normally -> the application would proceed");
    }
}
