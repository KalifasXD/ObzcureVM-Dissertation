#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
LOG=EVIDENCE.log; : > $LOG; exec >>$LOG 2>&1
rm -rf gate_patched bound_patched; mkdir -p gate_patched bound_patched
echo "### compile (JDK-only) ###"
javac GateDemo.java Vault.java VaultGen.java BoundDemo.java ClassPatch.java && echo "compiled OK"
echo
echo "########## A) BOOLEAN GATE = single point of failure ##########"
echo -n "  normal  valid  4321: "; java GateDemo 4321
echo -n "  normal  pirate 9999: "; java GateDemo 9999
echo "  attacker flips ONE byte in isLicensed:  if_icmpne(a0) -> if_icmpeq(9f)"
java ClassPatch GateDemo.class gate_patched/GateDemo.class 1a1110e1a0 1a1110e19f
echo -n "  PATCHED pirate 9999: "; java -cp gate_patched GateDemo 9999
echo "  => GATE DEFEATED by a 1-byte edit; attacker never touched the license logic."
echo
echo "########## B) RESULT-BINDING = the feature IS the value ##########"
java VaultGen
echo -n "  normal  valid  4321: "; java BoundDemo 4321
echo -n "  normal  pirate 9999: "; java BoundDemo 9999
echo "  attacker tries the SAME cheap trick: 1-byte patch of the computation 7919(111eef)->7918(111eee)"
cp feature.enc bound_patched/; cp BoundDemo.class bound_patched/
java ClassPatch Vault.class bound_patched/Vault.class 111eef 111eee
echo -n "  PATCHED legit  4321: "; (cd bound_patched && java BoundDemo 4321)
echo -n "  PATCHED pirate 9999: "; (cd bound_patched && java BoundDemo 9999)
echo "  => RESULT-BOUND: a 1-byte patch only BREAKS it (LOCKED), never unlocks. To unlock an invalid"
echo "     serial the attacker must reproduce licenseValue==136659 = defeat the real computation."
echo "########## DONE ##########"
