#!/usr/bin/env bash
# =============================================================================
# JabRef 5.x GUI protection demo - source-agnostic injection into a real,
# modular JavaFX application, with the FULL protection chain active
# (virtualized guard + per-build diversification + off-machine seed over TLS +
# hardware node-lock + result-binding), all on JabRef's own bundled JDK 18.
#
# JabRef 5.7 is a jpackage app-image: org.jabref is linked into runtime/lib/modules.
# We therefore inject via --patch-module (one patched JabRefLauncher.class) and keep
# the guard/enforcer + VM runtime on the classpath (unnamed module), reachable from
# the patched launcher via --add-reads org.jabref=ALL-UNNAMED. This mirrors the proven
# headless pipeline (demo/jabref-inject/run_jabref_inject.sh); only the final step
# changes from a harness to launching the real GUI.
#
# Usage (run from anywhere; the script cd's to the obzcurevm root):
#   bash demo/jabref-inject/gui/run_jabref_gui.sh valid        # -> JabRef window OPENS
#   bash demo/jabref-inject/gui/run_jabref_gui.sh pirate       # -> LOCKED, no window (wrong serial)
#   bash demo/jabref-inject/gui/run_jabref_gui.sh nolicense    # -> LOCKED, no window (no seed)
#   bash demo/jabref-inject/gui/run_jabref_gui.sh probe-valid  # -> headless gate check (no GUI)
#   bash demo/jabref-inject/gui/run_jabref_gui.sh probe-pirate # -> headless gate check (no GUI)
#   bash demo/jabref-inject/gui/run_jabref_gui.sh build        # -> build only
#
# JABREF_HOME may override the portable folder location.
# =============================================================================
set -u
MODE="${1:-valid}"
cd "$(dirname "$0")/../../.."                     # -> obzcurevm root
ROOT=$(pwd)
G=demo/jabref-inject/gui
B=$G/build
TOOL=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
SVJAR=license-server/target/license-server-0.1.0.jar
TS=client-truststore.p12
TOKEN="${OBZCURE_REGISTER_SECRET:-obzcure-vendor-secret-change-me}"
LICENSE=JABREF-001

JABREF_HOME="${JABREF_HOME:-$HOME/Downloads/JabRef-5.7-portable_windows/JabRef}"
JAVA="$JABREF_HOME/runtime/bin/java.exe"
JAVAC="$JABREF_HOME/runtime/bin/javac.exe"
JAR="$JABREF_HOME/runtime/bin/jar.exe"
KEYTOOL="$JABREF_HOME/runtime/bin/keytool.exe"
if [ ! -x "$JAVA" ]; then
  echo "!! JabRef bundled java not found at: $JAVA"
  case "$(uname -s)" in
    Linux*) echo "   You appear to be inside the Docker/Linux container. This GUI demo must run on"
            echo "   NATIVE WINDOWS (Git Bash) - it opens a real JabRef window on the Windows desktop,"
            echo "   and uses JabRef's Windows bundled runtime. Open Git Bash (prompt like"
            echo "   'you@PC MINGW64'), cd to this repo, and re-run. (Docker was only for the headless"
            echo "   screenshot-28 pipeline.)" ;;
    *)      echo "   Set JABREF_HOME to your unzipped JabRef-5.7 portable folder and re-run." ;;
  esac
  exit 2
fi

# The license server (Spring Boot + Logback) must run on a CLEAN JDK. JabRef's bundled
# runtime links org.tinylog.slf4j as a system module, which hijacks SLF4J and crashes the
# server. Use the host JDK 18 (falls back to a `java` on PATH). JabRef itself still runs on
# its own bundled runtime.
SVJAVA="${SVJAVA:-C:/Program Files/Java/jdk-18/bin/java.exe}"
[ -x "$SVJAVA" ] || SVJAVA="java"

# The client sets -Djavax.net.ssl.trustStore JVM-wide, so it governs JabRef's OWN https too
# (e.g. its startup update check). A pinned-only store makes that fail with a PKIX error. The
# realistic deployment is to ADD the vendor cert to the platform store, not replace it: we build
# a merged store = the runtime's cacerts (platform CAs) + our license-server cert. The license
# channel is unchanged (client still validates the self-signed server cert against this store;
# no public CA can mint a cert for it), and JabRef's other TLS keeps working.
CACERTS="$JABREF_HOME/runtime/lib/security/cacerts"
TS_MERGED="$G/merged-truststore.p12"

# Windows bundled java.exe wants ';' as the classpath separator (works from Git Bash).
SEP=';'

echo "############################################################"
echo "# JabRef 5.x GUI protection demo - mode=$MODE - $(date)"
"$JAVA" -version 2>&1 | head -1
echo "# JABREF_HOME=$JABREF_HOME"
echo "############################################################"

# ---------------------------------------------------------------------------
echo; echo "### 1. Compile components (guard/enforcer/sealer/probe --release 17; tools default)"
rm -rf "$B"; mkdir -p "$B"
"$JAVAC" --release 17 -d "$B" "$G/dissertation/LicenseGuard.java" "$G/dissertation/LicenseEnforcer.java" \
        "$G/dissertation/GuardSealer.java" "$G/dissertation/Probe.java"
"$JAVAC" -cp "$TOOL" -d "$B" "$G/ModularInjector.java"
"$JAVAC" -d "$B" "$G/Dump.java"
echo "    compiled: $(ls "$B" | tr '\n' ' ')"

echo; echo "### 1b. Build merged truststore (platform CAs + pinned license-server cert)"
cp "$CACERTS" "$TS_MERGED"
"$KEYTOOL" -importkeystore -srckeystore "$TS" -srcstorepass changeit \
           -destkeystore "$TS_MERGED" -deststorepass changeit -noprompt 2>&1 | tail -1

