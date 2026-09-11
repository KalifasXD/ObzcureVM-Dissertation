public class GateDemo {
    // BOOLEAN LICENSE GATE = the classic single-point-of-failure.
    static boolean isLicensed(int serial) { return serial == 4321; } // 4321 = the one valid key
    public static void main(String[] args) {
        int serial = args.length > 0 ? Integer.parseInt(args[0]) : 4321;
        if (isLicensed(serial)) System.out.println("serial " + serial + " -> UNLOCKED (all features)");
        else                    System.out.println("serial " + serial + " -> LOCKED (buy a license)");
    }
}
