import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

// Generic invokedynamic demo. Every checkLicense-style method below compiles to an
// invokedynamic (a lambda, a method reference, or string concatenation). The ones
// using Comparator, BiFunction, a custom functional interface or a method reference
// used to abort virtualization (they were outside the old hand-coded set); they now
// virtualize through the generic bootstrap path. Supplier and Function are kept so
// the previously-supported shapes are exercised by the same path.
//
// Each method is tagged for virtualization. main() prints the result of every method
// for a range of inputs; running this before and after virtualization (differential
// testing) must produce identical output.
public class GenericIndy {

    // Marker: ObzcureVM virtualizes a method tagged with an annotation whose simple
    // name is "ApplyObzcureVM" (bare declaration -> CLASS retention, like the other demos).
    public @interface ApplyObzcureVM { }

    @FunctionalInterface
    interface Transformer { int transform(int in); }

    @ApplyObzcureVM
    public static int viaSupplier(int serial) {           // Supplier: captures serial
        Supplier<Integer> s = () -> ((serial * 31) ^ 7919) + (serial % 17);
        return s.get();
    }

    @ApplyObzcureVM
    public static int viaFunction(int serial) {           // Function: one argument
        Function<Integer, Integer> f = x -> (x * 13) ^ 4242;
        return f.apply(serial);
    }

    @ApplyObzcureVM
    public static int viaComparator(int serial) {         // was unsupported
        Comparator<Integer> cmp = (a, b) -> ((a * 31) ^ 7919) + (a % 17);
        return cmp.compare(serial, 0);
    }

    @ApplyObzcureVM
    public static int viaBiFunction(int serial) {         // was unsupported
        BiFunction<Integer, Integer, Integer> bf = (a, b) -> (a * 100) + b;
        return bf.apply(serial, serial % 7);
    }

    @ApplyObzcureVM
    public static int viaCustomInterface(int serial) {    // was unsupported
        Transformer t = x -> (x ^ 0x5A5A) + (x >> 1);
        return t.transform(serial);
    }

    @ApplyObzcureVM
    public static int viaMethodReference(int serial) {    // was unsupported (method ref)
        BiFunction<Integer, Integer, Integer> sum = Integer::sum;
        return sum.apply(serial, 1000);
    }

    @ApplyObzcureVM
    public static String viaStringConcat(int serial) {    // StringConcatFactory
        return "serial=" + serial + ",sq=" + (serial * serial);
    }

    public static void main(String[] args) {
        int[] serials = { 4321, 0, 1, 2, 7, 15, 100, 99991, -5 };
        for (int s : serials) {
            System.out.println("S=" + s
                + " sup=" + viaSupplier(s)
                + " fun=" + viaFunction(s)
                + " cmp=" + viaComparator(s)
                + " bif=" + viaBiFunction(s)
                + " cust=" + viaCustomInterface(s)
                + " mref=" + viaMethodReference(s)
                + " cat=[" + viaStringConcat(s) + "]");
        }
    }
}
