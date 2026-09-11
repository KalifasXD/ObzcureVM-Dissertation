import java.nio.file.*;
public class VaultGen { // vendor build step: seal the feature with the CORRECT license value
    public static void main(String[] a) throws Exception {
        int correct = Vault.licenseValue(4321);
        byte[] enc = Vault.encrypt("PREMIUM: all features enabled".getBytes("UTF-8"), correct);
        Files.write(Path.of("feature.enc"), enc);
        System.out.println("sealed feature with licenseValue(4321)=" + correct + " -> feature.enc ("+enc.length+"B)");
    }
}
