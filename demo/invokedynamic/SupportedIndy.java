import java.util.function.Supplier;

// Task-4 demo (SUPPORTED case). ObzcureVM has a hand-coded shim that can virtualize
// invokedynamic instructions ONLY for a fixed set of functional interfaces:
// Runnable, Consumer/IntConsumer/LongConsumer/DoubleConsumer, Function, Predicate,
// Supplier, plus java.lang.String concatenation (StringConcatFactory).
// A Supplier lambda is in that set, so this method CAN be virtualized.
public class SupportedIndy {

    // Marker: ObzcureVM virtualizes a method tagged with an annotation whose simple
    // name is "ApplyObzcureVM" (default CLASS retention, like the other demos).
    public @interface ApplyObzcureVM { }

    @ApplyObzcureVM
    public static int checkLicense(int serial) {
        // lambda -> invokedynamic (LambdaMetafactory) producing a java.util.function.Supplier
        Supplier<Integer> s = () -> ((serial * 31) ^ 7919) + (serial % 17);
        return s.get();
    }

    public static void main(String[] a) {
        System.out.println("checkLicense(4321) = " + checkLicense(4321));
    }
}
