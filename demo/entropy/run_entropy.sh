#!/usr/bin/env bash
# =============================================================================
# Blob-entropy experiment (F8 / V3): Shannon entropy + chi-square on the ENCRYPTED
# blob vs its DECRYPTED plaintext, over a corpus of N per-build blobs. Rigorous
# replacement for the "56/256 vs 239/256 distinct bytes" smell test.
#   bash demo/entropy/run_entropy.sh 2>&1 | tee demo/entropy/EVIDENCE.log   (env: N, default 20)
# =============================================================================
set -u
cd "$(dirname "$0")/../.."
ROOT=$(pwd)
G=demo/entropy; B=$G/build
TOOL=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
ORIG=license-demo.jar
N="${N:-20}"
JV="${BENCH_JAVA:-C:/Program Files/Java/jdk-18/bin/java.exe}"; [ -x "$JV" ] || JV=java
JC="${BENCH_JAVAC:-C:/Program Files/Java/jdk-18/bin/javac.exe}"; [ -x "$JC" ] || JC=javac
JAR="${BENCH_JAR:-C:/Program Files/Java/jdk-18/bin/jar.exe}"; [ -x "$JAR" ] || JAR=jar
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; *) SEP=':' ;; esac

echo "############################################################"
echo "# Blob entropy (F8/V3) - $(date)  (N=$N builds)"; "$JV" -version 2>&1 | head -1
echo "############################################################"

echo; echo "### 1. Compile EntropyExp (against the tool jar, for BlobCrypto)"
rm -rf "$B" "$G/cm"; mkdir -p "$B" "$G/cm"
"$JC" -cp "$TOOL" -d "$B" "$G/EntropyExp.java"

echo; echo "### 2. Build $N variants; extract each cats.meow + seed"
: > "$G/pairs.txt"
for i in $(seq 1 "$N"); do
  out="$B/v$i.jar"
  seed=$("$JV" -jar "$TOOL" -i "$ORIG" -o "$out" -rf -sd -f | grep -oE 'OBZCURE_SEED=-?[0-9]+' | cut -d= -f2)
  ( cd "$B" && rm -rf ex; mkdir ex; cd ex && "$JAR" xf "$ROOT/$out" obzcure/cats.meow )
  cp "$B/ex/obzcure/cats.meow" "$G/cm/v$i.meow"
  printf '%s\t%s\n' "$G/cm/v$i.meow" "$seed" >> "$G/pairs.txt"   # relative path (java cwd = repo root)
  rm -f "$out"
done
echo "    collected $(wc -l < "$G/pairs.txt") (cats.meow, seed) pairs"

echo; echo "### 3. Entropy + chi-square (plaintext vs encrypted)"
"$JV" -cp "$TOOL${SEP}$B" EntropyExp "$G/pairs.txt"
rm -rf "$G/cm"
echo "### done (blob entropy)"
