// Minimal M1 demo. checkLicense() stays inside ObzcureVM's supported
// instruction subset (integer arithmetic, xor, modulo, a comparison and a
// branch), so ObzcureVM CAN virtualize it, and it is now tagged so ObzcureVM
// WILL virtualize it.
//
// main() uses string concatenation (invokedynamic) and is NOT tagged, so it
// stays plain and readable. After virtualization, main() still shows a normal
// call to checkLicense(), but checkLicense()'s body is replaced by a call into
// the interpreter. That contrast is your Figure 2.
public class LicenseDemo {

    // ObzcureVM only virtualizes methods carrying an annotation whose simple
    // name is "ApplyObzcureVM". Declaring our own marker is all it takes.
    public @interface ApplyObzcureVM { }

    public static void main(String[] args) {
        int serial = 4321;
        int result = checkLicense(serial);
        System.out.println("checkLicense(" + serial + ") = " + result);
    }

    // The method we want to protect. Pure integer logic, and now tagged.
    @ApplyObzcureVM
    public static int checkLicense(int serial) {
        int magic = 7919;
        int r = serial * 31;
        r = r ^ magic;
        r = r + (serial % 17);
        if (r < 0) {
            r = -r;
        }
        return r % 100000;
    }
}
