import java.util.*;
// Probe: dump the target's live thread names each second, and flag two candidate v2 signals:
//   (a) a thread whose name contains "attach listener"  (HotSpot's Attach-API listener)
//   (b) a thread whose name contains "jdwp"             (the old, reverted signal)
public class ThreadWatch {
    static TreeSet<String> names() {
        TreeSet<String> s = new TreeSet<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) s.add(t.getName());
        return s;
    }
    public static void main(String[] a) throws Exception {
        System.out.println("TARGET_PID=" + ProcessHandle.current().pid());
        System.out.println("initial threads: " + names());
        System.out.flush();
        for (int i = 0; ; i++) {
            TreeSet<String> n = names();
            boolean attachListener = n.stream().anyMatch(x -> x.toLowerCase().contains("attach listener"));
            boolean jdwp = n.stream().anyMatch(x -> x.toLowerCase().contains("jdwp"));
            System.out.println("[iter " + i + "]  attachListenerThread=" + attachListener
                    + "  jdwpThread=" + jdwp + "   threads=" + n);
            System.out.flush();
            Thread.sleep(1000);
        }
    }
}