echo; echo "### 2. SEAL guard.enc under checkLicense(4321) from the ORIGINAL packaged guard"
"$JAVA" -cp "$B" dissertation.GuardSealer "$G/guard.enc"

echo; echo "### 3. VIRTUALIZE the packaged guard (per-build seed printed by the tool)"
( cd "$B" && "$JAR" cf "$ROOT/$G/guard.jar" dissertation/LicenseGuard*.class )
SEED=$("$JAVA" -jar "$TOOL" -i "$G/guard.jar" -o "$G/guard-vm.jar" -rf -sd -f | grep -oE 'OBZCURE_SEED=-?[0-9]+' | cut -d= -f2)
echo "    per-build seed = $SEED"
[ -n "$SEED" ] || { echo "!! no seed captured from virtualizer"; exit 3; }

echo; echo "### 4. ASSEMBLE guard-cp.jar (virtualized guard + VM runtime + cats.meow + enforcer + guard.enc)"
rm -rf "$B/cp"; mkdir -p "$B/cp"
( cd "$B/cp" && "$JAR" xf "$ROOT/$G/guard-vm.jar" )          # dissertation/LicenseGuard(virtualized) + obzcu/re/** + obzcure/cats.meow
cp "$B/dissertation/LicenseEnforcer.class" "$B/cp/dissertation/"
cp "$B/dissertation/Probe.class" "$B/cp/dissertation/"        # headless gate-check harness
cp "$G/guard.enc" "$B/cp/guard.enc"
( cd "$B/cp" && "$JAR" cf "$ROOT/$G/guard-cp.jar" . )
echo "    guard-cp.jar entries: $("$JAR" tf "$G/guard-cp.jar" | wc -l)"

echo; echo "### 5. INJECT enforce() into org.jabref.gui.JabRefLauncher (modular patch)"
"$JAVA" -cp "$B" Dump org.jabref/org/jabref/gui/JabRefLauncher.class "$B/jr/org/jabref/gui/JabRefLauncher.class"
"$JAVA" -cp "$TOOL${SEP}$B" ModularInjector "$B/jr/org/jabref/gui/JabRefLauncher.class" \
        "$G/patched-launcher.jar" org/jabref/gui/JabRefLauncher main
echo "-- structural check (enforce() must be the FIRST instruction of main):"
rm -rf "$B/patchck"; mkdir -p "$B/patchck"
( cd "$B/patchck" && "$JAR" xf "$ROOT/$G/patched-launcher.jar" )   # javap the FILE directly (avoids module-precedence over -classpath)
"$JABREF_HOME/runtime/bin/javap.exe" -p -c "$B/patchck/org/jabref/gui/JabRefLauncher.class" \
        | sed -n '/public static void main/,/return/p'

if [ "$MODE" = "build" ]; then echo; echo "### build-only done"; exit 0; fi

# ---------------------------------------------------------------------------
echo; echo "### 6. Start license server + register the per-build seed"
mkdir -p "$ROOT/$G/.srvtmp"
"$SVJAVA" -Djava.io.tmpdir="$ROOT/$G/.srvtmp" -jar "$SVJAR" > "$G/lsvr.log" 2>&1 &
SVPID=$!
trap 'kill $SVPID 2>/dev/null' EXIT
for i in $(seq 1 40); do
  curl -sk -o /dev/null "https://localhost:8443/seed?license=probe&fingerprint=probe" 2>/dev/null && break
  sleep 1
done
curl -sk -w ' [register HTTP %{http_code}]\n' -X POST "https://localhost:8443/register?license=$LICENSE&seed=$SEED&token=$TOKEN"

# common client flags (release deployment path: license + merged truststore over TLS)
COMMON=(-Djavax.net.ssl.trustStore="$TS_MERGED" -Djavax.net.ssl.trustStorePassword=changeit)

run_probe() {  # $1 = serial ; $2 = withLicense(yes/no)
  local extra=(); [ "$2" = yes ] && extra=(-Dobzcure.license="$LICENSE")
  "$JAVA" -cp "$G/guard-cp.jar" "${extra[@]}" "${COMMON[@]}" -Dlicense.serial="$1" dissertation.Probe
}

launch_jabref() {  # $1 = serial ; $2 = withLicense(yes/no)
  local extra=(); [ "$2" = yes ] && extra=(-Dobzcure.license="$LICENSE")
  "$JAVA" \
    --patch-module org.jabref="$G/patched-launcher.jar" \
    --add-reads org.jabref=ALL-UNNAMED \
    --add-modules java.net.http \
    -cp "$G/guard-cp.jar" \
    "${extra[@]}" "${COMMON[@]}" -Dlicense.serial="$1" \
    -m org.jabref/org.jabref.gui.JabRefLauncher
}

echo; echo "### 7. RUN (mode=$MODE)"
case "$MODE" in
  probe-valid)    run_probe 4321 yes ;;
  probe-pirate)   run_probe 9999 yes ;;
  probe-nolicense) run_probe 4321 no ;;
  valid)     echo "-- launching JabRef GUI with a VALID license (serial 4321). The window should OPEN."; launch_jabref 4321 yes ;;
  pirate)    echo "-- launching JabRef with a PIRATE serial 9999. Expect LOCKED, no window, exit 1."; launch_jabref 9999 yes; echo "   (exit=$?)";;
  nolicense) echo "-- launching JabRef with NO license. Expect LOCKED, no window, exit 1."; launch_jabref 4321 no; echo "   (exit=$?)";;
  *) echo "unknown mode: $MODE"; exit 2 ;;
esac

kill $SVPID 2>/dev/null
echo; echo "### done (mode=$MODE)"
