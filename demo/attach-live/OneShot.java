import java.util.*;
public class OneShot {
    public static void main(String[] a){
        TreeSet<String> s = new TreeSet<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) s.add(t.getName());
        System.out.println("threads@startup=" + s);
    }
}
