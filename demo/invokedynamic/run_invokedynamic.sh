#!/usr/bin/env bash
# =============================================================================
# Task-4 demo: WHERE invokedynamic breaks ObzcureVM virtualization.
#
# ObzcureVM translates invokedynamic only for a FIXED, hand-coded set of
# functional interfaces (see src/.../vm/translator/TranslateInvokeDynamics.java
# and src/.../virtualmachine/types/VMInvokeDynamicInsnNode.java):
#   Runnable, Consumer/IntConsumer/LongConsumer/DoubleConsumer, Function,
#   Predicate, Supplier, and java.lang.String concatenation (StringConcatFactory).
# A lambda of ANY other functional interface hits `default: return false` in
# TranslateInvokeDynamics.translate(), and Translator.java then throws
#   IllegalStateException: Invokedynamic instruction has unsupported arguments in: ...
#
# CASE A (SUPPORTED):   Supplier lambda  -> virtualizes (checkLicense = dispatch shell)
# CASE B (UNSUPPORTED): Comparator lambda -> build aborts, pinpointing the method
# Reproduce:  bash demo/invokedynamic/run_invokedynamic.sh 2>&1 | tee demo/invokedynamic/EVIDENCE.log
# =============================================================================
set -u
cd "$(dirname "$0")/../.."                 # -> obzcurevm root
JAR=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
D=demo/invokedynamic
rm -rf "$D/bS" "$D/bU" "$D"/*.jar; mkdir -p "$D/bS" "$D/bU"

echo "############################################################"
echo "# Task-4: where invokedynamic breaks ObzcureVM - $(date)"
java -version 2>&1 | head -1
echo "############################################################"

echo; echo "### compile both demo classes (--release 17)"
javac --release 17 -d "$D/bS" "$D/SupportedIndy.java"
javac --release 17 -d "$D/bU" "$D/UnsupportedIndy.java"
( cd "$D/bS" && jar cfe "../supported.jar"   SupportedIndy   SupportedIndy*.class )
( cd "$D/bU" && jar cfe "../unsupported.jar" UnsupportedIndy UnsupportedIndy*.class )

echo; echo "============================================================"
echo "CASE A (SUPPORTED): Supplier lambda"
echo "============================================================"
echo "-- the source method compiles to an invokedynamic:"
javap -c -p "$D/bS/SupportedIndy.class" | grep -m1 "invokedynamic"
echo "-- run the virtualizer:"
java -jar "$JAR" -i "$D/supported.jar" -o "$D/supported-vm.jar" -rf -sd -f
echo "-- checkLicense AFTER virtualization (now a call into the interpreter = virtualized):"
mkdir -p "$D/bS/vm" && ( cd "$D/bS/vm" && jar xf "../../supported-vm.jar" )
javap -c -p "$D/bS/vm/SupportedIndy.class" | sed -n '/public static int checkLicense/,/astore_1/p'

echo; echo "============================================================"
echo "CASE B (UNSUPPORTED): Comparator lambda"
echo "============================================================"
echo "-- the source method compiles to an invokedynamic:"
javap -c -p "$D/bU/UnsupportedIndy.class" | grep -m1 "invokedynamic"
echo "-- run the virtualizer (expected: build aborts at the invokedynamic):"
java -jar "$JAR" -i "$D/unsupported.jar" -o "$D/unsupported-vm.jar" -rf -sd -f
echo "   (exit status above is the virtualizer's; the Error line names the method)"

echo; echo "############################################################"
echo "# RESULT: a Supplier lambda virtualizes; a Comparator lambda aborts the"
echo "# build with 'Invokedynamic instruction has unsupported arguments in: ...'."
echo "# That is the invokedynamic boundary of the current implementation."
echo "############################################################"
