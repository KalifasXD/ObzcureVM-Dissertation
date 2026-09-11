import obzcu.re.virtualmachine.BlobCrypto;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

// Blob-entropy experiment (F8 / V3): replace the crude distinct-byte "smell test" with a rigorous
// randomness measure. For each build we pull the per-method blob out of cats.meow ([count][len][blob...]),
// then compare the ENCRYPTED blob (anti-dump, C3) against its DECRYPTED plaintext (the diversified VM
// program, C1) using Shannon entropy (bits/byte, ideal 8.0) and a chi-square uniformity test on the byte
// histogram. Concatenating N builds gives enough bytes for chi-square to have power.
//
// args: <pairs-file>  (each line: "<cats.meow path>\t<per-build seed>")
public class EntropyExp {
    static byte[] firstBlob(byte[] cm) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(cm));
        dis.readInt();                       // count (>=1); we take method index 0
        int len = dis.readInt();
        byte[] b = new byte[len]; dis.readFully(b); return b;
    }
    static double shannon(long[] h, long n) {
        double e = 0;
        for (long c : h) if (c > 0) { double p = (double) c / n; e -= p * (Math.log(p) / Math.log(2)); }
        return e;
    }
    static double chi2(long[] h, long n) {
        double exp = (double) n / 256.0, s = 0;
        for (long c : h) { double d = c - exp; s += d * d / exp; }
        return s;
    }
    static void report(String label, List<byte[]> blobs) {
        long[] h = new long[256]; long n = 0;
        for (byte[] b : blobs) { for (byte x : b) h[x & 0xff]++; n += b.length; }
        int distinct = 0; for (long c : h) if (c > 0) distinct++;
        double H = shannon(h, n), X = chi2(h, n);
        System.out.printf("%-32s bytes=%-6d  distinct=%3d/256  entropy=%.4f bits/byte  chi2=%9.1f (df=255, crit@0.05=293.2 -> %s)%n",
                label, n, distinct, H, X, (X < 293.25 ? "consistent with UNIFORM" : "NON-uniform (structured)"));
    }
    public static void main(String[] a) throws Exception {
        List<byte[]> enc = new ArrayList<>(), plain = new ArrayList<>();
        int builds = 0;
        for (String line : Files.readAllLines(Paths.get(a[0]))) {
            if (line.trim().isEmpty()) continue;
            String[] t = line.split("\t");
            byte[] cm = Files.readAllBytes(Paths.get(t[0].trim()));
            long seed = Long.parseLong(t[1].trim());
            byte[] e = firstBlob(cm); enc.add(e);
            plain.add(BlobCrypto.decrypt(e, seed));      // public static; same-machine fingerprint reproduces the key
            builds++;
        }
        System.out.println("=========================================================================");
        System.out.println("Blob entropy / uniformity (F8 / V3) - corpus of " + builds + " per-build blobs");
        System.out.println("=========================================================================");
        report("PLAINTEXT VM program (C1)", plain);
        report("ENCRYPTED blob (anti-dump, C3)", enc);
        System.out.println("=========================================================================");
    }
}
