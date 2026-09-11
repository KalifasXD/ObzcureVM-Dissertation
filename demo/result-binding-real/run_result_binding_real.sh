#!/usr/bin/env bash
# F4 (integrate result-binding on the REAL consuming edge). The audit found result-binding was absent
# from the real path (main just printed checkLicense; nothing consumed it) and the standalone demo keyed
# off Vault.licenseValue, a plaintext re-impl that never used the seed. Here the premium feature is sealed
# under the value of the ACTUAL checkLicense, and the app derives its key from the VIRTUALIZED method -
# so there is no boolean gate to patch, and obtaining the feature requires the genuine protected computation.
#
# PRE-REQ: build with BlobCrypto.DEV_MODE = true, then `mvn clean package -DskipTests` (the dev build only
# supplies the seed offline via -Dobzcure.seed, standing in for a licensed user's server-delivered seed).
# Run:  bash demo/result-binding-real/run_result_binding_real.sh
set -u
cd "$(dirname "$0")/../.."                 # -> obzcurevm repo root (/work)
DEMO=demo/result-binding-real
JAR=target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
LOG=$DEMO/EVIDENCE.log; : > "$LOG"; exec >>"$LOG" 2>&1

echo "### 0. Build the F4 demo classes + virtualize checkLicense (dev build), capture the seed"
javac -d "$DEMO" "$DEMO/Feature.java" "$DEMO/FeatureGen.java" "$DEMO/PremiumApp.java"
SEED=$(java -jar "$JAR" -i license-demo.jar -o license-demo-vm.jar -rf -sd -f | grep -oP 'OBZCURE_SEED=\K-?[0-9]+')
echo "    seed=$SEED"
echo

echo "### 1. VENDOR seals the feature under the REAL checkLicense value (from the ORIGINAL jar, not a re-impl)"
java -cp "$DEMO" FeatureGen
echo

echo "### 2. LICENSED user, valid serial 4321: the key is derived from the VIRTUALIZED checkLicense"
java -Dobzcure.seed="$SEED" -cp "$DEMO" PremiumApp 4321
echo

echo "### 3. PIRATE, serial 9999: checkLicense returns a different value -> the feature stays SEALED"
java -Dobzcure.seed="$SEED" -cp "$DEMO" PremiumApp 9999
echo

echo "### 4. Why a cheap patch cannot help (result-binding): there is NO boolean gate in PremiumApp - the"
echo "       feature IS the decrypted payload. To unlock 9999 the attacker must make checkLicense(9999)"
echo "       return checkLicense(4321), i.e. reproduce the output of the virtualized + diversified +"
echo "       AES-GCM-encrypted + off-machine-seeded + fingerprint-node-locked method. Patching the VM blob"
echo "       only corrupts the decode; it never yields the licensed value."
echo "########## DONE:  result-binding now sits on the REAL virtualized method (F4) ##########"
