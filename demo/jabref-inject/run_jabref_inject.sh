#!/usr/bin/env bash
# =============================================================================
# JabRef injection milestone - end-to-end driver (DESIGN.md sections 4, 8).
#
# Reproduces the whole pipeline on the real application JabRef-4.3.1.jar and
# verifies protection with the release deployment path (server-first seed
# delivery over TLS, hardware node-lock, result-binding). No offline seed
# shortcut: BlobCrypto.DEV_MODE=false in the shipped runtime, so the guard
# fetches its per-build seed from the license server exactly as a deployed
# product would.
#
#   inject (ASM) -> seal -> virtualize -> repack -> structural verify ->
#   protection verify (valid / pirate / no-license)
#
# Run from the obzcurevm root (the script cd's there itself). Tee to capture:
#   bash demo/jabref-inject/run_jabref_inject.sh 2>&1 | tee demo/jabref-inject/EVIDENCE.log
#
# Requires: JDK 17+ (host JDK 18 is fine), the prebuilt ObzcureVM tool jar, the
# built license-server jar, curl, and client-truststore.p12 at the repo root.
# =============================================================================
set -u
cd "$(dirname "$0")/../.."                       # -> obzcurevm root
ROOT=$(pwd)
D=demo/jabref-inject
TOOL=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
SVJAR=license-server/target/license-server-0.1.0.jar
TOKEN="${OBZCURE_REGISTER_SECRET:-obzcure-vendor-secret-change-me}"            # F5 vendor token (PoC value)
LICENSE=JABREF-001                               # the license key the guard presents
TS=client-truststore.p12                         # pinned trust store (F6), holds only the server cert
INJAR=jabref/JabRef-4.3.1.jar
# Classpath separator: ';' on Windows shells (Git Bash / MSYS / Cygwin), ':' on Linux / WSL / macOS.
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; *) SEP=':' ;; esac
CP="$D/jabref-protected.jar${SEP}$D/harness"     # protected app + the step-7 harness

echo "############################################################"
echo "# JabRef injection milestone - $(date)"
java -version 2>&1 | head -1
echo "############################################################"

echo; echo "### 0. Compile the guard components"
rm -rf "$D/build" "$D/harness"; mkdir -p "$D/build" "$D/harness"
javac --release 17 -d "$D/build" "$D/LicenseGuard.java"
javac --release 17 -cp "$D/build" -d "$D/build" "$D/LicenseEnforcer.java"
javac -cp "$TOOL" -d "$D/build" "$D/Injector.java"
javac -cp "$D/build" -d "$D/build" "$D/GuardSealer.java"
javac -d "$D/build" "$D/Repack.java"
javac -cp "$D/build" -d "$D/harness" "$D/GuardHarness.java"
echo "    compiled: $(ls "$D/build" | tr '\n' ' ')"

echo; echo "### 1. INJECT (source-agnostic ASM) into $INJAR"
java -cp "$TOOL${SEP}$D/build" Injector "$INJAR" "$D/jabref-injected.jar" "$D/build"

echo; echo "### 2. SEAL a marker payload under checkLicense(VALID) from the ORIGINAL guard"
java -cp "$D/build" GuardSealer "$D/guard.enc"

echo; echo "### 3. VIRTUALIZE the guard (per-build seed printed by the tool)"
( cd "$D/build" && jar cf ../guard.jar LicenseGuard*.class )
SEED=$(java -jar "$TOOL" -i "$D/guard.jar" -o "$D/guard-vm.jar" -rf -sd -f | grep -oE 'OBZCURE_SEED=-?[0-9]+' | cut -d= -f2)
echo "    per-build seed = $SEED"

echo; echo "### 4. REPACK -> jabref-protected.jar"
java -cp "$D/build" Repack "$D/jabref-injected.jar" "$D/guard-vm.jar" "$D/guard.enc" "$D/jabref-protected.jar"

echo; echo "### 5. STRUCTURAL verify (injection point + virtualized guard + manifest)"
TMP="$ROOT/$D/.verify"; rm -rf "$TMP"; mkdir -p "$TMP"   # stable dir (mktemp -d is unreliable under Git Bash)
( cd "$TMP" && jar xf "$ROOT/$D/jabref-protected.jar" org/jabref/JabRefMain.class META-INF/MANIFEST.MF )
echo "-- JabRefMain.main (enforce() must be at offset 0):"
javap -p -c -classpath "$TMP" org.jabref.JabRefMain | sed -n '/public static void main/,/aload_0/p'
echo "-- Main-Class preserved:"; grep -i "Main-Class" "$TMP/META-INF/MANIFEST.MF"
echo "-- LicenseGuard.checkLicense inside the protected jar (must be a VM dispatch shell):"
javap -p -c -classpath "$D/jabref-protected.jar" LicenseGuard | grep -E "long checkLicense|ObzcureVM.virtualize|execute" | head -3
rm -rf "$TMP"

echo; echo "### 6. PROTECTION verify (server-first + TLS + node-lock + result-binding)"
mkdir -p "$ROOT/$D/.srvtmp"                       # stable java.io.tmpdir so embedded Tomcat can create its work dir
java -Djava.io.tmpdir="$ROOT/$D/.srvtmp" -jar "$SVJAR" > "$D/lsvr.log" 2>&1 &
SVPID=$!
trap 'kill $SVPID 2>/dev/null' EXIT
for i in $(seq 1 40); do
  curl -sk -o /dev/null "https://localhost:8443/seed?license=probe&fingerprint=probe" 2>/dev/null && break
  sleep 1
done
echo "-- vendor registers the per-build seed for $LICENSE:"
curl -sk -w ' [HTTP %{http_code}]\n' -X POST "https://localhost:8443/register?license=$LICENSE&seed=$SEED&token=$TOKEN"
echo "-- (a) VALID serial 4321  -> expect UNLOCKED:"
java -cp "$CP" -Dobzcure.license="$LICENSE" -Djavax.net.ssl.trustStore="$TS" -Djavax.net.ssl.trustStorePassword=changeit -Dlicense.serial=4321 GuardHarness
echo "-- (b) PIRATE serial 9999 -> expect LOCKED (guard runs, wrong value):"
java -cp "$CP" -Dobzcure.license="$LICENSE" -Djavax.net.ssl.trustStore="$TS" -Djavax.net.ssl.trustStorePassword=changeit -Dlicense.serial=9999 GuardHarness
echo "-- (c) NO LICENSE         -> expect LOCKED (no seed, guard inert):"
java -cp "$CP" -Djavax.net.ssl.trustStore="$TS" -Djavax.net.ssl.trustStorePassword=changeit -Dlicense.serial=4321 GuardHarness
kill $SVPID 2>/dev/null

echo; echo "############################################################"
echo "# DONE: protection demonstrated on the real application jar."
echo "############################################################"
