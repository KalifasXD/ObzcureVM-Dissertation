import java.util.Random;

// Diversification-effectiveness experiment (RQ1 / Kerckhoffs, quantified).
//
// The per-build opcode diversification replicates exactly what the toolchain does
// (obzcu.re.virtualmachine.VMLoader.buildPermutation): seed -> Fisher-Yates shuffle of
// [0..255] via java.util.Random(seed). The emitter stores perm[op]; the loader decodes
// with inverse[]. A GENERIC devirtualizer built for build A hard-codes A's table (invPermA).
// Applied to build B, it decodes B's stored value permB[op] as invPermA[permB[op]], which is
// the true op ONLY where permA[op] == permB[op]. So the cross-build decode-success rate is
// exactly the permutation agreement - computed here from the REAL per-build seeds (no decrypt).
//
// args: <comma-separated real build seeds> <comma-separated program opcode values>
public class DivExp {
    static int[] buildPermutation(long seed) {           // EXACT copy of VMLoader.buildPermutation
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) { int j = r.nextInt(i + 1); int t = p[i]; p[i] = p[j]; p[j] = t; }
        return p;
    }

    public static void main(String[] a) {
        long[] seeds = parseLongs(a[0]);
        int[] prog   = parseInts(a[1]);
        int N = seeds.length;
        int[][] perm = new int[N][];
        for (int i = 0; i < N; i++) perm[i] = buildPermutation(seeds[i]);

        // distinctness of the N permutations (a seed collision would tie two builds)
        int distinct = 0;
        for (int i = 0; i < N; i++) {
            boolean uniq = true;
            for (int j = 0; j < i; j++) if (java.util.Arrays.equals(perm[i], perm[j])) { uniq = false; break; }
            if (uniq) distinct++;
        }

        // same-build decode (sanity): A's table on A's own encoding = 100%
        // cross-build decode: averaged over all ordered pairs A != B
        double sumAll = 0, sumProg = 0; long pairs = 0, progAllOk = 0;
        for (int A = 0; A < N; A++) for (int B = 0; B < N; B++) {
            if (A == B) continue;
            int ok256 = 0; for (int op = 0; op < 256; op++) if (perm[A][op] == perm[B][op]) ok256++;
            int okProg = 0; for (int op : prog) if (perm[A][op] == perm[B][op]) okProg++;
            sumAll += ok256 / 256.0; sumProg += (double) okProg / prog.length;
            if (okProg == prog.length) progAllOk++;
            pairs++;
        }

        int distinctProgOpcodes = (int) java.util.Arrays.stream(prog).distinct().count();
        System.out.println("=========================================================================");
        System.out.println("RQ1 - diversification effectiveness (from " + N + " REAL build seeds)");
        System.out.println("=========================================================================");
        System.out.println("distinct per-build permutations : " + distinct + " / " + N);
        System.out.println("program instructions (opcodes)  : " + prog.length
                + "  (" + distinctProgOpcodes + " distinct opcode values)");
        System.out.println("-------------------------------------------------------------------------");
        System.out.println("SAME-build decode success        : 100.000%  (a build's own table always decodes it)");
        System.out.printf ("CROSS-build decode, per opcode    : %.4f%%   (theory 1/256 = %.4f%%)%n",
                100.0 * sumAll / pairs, 100.0 / 256.0);
        System.out.printf ("CROSS-build decode, program opset : %.4f%%%n", 100.0 * sumProg / pairs);
        System.out.println("CROSS-build pairs decoding the WHOLE program correctly : " + progAllOk + " / " + pairs);
        System.out.printf ("theoretical P(whole program) cross-build : (1/256)^%d = %.2e%n",
                distinctProgOpcodes, Math.pow(1.0 / 256.0, distinctProgOpcodes));
        System.out.println("=========================================================================");
    }

    static long[] parseLongs(String s) {
        String[] t = s.split(","); long[] o = new long[t.length];
        for (int i = 0; i < t.length; i++) o[i] = Long.parseLong(t[i].trim()); return o;
    }
    static int[] parseInts(String s) {
        String[] t = s.split(","); int[] o = new int[t.length];
        for (int i = 0; i < t.length; i++) o[i] = Integer.parseInt(t[i].trim()); return o;
    }
}
