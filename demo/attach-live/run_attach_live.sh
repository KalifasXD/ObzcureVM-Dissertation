#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
LOG=EVIDENCE.log
: > $LOG
exec >>$LOG 2>&1

echo "### 0) compile target + attackers + agent (JDK 18) ###"
javac --add-modules jdk.attach AntiDebugTarget.java AttachTool.java AttachAgentTool.java InjectedAgent.java && jar cfm agent.jar manifest.txt InjectedAgent.class && echo "    compiled OK" || { echo "    COMPILE FAILED"; exit 1; }
echo

echo "########## LIVE DYNAMIC-ATTACH DEMO v2 (native Windows, JDK 18) ##########"
java -version 2>&1 | head -1
echo

echo "### 1) launch target CLEAN (no debug/agent flags in its launch line) ###"
java --add-modules jdk.attach -cp . AntiDebugTarget > target.log 2>&1 &
sleep 4
PID=$(grep -oE 'TARGET_PID=[0-9]+' target.log | head -1 | cut -d= -f2)
echo "captured TARGET_PID=$PID"
echo "--- PRE-ATTACK target output (v1 clean, v2 clean, gate ALLOWED): ---"
cat target.log
echo

echo "### 2) attacker try A: attach the JDWP DEBUGGER post-launch (jdwp.dll) ###"
echo "    (jdwp.dll exports only Agent_OnLoad/OnUnload -> no Agent_OnAttach -> late-attach impossible)"
java --add-modules jdk.attach -cp . AttachTool "$PID" 2>&1 | sed 's/^/    jdwp> /'
echo "    => the pure-JDWP debugger CANNOT be dynamically attached; v1 already blocks it at launch."
echo

echo "### 3) attacker try B: attach a JAVA INSTRUMENTATION AGENT post-launch (instrument.dll) ###"
echo "    (instrument.dll exports Agent_OnAttach -> this is the REAL residual dynamic-attach vector)"
java --add-modules jdk.attach -cp . AttachAgentTool "$PID" "$PWD/agent.jar" 2>&1 | sed 's/^/    agent> /'
echo "    attacker_exit=${PIPESTATUS[0]}"
sleep 4
echo

echo "### 4) target output ACROSS the instrumentation attach ###"
echo "    (watch: the INJECTED AGENT banner appears INSIDE the target, yet v1 & v2 stay clean, gate stays ALLOWED)"
cat target.log
echo

echo "### 5) would-be v2 verdict summary (pure-Java runtime detection) ###"
if grep -q 'v2=DEBUGGER' target.log; then
  echo "    v2 (thread-scan) DID detect something."
else
  echo "    v2 (thread-scan) saw NOTHING across the whole run -> pure-Java runtime detection is BLIND."
  echo "    => closing this gap needs native / JVMTI code (PhD future work)."
fi
echo

echo "### stop target ###"
taskkill //PID "$PID" //F 2>&1 | head -1
echo "########## DONE ##########"
