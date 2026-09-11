#!/usr/bin/env bash
# =============================================================================
# RQ2 / EE2 - performance microbenchmark (§8.5, axis 1): per-call execution time
# of the VIRTUALIZED checkLicense vs the ORIGINAL, with JIT warmup + multiple JVM
# forks + tail percentiles. The virtualized target runs the REAL release path
# (seed fetched once from the license server over TLS, then cached; every call
# still re-decrypts + re-parses + interprets the VM program = the as-shipped cost).
#
# Reproducible: virtualizes a fresh license-demo-vm with a known per-build seed,
# registers it, then forks the harness. Run from anywhere (cd's to obzcurevm root).
#   bash demo/rq2-bench/run_rq2_bench.sh 2>&1 | tee demo/rq2-bench/EVIDENCE.log
# Env overrides: FORKS, O_WARM/O_BATCH/O_REP (original), V_WARM/V_BATCH/V_REP (virtual).
# =============================================================================
set -u
cd "$(dirname "$0")/../.."                        # -> obzcurevm root
ROOT=$(pwd)
G=demo/rq2-bench
B=$G/build
TOOL=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
SVJAR=license-server/target/license-server-0.1.0.jar
TS=client-truststore.p12
TOKEN="${OBZCURE_REGISTER_SECRET:-obzcure-vendor-secret-change-me}"
LICENSE=BENCH-001
ORIG=license-demo.jar
VM=$G/license-demo-vm-bench.jar

# clean JDK for BOTH the benchmark and the server (no JabRef module pollution here,
# but keep it a normal JDK 18; the Java-17 VM runtime runs fine on it).
JV="${BENCH_JAVA:-C:/Program Files/Java/jdk-18/bin/java.exe}"
JC="${BENCH_JAVAC:-C:/Program Files/Java/jdk-18/bin/javac.exe}"
[ -x "$JV" ] || JV=java
[ -x "$JC" ] || JC=javac
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; *) SEP=':' ;; esac

FORKS="${FORKS:-8}"
# original is ~ns/call: heavy warmup, huge batches. virtualized is ~tens of ms/call
# (dominated by the per-call node-lock fingerprint), so time SINGLE calls with light
# warmup (batch=1), many reps for a distribution; forks give independent JIT/OS state.
O_WARM="${O_WARM:-1000000}"; O_BATCH="${O_BATCH:-5000000}"; O_REP="${O_REP:-15}"
V_WARM="${V_WARM:-30}";      V_BATCH="${V_BATCH:-1}";       V_REP="${V_REP:-60}"

echo "############################################################"
echo "# RQ2 microbenchmark - $(date)"; "$JV" -version 2>&1 | head -1
echo "# forks=$FORKS  original[warm=$O_WARM batch=$O_BATCH x$O_REP]  virtual[warm=$V_WARM batch=$V_BATCH x$V_REP]"
echo "############################################################"

echo; echo "### 1. Compile harness + virtualize a fresh license-demo (known seed)"
rm -rf "$B"; mkdir -p "$B"
"$JC" -d "$B" "$G/Bench.java" "$G/BenchBreakdown.java"
[ -f "$ORIG" ] || { echo "!! $ORIG (original) not found at repo root"; exit 2; }
SEED=$("$JV" -jar "$TOOL" -i "$ORIG" -o "$VM" -rf -sd -f | grep -oE 'OBZCURE_SEED=-?[0-9]+' | cut -d= -f2)
echo "    original=$ORIG  virtualized=$VM  per-build seed=$SEED"
[ -n "$SEED" ] || { echo "!! no seed captured"; exit 3; }

echo; echo "### 2. Start license server + register the seed"
mkdir -p "$ROOT/$G/.srvtmp"
"$JV" -Djava.io.tmpdir="$ROOT/$G/.srvtmp" -jar "$SVJAR" > "$G/lsvr.log" 2>&1 &
SVPID=$!
trap 'kill $SVPID 2>/dev/null' EXIT
for i in $(seq 1 40); do
  curl -sk -o /dev/null "https://localhost:8443/seed?license=probe&fingerprint=probe" 2>/dev/null && break
  sleep 1
done
curl -sk -w ' [register HTTP %{http_code}]\n' -X POST "https://localhost:8443/register?license=$LICENSE&seed=$SEED&token=$TOKEN"

COMMON=(-Djavax.net.ssl.trustStore="$TS" -Djavax.net.ssl.trustStorePassword=changeit -Dobzcure.license="$LICENSE")

echo; echo "### 3. Run $FORKS forks per target (SAMPLE lines = per-call ns)"
SAMPLES="$G/samples.txt"; : > "$SAMPLES"
for f in $(seq 1 "$FORKS"); do
  echo "-- fork $f/$FORKS (original + virtualized)"
  "$JV" -cp "$B" Bench original "$ORIG" "$O_WARM" "$O_BATCH" "$O_REP" >> "$SAMPLES"       # raw samples -> file only
  "$JV" "${COMMON[@]}" -cp "$B" Bench virtualized "$VM" "$V_WARM" "$V_BATCH" "$V_REP" >> "$SAMPLES"
done

kill $SVPID 2>/dev/null; trap - EXIT
echo; echo "### 4. Aggregate (from $SAMPLES, deterministic)"
python "$G/rq2_agg.py" "$SAMPLES"

echo; echo "### 5. Per-call cost breakdown (§8.5 axis 2: where the ~31 ms goes)"
"$JV" -cp "$B" BenchBreakdown "$VM" 25
echo "### done (RQ2)"
