// Target for the dynamic-attach demo. Launched CLEANLY (no debugger in its launch args), so
// anti-debug v1 passes. It calls the protected method once per second forever, and keeps running
// even if a call is blocked, so we can watch the exact iteration where an attached debugger
// changes the outcome.
public class LoopDemo {
    public static void main(String[] args) throws Exception {
        for (int i = 0; ; i++) {
            try {
                System.out.println("[iter " + i + "] checkLicense(4321) = " + LicenseDemo.checkLicense(4321));
            } catch (Throwable t) {
                while (t.getCause() != null) t = t.getCause();
                System.out.println("[iter " + i + "] BLOCKED: " + t.getMessage());
            }
            Thread.sleep(1000);
        }
    }
}
