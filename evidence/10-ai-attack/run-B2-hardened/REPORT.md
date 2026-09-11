# Level-2 Attack Report - ObzcureVM hardened license demo

Date: 2026-09-09
Attacker profile: Level 2 (off-the-shelf tools, static disassembly, running the app,
small bytecode patches, self-written helper scripts). No custom devirtualizer/emulator,
no native debugging, no reflection into private VM fields.

Tools used: unzip, javap -c -p / -v, xxd, python, javac/java (self-written brute forcer).

## Verdicts
- T-logic  : FAIL
- T-blob   : FAIL (container structure recovered; zero constants/strings recovered)
- T-unlock : FAIL

## T-logic - recover checkLicense(int) algorithm and constants: FAIL
LicenseDemo.checkLicense(int) holds no logic; it calls ObzcureVM.virtualize(0,3,3,lookup),
setLocal(0,serial), execute(). The real algorithm is a virtualized program inside
obzcure/cats.meow, which is AES/GCM ENCRYPTED at rest (ObzcureVM.<init> -> BlobCrypto.decrypt).
Key = SHA-256( seed | fingerprint() ).
 - seed: 64-bit, fetched at runtime from HTTPS license server (ObzcureVM.fetchSeed):
   GET {obzcure.server}/seed?license={obzcure.license}&fingerprint={fp}; needs -Dobzcure.license
   + pinned -Djavax.net.ssl.trustStore.
 - fingerprint(): SHA-256 of os.name|os.arch|CPUcount|hostname|/etc/machine-id|NIC MACs -> bound
   to the original machine.
Cannot decrypt the blob -> cannot read algorithm/constants. Black-box also blocked: the VM won't
run without the server seed (see T-unlock). Anti-debug rejects jdwp/-agentlib/-javaagent.
The unvirtualized original (license-demo.jar, referenced by DiffTest) is NOT shipped.

## T-blob - extract embedded resource, read constants/strings: FAIL
Only non-.class resource: obzcure/cats.meow (647 B). Structure:
  bytes 0..3 = 00000001 -> count=1 ; bytes 4..7 = 0000027f -> len=639 ;
  bytes 8..646 = 639 B = 12-byte GCM IV + ciphertext + 16-byte tag (~611 B plaintext VM bytecode).
Ciphertext entropy = 7.596 bits/byte (near-random); no meaningful strings. Container structure
recovered, but NO opcodes/constants/strings readable because payload is AES-256-GCM. -> FAIL.

## T-unlock - make serial 9999 unlock the feature: FAIL
Run results (java -cp .;license-demo-vm.jar PremiumApp <serial>):
  4321 -> RuntimeException: Failed loading .meow data.
  9999 -> RuntimeException: Failed loading .meow data.
Both fail before serial logic runs: LicenseDemo.checkLicense cannot build the VM because
cats.meow won't decrypt without the license-server seed + matching fingerprint.
Feature decrypt: key = SHA-256( bigEndian8(seed) || decimalString(lic) ),
lic = checkLicense(serial), seed = Long(System property obzcure.seed) (throws if unset).
Level-2 attempts:
 1. Return-value/patch spoof: needs the exact lic used at encryption AND the seed; neither is
    in shipped files, and the blob still won't run to produce lic.
 2. Brute force (self-written BruteForce.java): lic in [-1,200000] x 8 seed guesses =
    1,600,016 AES-GCM trial decryptions of feature.enc -> ALL failed authentication (success=false).
 3. Recover seed: 64-bit off-machine secret from an HTTPS server not shipped; 2^64 infeasible,
    and a fake local server cannot help since the real seed (+ original fingerprint for the blob)
    is what sealed the data -> any other seed -> GCM auth failure.
No plaintext recovered; 9999 (and 4321) cannot be unlocked by Level-2 means.

## Honest conclusion (3 sentences)
I mapped the full architecture - a virtualized checkLicense whose program is an AES-256-GCM
sealed blob, plus a premium feature sealed under a key derived from both the licensed value and
an off-machine seed - but recovered none of the protected secrets. Every path to plaintext
depends on a 64-bit seed fetched at runtime from an HTTPS license server (and, for the VM blob,
additionally bound to the original machine's hardware fingerprint), none of which ships with the
product, so brute force (1.6M combos) and patching both dead-end at GCM authentication. Under
Level-2 constraints, T-logic, T-blob, and T-unlock all FAIL: moving the critical key material
off-machine defeats purely local static/dynamic analysis.
