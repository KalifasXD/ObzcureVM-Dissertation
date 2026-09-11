#!/usr/bin/env bash
# F3 (measure, not fix): the anti-debug guard antiDebugCheck() is UNVIRTUALIZED plaintext bytecode -
# a single invokestatic at the top of fetchSeed(). A verifier-safe ~3-byte NOP patch deletes that
# call, so a debugger launch that was BLOCKED now RUNS -> this FALSIFIES the V5 "anti-debug BLOCKED"
# cell for a tool-combining Level-2 attacker (mirror of the demo/result-binding gate-vs-bound contrast).
#
# PRE-REQ: build with BlobCrypto.DEV_MODE = true, then `mvn clean package -DskipTests`. The dev build
# is used ONLY so the seed can be supplied offline via -Dobzcure.seed (standing in for a licensed
# user's server-delivered seed). The antiDebugCheck guard is BYTE-IDENTICAL in a release build -
# DEV_MODE gates only the seed/fingerprint overrides (F2), never the guard - so the patch is faithful.
#
# Run:  bash demo/anti-debug-patch/run_anti_debug_patch.sh   (from anywhere; it finds the repo root)
set -u
cd "$(dirname "$0")/../.."                 # -> obzcurevm repo root (/work)
DEMO=demo/anti-debug-patch
JAR=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
CLASS=obzcu/re/virtualmachine/ObzcureVM.class
JDWP="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005"
LOG=$DEMO/EVIDENCE.log; : > "$LOG"; exec >>"$LOG" 2>&1
rm -rf "$DEMO/work"; mkdir -p "$DEMO/work"

echo "### 0. Virtualize (dev build) + capture the off-machine seed"
javac -d build/classes demo/DiffTest.java
SEED=$(java -jar "$JAR" -i license-demo.jar -o license-demo-vm.jar -rf -sd -f | grep -oP 'OBZCURE_SEED=\K-?[0-9]+')
echo "    seed=$SEED"
echo -n "    sanity, NO debugger (expect PASS): "
java -Dobzcure.seed="$SEED" -cp build/classes DiffTest 2>&1 | grep -E "PASS|FAIL|Exception" | head -1
echo

echo "### 1. The guard is UNVIRTUALIZED PLAINTEXT (javap of the SHIPPED ObzcureVM.class)"
( cd "$DEMO/work" && jar xf ../../../license-demo-vm.jar "$CLASS" )
javap -p -c -classpath "$DEMO/work" obzcu.re.virtualmachine.ObzcureVM \
    | grep -nE "private static void antiDebugCheck|invokestatic.*antiDebugCheck" | head
echo "    ^ antiDebugCheck() sits in cleartext and is dispatched by a plain invokestatic (not virtualized)."
echo

echo "### 2. V5 AS CLAIMED: launch under a debugger -> anti-debug BLOCKS it"
java -Dobzcure.seed="$SEED" $JDWP -cp build/classes DiffTest 2>&1 \
    | grep -E "Debugger or instrumentation|Failed loading|PASS" | head -2
echo

echo "### 3. ATTACK (Level-2, ~3 bytes, verifier-safe): NOP the antiDebugCheck invokestatic"
javac -d "$DEMO" "$DEMO/NopAntiDebug.java"
java -cp "$DEMO" NopAntiDebug "$DEMO/work/$CLASS" "$DEMO/work/$CLASS"
( cd "$DEMO/work" && jar uf ../../../license-demo-vm.jar "$CLASS" )
echo "    patched ObzcureVM.class re-inserted into license-demo-vm.jar"
echo

echo "### 4. V5 FALSIFIED: the SAME debugger launch now RUNS (guard gone; JDWP still listening on 5005)"
java -Dobzcure.seed="$SEED" $JDWP -cp build/classes DiffTest 2>&1 \
    | grep -E "Debugger or instrumentation|Mismatches|PASS|FAIL" | head -2
echo
echo "########## DONE:  BLOCKED before the patch  ->  the exact same launch PASSES after a 3-byte NOP ##########"
