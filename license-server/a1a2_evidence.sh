#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# A1/A2 license-server evidence (dissertation Screenshot 27).
# Run this in a terminal and screenshot the window: it starts the server,
# exercises /register and /seed, and shows that EVERY refusal returns the same
# opaque body to the client (A1, no oracle) + that a missing fingerprint is
# refused (A2), while the REAL reason appears only in the server log.
#
# Requires: the built server jar (target/license-server-0.1.0.jar), java, curl.
# Works on the host (Git Bash / WSL, JDK 17+) or inside the Docker dev container.
# ---------------------------------------------------------------------------
cd "$(dirname "$0")"
JAR="target/license-server-0.1.0.jar"
TOKEN="${OBZCURE_REGISTER_SECRET:-obzcure-vendor-secret-change-me}"
LOG="$(mktemp)"

[ -f "$JAR" ] || { echo "Build the server first: mvn -DskipTests package"; exit 1; }

java -jar "$JAR" > "$LOG" 2>&1 &
SVPID=$!
trap 'kill $SVPID 2>/dev/null' EXIT
for i in $(seq 1 40); do
  curl -sk -o /dev/null "https://localhost:8443/seed?license=probe&fingerprint=probe" 2>/dev/null && break
  sleep 1
done

B="https://localhost:8443"
echo "==================== CLIENT VIEW  (every refusal = the SAME body) ===================="
printf '%-46s -> ' "register ABC-123 (vendor token)"; curl -sk -w ' [HTTP %{http_code}]\n' -X POST "$B/register?license=ABC-123&seed=123456789&token=$TOKEN"
printf '%-46s -> ' "seed  ABC-123  fingerprint=MACHINE-A";  curl -sk -w ' [HTTP %{http_code}]\n' "$B/seed?license=ABC-123&fingerprint=MACHINE-A"
printf '%-46s -> ' "seed  ABC-123  fingerprint=MACHINE-B";  curl -sk -w ' [HTTP %{http_code}]\n' "$B/seed?license=ABC-123&fingerprint=MACHINE-B"
printf '%-46s -> ' "seed  UNKNOWN-999";                     curl -sk -w ' [HTTP %{http_code}]\n' "$B/seed?license=UNKNOWN-999&fingerprint=MACHINE-A"
printf '%-46s -> ' "seed  ABC-123  (no fingerprint)";       curl -sk -w ' [HTTP %{http_code}]\n' "$B/seed?license=ABC-123"
echo
echo "==================== SERVER LOG  (the real reason, visible ONLY here) ================="
grep -E 'seed (refused|delivered)' "$LOG" | grep -v probe | sed -E 's/^.*(INFO|WARN).*SeedController[^:]*: /\1  /'
echo "======================================================================================="
