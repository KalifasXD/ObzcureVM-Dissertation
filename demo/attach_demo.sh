#!/usr/bin/env bash
# Two-process dynamic-attach demo. Run from /work:  bash demo/attach_demo.sh
set -u

echo "=== build protected jar + capture seed ==="
SEED=$(java --enable-preview -jar target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar \
        -i license-demo.jar -o license-demo-vm.jar -rf -sd -f | grep -oP 'OBZCURE_SEED=\K-?[0-9]+')
echo "seed=$SEED"

javac --release 17 --enable-preview --add-modules jdk.attach -cp license-demo-vm.jar \
      -d attacker-classes demo/LoopDemo.java demo/AttachTool.java

echo "=== start target (CLEAN launch, no debugger in its args) ==="
java --enable-preview -Dobzcure.seed=$SEED -cp "attacker-classes:license-demo-vm.jar" LoopDemo &
TARGET=$!
sleep 4

echo "=== attacker attaches JDWP to running target PID $TARGET ==="
java --enable-preview --add-modules jdk.attach -cp attacker-classes AttachTool "$TARGET"
sleep 4

echo "=== stopping target ==="
kill "$TARGET" 2>/dev/null
