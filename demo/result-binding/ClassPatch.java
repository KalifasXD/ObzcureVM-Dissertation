import java.nio.file.*;
// JDK-only bytecode patcher: replace the first occurrence of a hex byte-sequence with another of
// equal length. Models a Level-2 attacker editing compiled bytecode (no source, no ASM needed).
// usage: java ClassPatch <in.class> <out.class> <findHex> <replaceHex>
public class ClassPatch {
    public static void main(String[] a) throws Exception {
        byte[] data = Files.readAllBytes(Path.of(a[0]));
        byte[] find = hex(a[2]), repl = hex(a[3]);
        if (find.length != repl.length) throw new IllegalArgumentException("find/replace differ in length");
        int i = indexOf(data, find);
        if (i < 0) throw new IllegalStateException("pattern not found: " + a[2]);
        System.arraycopy(repl, 0, data, i, repl.length);
        Files.write(Path.of(a[1]), data);
        System.out.println("patched " + a[0] + " @offset " + i + ": " + a[2] + " -> " + a[3]);
    }
    static byte[] hex(String s){ byte[] b=new byte[s.length()/2]; for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16); return b; }
    static int indexOf(byte[] h, byte[] n){ outer: for(int i=0;i+n.length<=h.length;i++){ for(int j=0;j<n.length;j++) if(h[i+j]!=n[j]) continue outer; return i;} return -1; }
}
