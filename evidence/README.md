# Evidence

Reproducible evaluation evidence for the MSc dissertation *"Development and Evaluation of a JVM-Level
Software Protection System via Bytecode Virtualization"* (University of Piraeus, MSc in Cybersecurity and
Data Science).

This directory holds the **raw material behind every figure and screenshot in the thesis**: the actual
`EVIDENCE.log` files the experiments produced, the statistical outputs, the attack transcripts, and the
screenshots themselves. Where an experiment writes a log or data file, that file is included here so a
reader can open the real artifact instead of only a screenshot. Where a result is a terminal state with
no persisted file, the screenshot is included together with the exact command that reproduces it.

## Code

This `evidence/` directory lives inside the dissertation repository, alongside the implementation it
documents. The build instructions and the per-experiment demo drivers are under [`../demo/`](../demo/);
see the top-level [`../README.md`](../README.md) and the detailed
[`../README-DISSERTATION.md`](../README-DISSERTATION.md).

The implementation builds on the original ObzcureVM by HoverCatz (GPL-3.0), attributed in the top-level
README. This directory holds the evaluation artifacts (logs, data, screenshots, notes).

## Threat model (one line)

A **Level-2** attacker (skilled, standard off-the-shelf tools: decompiler, debugger, instrumentation
agent, hex/bytecode patcher) is the primary target. The interpreter is public (ObzcureVM is on GitHub),
so protection cannot rest on VM secrecy; the only secret is the per-build seed, delivered off-machine
(a Kerckhoffs framing). Full model, configurations C0-C7 and the attack matrix are in
[`EVALUATION_EVIDENCE.md`](EVALUATION_EVIDENCE.md).

## How this directory is organised

One folder per experiment group. Each folder has a short `README.md` explaining what it proves and how to
reproduce it. Raw artifacts are named `<experiment>.EVIDENCE.log` etc. Screenshots are in
[`screenshots/`](screenshots/), numbered to match the thesis captions (Στιγμιότυπο 1-35).

| folder | covers | screenshots |
|---|---|---|
| [`01-core-virtualization`](01-core-virtualization/) | build, semantics-preservation, static (V1) and dynamic (V2) attacks on the core, per-build diversification and its quantification | 01, 02, 03a/03b, 04, 05, 06, 32 |
| [`02-anti-debug`](02-anti-debug/) | launch-time debugger/agent detection (V5), the live post-launch attach gap (V6), and the NOP-patch that falsifies V5 (F3) | 07, 08, 09, 23 |
| [`03-anti-dump-entropy`](03-anti-dump-entropy/) | AES-256-GCM encrypt-at-rest (V3), behaviour-neutrality, and the blob entropy/chi-square figure | 10, 11 |
| [`04-result-binding`](04-result-binding/) | boolean gate vs result-binding (V4), and result-binding on the real virtualized method (F4) | 12, 24 |
| [`05-license-server-tls-nodelock`](05-license-server-tls-nodelock/) | off-machine seed delivery, fail-secure, hardware node-lock (Vlic), TLS (V8), and server hardening (F5-F7) | 13, 14, 15, 16, 17a/17b, 25, 26, 27 |
| [`06-hardening-and-usecase`](06-hardening-and-usecase/) | preview-lock removal + ingest ceiling (portability), 64-bit seed (F1), cryptographic node-lock + dev-override strip (F2) | 18, 19, 20, 21, 22 |
| [`07-jabref-integration`](07-jabref-integration/) | source-agnostic injection into JabRef 4.3.1 (headless) and the live JabRef 5.7 GUI under the full chain | 28, 29, 30 |
| [`08-rq2-performance`](08-rq2-performance/) | RQ2 performance overhead (per-call cost dominated by the node-lock; negligible in practice) | 31 |
| [`09-rq3-obfuscators`](09-rq3-obfuscators/) | RQ3 vs ProGuard/Allatori (only virtualization removes the logic from the bytecode) | 33 |
| [`10-ai-attack`](10-ai-attack/) | independent AI-assisted Level-2 attack: unprotected baseline falls, hardened product resists | 34, 35 |

## Master index: screenshot -> evidence

