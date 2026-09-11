public class ArgPrint {
    public static void main(String[] a){
        System.out.println("getInputArguments() = "
            + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
    }
}
