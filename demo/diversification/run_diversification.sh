#!/usr/bin/env bash
# =============================================================================
# RQ1 - diversification-effectiveness experiment (quantitative Kerckhoffs proof).
# Builds N variants of the SAME method (each a fresh per-build seed -> a different
# opcode permutation -> a different cats.meow), confirms the N blobs are pairwise
# distinct, then computes the CROSS-build decode-success rate from the real seeds
# (DivExp replicates the toolchain's exact Fisher-Yates permutation).
#
#   bash demo/diversification/run_diversification.sh 2>&1 | tee demo/diversification/EVIDENCE.log
# Env: N (default 20).
# =============================================================================
set -u
cd "$(dirname "$0")/../.."
ROOT=$(pwd)
G=demo/diversification
B=$G/build
TOOL=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
ORIG=license-demo.jar
N="${N:-20}"
JV="${BENCH_JAVA:-C:/Program Files/Java/jdk-18/bin/java.exe}"; [ -x "$JV" ] || JV=java
JC="${BENCH_JAVAC:-C:/Program Files/Java/jdk-18/bin/javac.exe}"; [ -x "$JC" ] || JC=javac
JAR="${BENCH_JAR:-C:/Program Files/Java/jdk-18/bin/jar.exe}"; [ -x "$JAR" ] || JAR=jar

# distinct opcode values used by the demo checkLicense (from javap of the original):
# sipush17 bipush16 iload_0=26 iload_1=27 iload_2=28 istore_1=60 istore_2=61 iadd96
# imul104 irem112 ineg116 ixor130 ifge156 ldc18 ireturn172
PROG_OPCODES="17,16,26,27,28,60,61,96,104,112,116,130,156,18,172"

echo "############################################################"
echo "# RQ1 diversification-effectiveness - $(date)  (N=$N builds)"
"$JV" -version 2>&1 | head -1
echo "############################################################"

echo; echo "### 1. Compile DivExp"
rm -rf "$B"; mkdir -p "$B" "$G/blobs"
"$JC" -d "$B" "$G/DivExp.java"

echo; echo "### 2. Build $N variants of the same method; capture seed + blob hash each"
SEEDS=""; : > "$G/seeds.txt"; : > "$G/blob_hashes.txt"
for i in $(seq 1 "$N"); do
  out="$G/blobs/v$i.jar"
  seed=$("$JV" -jar "$TOOL" -i "$ORIG" -o "$out" -rf -sd -f | grep -oE 'OBZCURE_SEED=-?[0-9]+' | cut -d= -f2)
  ( cd "$B" && rm -rf ex; mkdir ex; cd ex && "$JAR" xf "$ROOT/$out" obzcure/cats.meow )
  h=$(sha256sum "$B/ex/obzcure/cats.meow" | cut -d' ' -f1)
  echo "$seed" >> "$G/seeds.txt"; echo "$h" >> "$G/blob_hashes.txt"
  SEEDS="${SEEDS:+$SEEDS,}$seed"
  printf "  build %2d  seed=%-22s blob sha256=%s\n" "$i" "$seed" "${h:0:16}..."
done
rm -rf "$G/blobs"   # the jars are throwaway; seeds.txt + blob_hashes.txt are the record

echo; echo "### 3. Distinctness of the $N blobs + seeds"
ublobs=$(sort -u "$G/blob_hashes.txt" | wc -l | tr -d ' ')
useeds=$(sort -u "$G/seeds.txt" | wc -l | tr -d ' ')
echo "    distinct cats.meow blobs: $ublobs / $N     distinct seeds: $useeds / $N"

echo; echo "### 4. Cross-build decode-success (from the real seeds)"
"$JV" -cp "$B" DivExp "$SEEDS" "$PROG_OPCODES"
echo "### done (RQ1 diversification)"
