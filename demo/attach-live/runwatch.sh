#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
LOG=threadwatch.log
: > $LOG
exec >>$LOG 2>&1
echo "### launch clean target (ThreadWatch) ###"
java -cp . ThreadWatch > tw_target.log 2>&1 &
sleep 4
PID=$(grep -oE 'TARGET_PID=[0-9]+' tw_target.log | head -1 | cut -d= -f2)
echo "PID=$PID"
echo "--- BEFORE attach ---"; cat tw_target.log
echo
echo "### attach instrumentation agent post-launch ###"
java --add-modules jdk.attach -cp . AttachAgentTool "$PID" "$PWD/agent.jar" 2>&1
sleep 4
echo
echo "--- AFTER attach (look for attachListenerThread flipping to true) ---"; cat tw_target.log
echo
taskkill //PID "$PID" //F 2>&1 | head -1
echo "### DONE ###"