`file` = a raw artifact included here. `reproduce` = the driver under `../demo/` that regenerates the
state (all drivers `cd` to the repository root and tee an `EVIDENCE.log`; paths below are relative to the
repository root). "§" = thesis section.

| # | screenshot | proves | evidence in this repo / reproduce | § |
|---|---|---|---|---|
| 01 | `01_build_success` | the virtualizer builds (Maven BUILD SUCCESS) | reproduce: `mvn clean package -DskipTests` | 5.2 |
| 02 | `02_differential_test_1M_pass` | virtualization is semantics-preserving (1,000,000 inputs, 0 mismatches) | reproduce: `demo/DiffTest.java` | 5.2 |
| 03a | `03a_jadx_before_unprotected` | V1: unprotected jar leaks full logic incl. constant 7919 | reproduce: `jadx -d out license-demo.jar` | 6.2 |
| 03b | `03b_jadx_after_virtualized` | V1: virtualized jar = dispatch shell only | reproduce: `jadx -d out license-demo-vm.jar` | 6.2 |
| 04 | `04_diversification_blobs_differ` | two builds -> different `cats.meow` (per-build diversification is real) | `01-core-virtualization/diversification.blob_hashes.txt` | 5.3 |
| 05 | `05_offmachine_seed_fail_then_pass` | the blob is inert without the external seed; works with it | reproduce: run without vs with the seed | 5.4 |
| 06 | `06_dynamic_attack_opcodes_recovered` | V2: an in-process attacker recovers all 27 de-diversified opcodes | reproduce: `demo/Attacker.java` | 6.3 |
| 07 | `07_antidebug_blocks_debugger` | V5: a launch-time debugger is detected and refused | reproduce: run with `-agentlib:jdwp=...` | 6.4 |
| 08 | `08_antidebug_argprobe_finding` | v1 also catches `JAVA_TOOL_OPTIONS` injection; the thread-scan v2 is blind | reproduce: `demo/ArgProbe.java` | 6.4 |
| 09 | `09_dynamic_attach_gap_live` | V6: JDWP late-attach is impossible; an instrumentation agent attaches, invisible to v1/v2 | `02-anti-debug/attach-live.EVIDENCE.log` | 6.6 |
| 10 | `10_antidump_difftest_1M_pass` | anti-dump is behaviour-neutral (1,000,000/0 after encryption) | reproduce: DiffTest on the encrypted build | 7.1 |
| 11 | `11_antidump_blob_encrypted_before_after` | V3 closed: blob is ciphertext, 7919 + names gone | reproduce: `demo/BlobInspect.java` before/after | 7.1 |
| 12 | `12_result_binding_gate_vs_bound` | V4: a boolean gate is 1-byte-defeated; result-binding resists | `04-result-binding/result-binding.EVIDENCE.log` | 6.5 |
| 13 | `13_licenseserver_seed_delivered_pass` | seed delivered over the network after a valid license -> DiffTest pass | reproduce: `demo/jabref-inject` / server drivers | 5.5 |
| 14 | `14_licenseserver_invalid_license_locked` | wrong / no license -> refused -> locked (fail-secure) | reproduce: server drivers, invalid license | 5.5 |
| 15 | `15_licenseserver_autoregister_pass` | the build auto-registers its seed; end-to-end pass, no manual step | reproduce: build with `-Dobzcure.register.license` | 5.5 |
| 16 | `16_fingerprint_nodelock_sharing_blocked` | node-lock: machine A works, machine B (same key) refused, A works again | reproduce: two `-Dobzcure.fingerprint` values | 7.12 |
| 17a | `17a_tls_pass_and_handshake_fail` | seed over HTTPS passes; without the truststore the handshake fails (real cert validation) | reproduce: TLS server + client truststore | 7.13 |
| 17b | `17b_tls_handshake_trace_and_plaintext_refused` | plaintext HTTP to the TLS port is refused (HTTP 400) | reproduce: plaintext request to `:8443` | 7.13 |
| 18 | `18_preview_lock_removed_flagless_portable` | protected output runs on any JVM >= 17 with no `--enable-preview` | reproduce: run the flagless protected jar | 5.6 |
| 19 | `19_ingest_ceiling_java21_yes_java23_no` | ingest ceiling: a Java-21 class virtualizes, Java-23 is rejected in `ClassReader` | reproduce: version-probe classes | 5.6 |
| 20 | `20_f1_seed_widened_64bit_difftest_1M_pass` | F1: 64-bit `SecureRandom` seed; brute-force target 2^32 -> 2^64; behaviour-neutral | reproduce: DiffTest with the 64-bit seed | 7.8 |
| 21 | `21_f2_fingerprint_nodelock_crypto` | F2: right machine PASS, wrong fingerprint -> `AEADBadTagException` (fingerprint in the AES key) | reproduce: `-Dobzcure.fingerprint=EVIL-MACHINE` | 7.9 |
| 22 | `22_f2_dev_override_stripped` | F2: in a release build `-Dobzcure.seed` is compiled out -> the offline bypass is gone | reproduce: release build (`DEV_MODE=false`) | 7.9 |
| 23 | `23_f3_antidebug_nop_patch_v5_falsified` | F3: a verifier-safe 3-byte NOP removes the anti-debug guard -> V5 "BLOCKED" falsified | `02-anti-debug/anti-debug-patch.EVIDENCE.log` | 7.10 |
| 24 | `24_f4_result_binding_real_virtualized` | F4: the app derives its key from the VIRTUALIZED `checkLicense`; valid unlocks, pirate locks | `04-result-binding/result-binding-real.EVIDENCE.log` | 7.11 |
| 25 | `25_f5_register_token_required` | F5: `/register` requires a vendor token (missing/wrong -> 403) | reproduce: `/register` with/without token | 7.13 |
| 26 | `26_f6_f7_tls_enforced_no_oracle` | F6+F7: release enforces HTTPS + pinned truststore; failure cause suppressed | reproduce: release client over HTTPS vs HTTP | 7.13 |
| 27 | `27_lsvr_uniform_403_no_oracle` | server hardening: three refusal reasons all return an identical opaque 403 (no oracle) | reproduce: the three refusal cases | 7.13 |
| 28 | `28_jabref_injection_protection` | JabRef 4.3.1 headless injection under the full release path | `07-jabref-integration/jabref-inject.EVIDENCE.log` | 5.7 |
| 29 | `29_jabref_gui_unlocked` | JabRef 5.7 real GUI launched live under the full protection chain (valid license) | reproduce: `demo/jabref-inject/gui/run_jabref_gui.sh valid` | 5.7 |
| 30 | `30_jabref_gui_locked` | JabRef 5.7 GUI, pirate serial -> refusing to run, no window (fail-secure) | reproduce: `... run_jabref_gui.sh pirate` | 5.7 |
| 31 | `31_rq2_perf_breakdown` | RQ2: per-call cost dominated by the node-lock fingerprint; interpreter sub-millisecond | `08-rq2-performance/rq2-bench.EVIDENCE.log` | 8.6 |
| 32 | `32_diversification_effectiveness` | RQ1: 20 builds distinct; same-build decode 100% vs cross-build ~1/256; 0/380 whole-program | `01-core-virtualization/diversification.EVIDENCE.log` (+ `seeds.txt`, `blob_hashes.txt`) | 8.4 |
| 33 | `33_rq3_obfuscator_comparison` | RQ3: ProGuard keeps 7919+arithmetic, Allatori hides 7919 only, virtualization lifts the whole logic | `09-rq3-obfuscators/rq3-compare.EVIDENCE.log` | 8.7 |
| 34 | `34_ai_attack_baseline_falls` | independent AI attack: unprotected + diversification-without-anti-dump fall | `10-ai-attack/run-A-baseline/` | 8.9 |
| 35 | `35_ai_attack_hardened_resists` | independent AI attack: hardened product resists all three goals (1.6M brute-force rejected) | `10-ai-attack/run-B2-hardened/` | 8.9 |

The blob entropy/chi-square result (V3, thesis §7.2) is reported as prose in the thesis and its raw run is
in [`03-anti-dump-entropy/entropy.EVIDENCE.log`](03-anti-dump-entropy/) (no screenshot).

## Reproducing from scratch

Follow [`../README-DISSERTATION.md`](../README-DISSERTATION.md) section 3 (each experiment has a driver
under [`../demo/`](../demo/)). The drivers regenerate the `EVIDENCE.log` files collected here.
