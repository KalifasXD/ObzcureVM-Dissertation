import java.util.Comparator;

// Task-4 demo (UNSUPPORTED case). A Comparator lambda is NOT in ObzcureVM's supported
// set of functional interfaces, so the invokedynamic that builds it cannot be
// virtualized. At BUILD time the translator aborts with an IllegalStateException that
// names the offending method. This is "where invokedynamic breaks".
public class UnsupportedIndy {

    public @interface ApplyObzcureVM { }

    @ApplyObzcureVM
    public static int checkLicense(int serial) {
        // lambda -> invokedynamic (LambdaMetafactory) producing a java.util.Comparator
        Comparator<Integer> cmp = (a, b) -> ((a * 31) ^ 7919) + (a % 17);
        return cmp.compare(serial, 0);
    }

    public static void main(String[] a) {
        System.out.println("checkLicense(4321) = " + checkLicense(4321));
    }
}
