# Attack A - Level-2 Baseline Assessment

**Date:** 2026-09-09
**Attacker profile:** Level 2 (unzip, javap -c -p, xxd, python, running the app, static inspection). No custom devirtualizer, no debugger, no runtime reflection.

## Per-jar verdict

| Jar | Protection | Verdict | Algorithm | Constants |
|-----|-----------|---------|-----------|-----------|
| license-demo.jar | none (plain bytecode) | SUCCESS | fully recovered | 7919, 31, 17, 100000 |
| build-a.jar | ObzcureVM virtualization | SUCCESS | fully recovered | 7919, 31, 17, 100000 |

## Recovered algorithm (identical in both builds)

    int checkLicense(int serial) {
        int k = 7919;
        int r = serial * 31;
        r = r ^ k;
        r = r + (serial % 17);
        if (r < 0) r = -r;
        return r % 100000;
    }

Secret constants: 7919, 31, 17, 100000.
Verification: checkLicense(4321) = 36659 (plain jar prints this; Python re-impl of the recovered formula reproduces 36659).

## How each was recovered

### license-demo.jar (plain)
javap -c -p disassembles checkLicense into readable bytecode (sipush 7919; bipush 31/imul; ixor; bipush 17/irem/iadd; ifge/ineg; ldc 100000/irem/ireturn). Formula and constants read straight off.

### build-a.jar (ObzcureVM)
checkLicense no longer holds the algorithm; it calls ObzcureVM.virtualize(0,3,3,lookup), sets local 0 = serial, and execute(). The real program is a non-class embedded resource: obzcure/cats.meow (619 bytes), stored in PLAINTEXT.
- Strings leak identity: Meow, LicenseDemo, checkLicense, (I)I, then the VM node list.
- All four secret constants appear verbatim as big-endian ints: 0x1EEF=7919, 0x1F=31, 0x11=17, 0x186A0=100000 (each confirmed by byte search).
- The decoded node stream maps 1:1 onto the plain build (push 7919; serial*31; XOR; +serial%17; conditional negate via forward jump; %100000; return).
- build-a.jar could not run here (runtime JDK 18 refuses ObzcureVM's class-file v61 preview class: UnsupportedClassVersionError). Execution was unnecessary; static resource extraction sufficed.

## Honest summary
Both builds fell to a Level-2 attacker. The plain jar exposes the algorithm directly in javap output; the ObzcureVM build stores its virtualized program in a plaintext embedded resource (obzcure/cats.meow) whose strings and constants are readable with xxd/python, so the same formula and all secret constants (7919, 31, 17, 100000) were recovered with no devirtualizer, debugger, or runtime reflection.

## Scope note
Byte-accurate semantics of every VM opcode would require interpreting VMInsnNode.execute(); opcode semantics were inferred by structural correspondence to the plain sibling build (valid Level-2 differential inference). The constants are exact, read directly from the cleartext resource.
