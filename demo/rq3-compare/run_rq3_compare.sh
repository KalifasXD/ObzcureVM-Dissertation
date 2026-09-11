#!/usr/bin/env bash
# =============================================================================
# RQ3 / EE3 - virtualization vs commodity obfuscators (ProGuard, Allatori).
# The sharp axis is STATIC-DECOMPILATION RESISTANCE of the license LOGIC: does the
# magic constant 7919 and the arithmetic (imul/ixor/irem) survive in the decompilable
# bytecode? Commodity obfuscators rename/reorder but preserve the arithmetic; only
# virtualization lifts the logic out of the class into the encrypted VM program.
#
#   bash demo/rq3-compare/run_rq3_compare.sh 2>&1 | tee demo/rq3-compare/EVIDENCE.log
# Env: PROGUARD_JAR, ALLATORI_JAR (defaults point at the user's Downloads).
# =============================================================================
set -u
cd "$(dirname "$0")/../.."
ROOT="$(pwd -W 2>/dev/null || pwd)"   # Windows-style path (C:/...) so ProGuard/Allatori (Windows java) can read it
G=demo/rq3-compare; B=$G/build
TOOL=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
ORIG=license-demo.jar
JV="${BENCH_JAVA:-C:/Program Files/Java/jdk-18/bin/java.exe}"; [ -x "$JV" ] || JV=java
JP="${BENCH_JAVAP:-C:/Program Files/Java/jdk-18/bin/javap.exe}"; [ -x "$JP" ] || JP=javap
JAR="${BENCH_JAR:-C:/Program Files/Java/jdk-18/bin/jar.exe}"; [ -x "$JAR" ] || JAR=jar
JHOME="${JAVA_HOME_18:-C:/Program Files/Java/jdk-18}"
PROGUARD_JAR="${PROGUARD_JAR:-$HOME/Downloads/proguard-7.10.0/proguard-7.10.0/lib/proguard.jar}"
ALLATORI_JAR="${ALLATORI_JAR:-$HOME/Downloads/Allatori-9.9-Demo/lib/allatori.jar}"

echo "############################################################"
echo "# RQ3 virtualization vs ProGuard/Allatori - $(date)"; "$JV" -version 2>&1 | head -1
echo "############################################################"
rm -rf "$B"; mkdir -p "$B"

echo; echo "### 1. ProGuard obfuscate (keep LicenseDemo.checkLicense name; obfuscate body)"
cat > "$B/pg.pro" <<PRO
-injars '$ROOT/$ORIG'
-outjars '$ROOT/$G/license-demo-pg.jar'
-libraryjars '$JHOME/jmods/java.base.jmod'
-keep class LicenseDemo { public static int checkLicense(int); }
-dontwarn
-dontnote
-optimizationpasses 3
-overloadaggressively
PRO
"$JV" -jar "$PROGUARD_JAR" @"$B/pg.pro" > "$G/proguard-run.log" 2>&1 && echo "    -> license-demo-pg.jar" || echo "    !! ProGuard failed (see proguard-run.log)"

echo; echo "### 2. Allatori obfuscate (control-flow maximum + string encryption; keep the method name)"
cat > "$B/allatori.xml" <<XML
<config>
  <input><jar in="$ROOT/$ORIG" out="$ROOT/$G/license-demo-allatori.jar"/></input>
  <keep-names><class access="protected+"><method access="protected+"/></class></keep-names>
  <property name="string-encryption" value="maximum"/>
  <property name="log-file" value="$ROOT/$G/allatori-log.xml"/>
</config>
XML
"$JV" -jar "$ALLATORI_JAR" "$B/allatori.xml" > "$G/allatori-run.log" 2>&1 && echo "    -> license-demo-allatori.jar (Allatori DEMO)" || echo "    !! Allatori failed (see allatori-run.log)"

echo; echo "### 3. Virtualize (static; the seed is irrelevant to a decompilation comparison)"
"$JV" -jar "$TOOL" -i "$ORIG" -o "$G/license-demo-vm-rq3.jar" -rf -sd -f >/dev/null 2>&1 && echo "    -> license-demo-vm-rq3.jar"

echo; echo "### 4. Static analysis: does checkLicense still reveal the logic?"
analyze() {  # $1 label ; $2 jar
  local d="$B/x"; rm -rf "$d"; mkdir -p "$d"
  ( cd "$d" && "$JAR" xf "$ROOT/$2" LicenseDemo.class 2>/dev/null )
  local jp; jp=$("$JP" -c -p -classpath "$d" LicenseDemo 2>/dev/null)
  local body; body=$(echo "$jp" | sed -n '/checkLicense/,/^$/p')
  has() { echo "$body" | grep -qiE "$1" && echo YES || echo "no"; }
  local c7919 ops disp size
  c7919=$(echo "$body" | grep -qE "7919|1eef|1EEF" && echo YES || echo "no")
  ops=$(echo "$body" | grep -qiE "imul|ixor|irem" && echo YES || echo "no")
  disp=$(echo "$body" | grep -qiE "ObzcureVM|virtualize|obzcu/re" && echo YES || echo "no")
  size=$(du -b "$2" 2>/dev/null | cut -f1)
  printf "%-26s const7919=%-4s arithmetic=%-4s vm-dispatch=%-4s  size=%sB\n" "$1" "$c7919" "$ops" "$disp" "${size:-?}"
}
echo "-------------------------------------------------------------------------------------------"
analyze "Original (C0)"            "$ORIG"
analyze "ProGuard"                 "$G/license-demo-pg.jar"
analyze "Allatori"                 "$G/license-demo-allatori.jar"
analyze "Virtualized (C1)"         "$G/license-demo-vm-rq3.jar"
echo "-------------------------------------------------------------------------------------------"
echo "Reading: const7919/arithmetic = the license logic is still in the decompilable bytecode."
echo "vm-dispatch = the body is only a call into the interpreter (logic lifted into cats.meow)."
echo "### done (RQ3)"
