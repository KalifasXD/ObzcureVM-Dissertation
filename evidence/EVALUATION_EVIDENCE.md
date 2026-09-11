# Evaluation Evidence Ledger - JVM Protection Envelope

Paper-facing synthesis of every attack and defence result in the evaluation. Each result cites its
reproducible artifact: screenshot(s), code files, and the exact commands.

This ledger covers the technical evaluation and screenshots 01-33. The independent AI-assisted attack
(screenshots 34-35) is documented separately under `10-ai-attack/`. The master index that maps every
screenshot to its file is [`README.md`](README.md).

Scope note: the dissertation prose is written in academic Greek; this ledger is a working English
reference that matches the code and the per-folder READMEs.

Environment: bytecode core built + attacked in Docker (Maven, **JDK 17**, `--enable-preview`); the
native Windows experiments (dynamic attach) run on the host (Oracle JDK 18). Repo:
`Dissertation-Code/obzcurevm/` (vendored ObzcureVM fork + our modules + demos + license-server).

---

## 1. Threat model

| Level | Attacker | Tools | In scope? |
|---|---|---|---|
| Level 1 | casual | rename/repack, GUI | yes (trivially beaten) |
| **Level 2** | **skilled, standard tools** | **decompiler (Jadx), debugger (jdb/JDWP), `-javaagent`/instrumentation, hex/bytecode patch** | **PRIMARY target** |
| Level 3 | advanced | native/JVMTI agents, OS debuggers (x64dbg/WinDbg), process-memory reads | boundary (motivates PhD native work) |

Kerckhoffs framing (RQ1 headline): the interpreter is **public** (ObzcureVM on GitHub), so protection
cannot rest on VM secrecy. The only secret is the **per-build seed**, delivered off-machine. RQ1: can
an envelope make even a KNOWN/public virtualizer resist a Level-2 attacker, and which layers do the
work?

---

## 2. Configurations (ablation subjects)

Additive layers. Each layer is independently attributable (RQ1).

| id | configuration | artifact |
|---|---|---|
| C0 | unprotected | `license-demo.jar` |
| C1 | protected core = virtualization + per-build diversification + off-machine seed (NO envelope) | `license-demo-vm.jar` |
| C2 | C1 + anti-debug v1 (launch-time) | + `ObzcureVM.antiDebugCheck` |
| C3 | C2 + anti-dump (AES-256-GCM encrypt-at-rest) | + `BlobCrypto` |
| C4 | C3 + result-binding | design property (demo `result-binding/`) |
| C5 | C4 + real license server (network seed delivery) | + `license-server/` |
| C6 | C5 + hardware fingerprint (node-lock) | `LicenseStore` binding + `ObzcureVM.fingerprint()` |
| C7 | C6 + TLS | HTTPS keystore + client truststore (real cert validation) |

---

## 3. Envelope layers - status

| layer | mechanism | status | evidence |
|---|---|---|---|
| Virtualization | ObzcureVM stack-machine interpreter; method body -> VM program in `cats.meow` | DONE (M1) | 01, 02, 03a/03b |
| Per-build diversification | seed -> Fisher-Yates permutation of opcode numbers; emitter writes `perm[op]`, loader inverts | DONE | 04 |
| Off-machine seed | seed NOT in the jar; supplied at runtime; blob inert without it | DONE | 05 |
| Anti-debug v1 | scan `getInputArguments()` for jdwp/-agentlib/-agentpath/-javaagent; guards seed fetch | DONE | 07, 08 |
| Anti-dump (encrypt-at-rest) | AES-256-GCM on the blob; key = SHA-256(seed); authenticated (anti-tamper free) | DONE | 10, 11 |
| Result-binding | licensed computation yields a consumed value (no boolean gate to flip) | DONE (demo) | 12 |
| License server | Spring Boot; delivers seed only after a valid license; server-first, fail-secure | DONE | 13, 14, 15 |
| Hardware fingerprint | bind license -> machine (trust-on-first-use); refuse a shared license on a new machine | DONE | 16 |
| TLS | HTTPS (self-signed cert); client validates the cert (not trust-all) | DONE | 17 |

---

## 4. RQ1 - Attack x Configuration ablation matrix (the dominance data)

`SUCCESS` = attacker recovers the license logic / bypasses; `FAIL` = attack does not achieve its goal;
`-` = not applicable / superseded. All attacks are Level-2 unless noted.

| # | Attack vector | tool tier | C0 unprotected | C1 core | C2 +anti-debug | C3 +anti-dump | C4 +result-bind | C5 +server |
|---|---|---|---|---|---|---|---|---|
| V1 | Static decompilation (Jadx 1.5.0) | 2 | SUCCESS (full logic incl. 7919) | FAIL (dispatch shell only) | FAIL | FAIL | FAIL | FAIL |
| V2 | Dynamic in-process recovery (reflect `instructions`) | 2 | n/a | SUCCESS (all 27 opcodes+operands) | partial: launch-debugger path BLOCKED (07); in-proc w/ seed still recovers | same | same | same |
| V3 | Static blob dump (`BlobInspect`/hex) | 2 | n/a | SUCCESS (leaks 7919 + operands + class/method names) | SUCCESS | **FAIL (ciphertext only)** | FAIL | FAIL |
| V4 | Bytecode patch / gate bypass | 2 | SUCCESS (1-byte flip unlocks) | (returns a value already) | - | - | **FAIL (patch only locks)** | FAIL |
| V5 | Debugger/agent at LAUNCH (cmdline + `JAVA_TOOL_OPTIONS`) | 2 | n/a | not detected | **BLOCKED (detected+refused)** | BLOCKED | BLOCKED | BLOCKED |
| V6 | Debugger/agent POST-LAUNCH attach | 2/3 | n/a | n/a | JDWP = **IMPOSSIBLE** (no `Agent_OnAttach`); instrumentation/JVMTI agent = SUCCESS, **invisible to v1/v2** (needs native = PhD) | same | same | same |
| Vlic | License sharing (same key, new machine) | 2 | n/a | n/a | n/a | n/a | n/a | C5 SUCCESS (works anywhere); **C6 +fingerprint = FAIL (403 bound to another machine)** |
| V8 | Network sniff of the seed | 2 | n/a | n/a | n/a | n/a | n/a | C5/C6 (HTTP) SUCCESS (seed plaintext on wire); **C7 +TLS = FAIL (encrypted; untrusted cert rejected; plaintext refused)** |

Headline reading of the matrix:
- Virtualization defeats **static decompilation** (V1) from C1 onward.
- The core (C1) is **fully recovered dynamically** (V2) - this empirically justifies the anti-debug /
  anti-tamper envelope.
- Diversification alone leaves operands+metadata **readable in a raw blob dump** (V3) - this justifies
  anti-dump; C3 closes it.
- A **boolean gate is trivially patched** (V4) - this justifies result-binding; C4 removes the target.
- Anti-debug v1 fully covers **launch-time** debuggers/agents (V5) and, per V6, JDWP cannot even be
  attached post-launch; the residual gap is a post-launch **instrumentation/native agent**, not
  reliably detectable in pure Java (native = PhD).

---

## 5. Attack vectors - detail

### V1 - Static decompilation (Jadx 1.5.0)
- C0: full logic recovered readably: `((i*31)^7919)+(i%17)`, the `<0` branch, `%100000`, incl. the
  magic constant 7919. Attack SUCCEEDS in seconds.
- C1: only the dispatch shell (`ObzcureVM.virtualize(...); setLocal; execute()`). Algorithm + constant
  GONE. Attack FAILS to recover logic statically.
- Evidence: `03a_jadx_before_unprotected.jpg`, `03b_jadx_after_virtualized.jpg`. Reproduce:
  `jadx -d out <jar>` then read `sources/defpackage/LicenseDemo.java`.

### V2 - Dynamic in-process recovery
- Models a `-javaagent`/modified-launcher/licensed-user attacker who runs code in the licensed process
  and thus holds the runtime seed. `demo/Attacker.java` reflects the private `instructions` `VMNode[]`.
- Result (C1): recovered ALL 27 VM instructions with real de-diversified opcodes = the full
  `checkLicense` program; operands (incl. 7919, 31, 17, 100000) recoverable too. Diversification
  DEFEATED at runtime.
- Key ablation finding: C1 RESISTS static decompilation (V1) but FALLS to a dynamic in-process
  attacker (V2). Evidence: `06_dynamic_attack_opcodes_recovered.jpg`.

### V3 - Static blob dump (anti-dump justification)
- Diversification permutes OPCODES only, NOT operands/metadata. A raw dump of `cats.meow` (`unzip` +
  hex, or `demo/BlobInspect.java`) leaks in plaintext: the constant 7919 (`00 00 1e ef`), 31/17/100000,
  and the names `Meow`/`LicenseDemo`/`checkLicense`/VMNode types.
- C3 (anti-dump) result: blob is `[12B IV][AES-GCM ciphertext+tag]`; BlobInspect before/after:
  C1 = contains 7919+names TRUE, 56/256 distinct bytes; C3 = all FALSE, 239/256 distinct bytes.
- Evidence: `11_antidump_blob_encrypted_before_after.jpg`; behavior-neutral `10_antidump_difftest_1M_pass.jpg`.
- CAVEAT (do not overclaim): distinct-byte-count is a crude smell test, not proof of crypto strength;
  security rests on AES-256-GCM being a standard authenticated cipher. For a rigorous figure use
  Shannon entropy (~8.0 ideal) / chi-square. (239 not 256 because ~647 bytes < ~1500 needed for all
  values, coupon-collector.)

### V4 - Bytecode patch / gate bypass (result-binding justification)
- Boolean gate (`demo/result-binding/GateDemo`): attacker flips `if_icmpne`(0xA0)->`if_icmpeq`(0x9F),
  ONE byte, verifier-safe -> pirate serial UNLOCKED. Gate defeated without touching the logic.
- Result-bound (`BoundDemo`+`Vault`): the licensed value derives the AES key that decrypts the feature;
  the feature IS the decrypted payload, no boolean. Same class of 1-byte patch (7919->7918) -> BOTH
  legit and pirate LOCKED. Unlocking requires reproducing the exact value (136659) = defeat the real
  computation.
- Evidence: `12_result_binding_gate_vs_bound.jpg`; reproduce `bash demo/result-binding/run_result_binding.sh`.

### V5 - Debugger/agent at LAUNCH
- Anti-debug v1 scans `getInputArguments()` (verbatim in `ObzcureVM.antiDebugCheck`, reproduced in
  `demo/attach-live/AntiDebugTarget.java`). Detects jdwp/-xdebug/-agentlib/-agentpath/-javaagent.
- Covers BOTH command-line AND environment-variable (`JAVA_TOOL_OPTIONS`) injection (empirically
  re-confirmed on host, `demo/attach-live/ArgPrint.java`).
- Result: normal run works; run with `-agentlib:jdwp=...` -> "Debugger or instrumentation agent
  detected; refusing to run" -> app locks. Evidence: `07_antidebug_blocks_debugger.jpg`,
  `08_antidebug_argprobe_finding.jpg`.
- External corroboration: StackOverflow Q#5393403 accepted answer IS this technique, and states Java
  has no native-equivalent of `IsDebuggerPresent` -> reliable runtime detection needs native code.

### V6 - Debugger/agent POST-LAUNCH attach (the live gap proof)
- Proven live on native Windows JDK 18 (the container's attach handshake was broken; Windows named-pipe
  transport works). Files: `demo/attach-live/` (`run_attach_live.sh`, `EVIDENCE.log`, `README.md`).
- Finding, refined by experiment:
  1. **JDWP cannot be dynamically attached** - `jdwp.dll` exports only `Agent_OnLoad`/`Agent_OnUnload`
     (no `Agent_OnAttach`, verified via objdump). `loadAgentLibrary("jdwp")` throws
     `AgentLoadException`. So JDWP only enters at LAUNCH -> v1 already covers it. **v1 sufficient for JDWP.**
  2. **Instrumentation agent CAN attach post-launch** - `instrument.dll` exports `Agent_OnAttach`;
     `loadAgent(agent.jar)` injects a Java agent into a clean-launched target at runtime with full
     redefine/retransform, INVISIBLE to v1 (it is one of v1's own keywords but arrives after launch).
     This is the real residual gap.
  3. **v2 cannot be made rock-solid in pure Java** - the "Attach Listener" thread is always present at
     startup on Windows (verified, 3 bare launches), and `loadAgent` adds no new thread; detection is
     after-the-fact anyway; a native/OS attacker leaves no Java-visible trace (Barak ceiling).
     Reliable detection needs native/JVMTI/Windows anti-debug = PhD.
- Evidence: `09_dynamic_attach_gap_live.jpg`.

### Vlic - License sharing / node-lock (hardware fingerprint)
- Client (`ObzcureVM.fingerprint()`) computes a machine fingerprint = SHA-256(os.name|os.arch|cpus|
  hostname|/etc/machine-id|first-MAC), sent with the license as `&fingerprint=`. Server
  (`LicenseStore`+`SeedController`) binds `license -> fingerprint` on first activation
  (trust-on-first-use); a later request with a different fingerprint gets 403 "license bound to another
  machine". Re-register (new build) resets the binding.
- Result (verified, `-Dobzcure.fingerprint` simulating two machines): MACHINE-A activates -> DiffTest
  1,000,000/0 PASS; MACHINE-B (same key) -> 403 -> locked; MACHINE-A again -> PASS. License sharing
  DEFEATED. Evidence: `16_fingerprint_nodelock_sharing_blocked.jpg`.
- BOUNDARY: the fingerprint is CLIENT-COMPUTED, so a client-controlling attacker can spoof it. This
  demonstrates the license<->machine binding MECHANISM, not spoof-proof node-locking (needs hardware
  attestation/TPM, or mixing the fingerprint into the decode key so a wrong fingerprint yields a wrong
  key).

### V8 - Network sniff of the seed (TLS)
- Motivation: over plaintext HTTP the seed is visible on the wire to a network attacker. TLS encrypts
  the exchange.
- Impl: server HTTPS on 8443 with a self-signed cert (`server-keystore.p12`, CN/SAN=localhost); client
  trusts it via a dedicated truststore (`client-truststore.p12`, server cert only - real validation,
  not trust-all). No client code change (HttpClient honors `javax.net.ssl.trustStore` + the https URL).
- Result (verified): (a) POSITIVE - auto-register + DiffTest 1,000,000/0 over HTTPS (seed encrypted);
  (b) NEGATIVE (real-TLS proof) - without the truststore -> `SSLHandshakeException: PKIX path building
  failed` -> locked (client validates the cert); (c) plaintext HTTP to the TLS port -> HTTP 400, no
  seed. Evidence: `17a_tls_pass_and_handshake_fail.jpg` + `17b_tls_handshake_trace_and_plaintext_refused.jpg`.
- BOUNDARY: self-signed cert, keystore bundled on classpath, password `changeit` = PoC only (production
  externalizes the keystore + CA-signed cert). TLS closes the network-sniff vector only; the on-machine
  attacker reading the seed from process memory is unaffected (anti-debug / native story).

### Off-machine seed + license server (the enforcement backbone)
- Off-machine seed: the jar ships with NO seed; without it the blob cannot decode. Evidence:
  `05_offmachine_seed_fail_then_pass.jpg`.
- License server (Spring Boot, `license-server/`): server-first, fail-secure. Verified end-to-end,
  FOUR outcomes: valid license -> seed over HTTP -> DiffTest 1,000,000/0 PASS (`13`); wrong license ->
  403 -> locked (`14`); no license -> refused -> locked (`14`); server down -> ConnectException ->
  locked (bonus). Auto-register: the build POSTs its fresh seed itself (`15`).
- One seed does three jobs: de-diversify opcodes + AES blob key + (design) result-bound value; delivered
  only for a valid license => license check and code-runnability are INTERLOCKED.

---

## 5A. Real-application integration - JabRef (source-agnostic injection on a real third-party app)

Two milestones demonstrate the source-agnostic protection on a real, non-trivial application, not just the
`checkLicense` demo. In both the injected `LicenseEnforcer` is plain bytecode; only the guard is virtualized.

**(a) JabRef 4.3.1, headless (screenshot 28).** ASM injects `invokestatic LicenseEnforcer.enforce()` at offset 0 of
`org/jabref/JabRefMain.main` (frame-neutral: no stack/locals/StackMapTable change, `ClassWriter(0)`, the constructive use
of the F3 verifier-safety lesson). Only a minimal guard is virtualized (ObzcureVM re-emits with COMPUTE_FRAMES, which
cannot resolve JabRef's absent JavaFX supertypes, so whole-app virtualization is neither the goal nor feasible). Seal +
repack -> `jabref-protected.jar`. Runs the RELEASE deployment path (no offline seed: `DEV_MODE=false`), so the guard
fetches its per-build seed from the license server over TLS with a pinned truststore + hardware node-lock, then
result-binds. VERIFIED: valid serial 4321 -> `checkLicense=1492878012857698063` -> UNLOCK "JABREF-PREMIUM-ENABLED";
pirate 9999 -> LOCK; no-license -> LOCK. Reproducible: `bash demo/jabref-inject/run_jabref_inject.sh`. Documented limit:
the 4.3.1 GUI is not launched (its `JabRefMain` needs JDK 8 + JavaFX, which cannot coexist with the Java-17 VM runtime).

**(b) JabRef 5.7, live GUI (screenshots 29/30).** Generalizes (a) to a MODULAR JavaFX app and LIFTS the GUI limit. The
5.7 portable is a jpackage app-image (bundled Temurin JDK 18 with `org.jabref` + all `javafx.*` + `java.net.http` linked
into the runtime jimage; main = module `org.jabref` / `org.jabref.gui.JabRefLauncher`). Injection is via
`--patch-module org.jabref=<one patched JabRefLauncher.class>` (`enforce()` prepended frame-neutral at offset 0); the
virtualized guard + enforcer + VM runtime + `cats.meow` + `guard.enc` ride the classpath in package `dissertation`
(a NAMED module cannot reference a default-package class), reachable from the patched launcher via
`--add-reads org.jabref=ALL-UNNAMED` + `--add-modules java.net.http`. The FULL protection chain is active (virtualized
guard + per-build diversification + off-machine seed over TLS + node-lock + result-binding), all on JabRef's own bundled
JDK. VERIFIED LIVE: valid -> `[license] verified: JABREF-PREMIUM-ENABLED` -> the real JabRef 5.7 window OPENS (JavaFX
`PlatformImpl startup`, preferences/theme/CSS boot); pirate 9999 (real `java -m org.jabref/...` launch) -> `refusing to
run`, exit 1, NO window (fail-secure before any GUI). Reproducible: `bash demo/jabref-inject/gui/run_jabref_gui.sh
{valid|pirate|nolicense}` on native Windows (the GUI needs the desktop; the container is only for the headless pipeline).
Two integration findings, honestly recorded: the license server must run on a CLEAN JDK (JabRef's bundled runtime links
`org.tinylog.slf4j`, which hijacks the server's Logback); and `-Djavax.net.ssl.trustStore` is JVM-wide, so a pinned
server-only store breaks JabRef's own startup update-check TLS (PKIX) - fixed the realistic way, a MERGED truststore
(platform CAs + the pinned license-server cert), which leaves the license channel's validation unchanged.

Scope note (no overclaim): the attack x configuration ablation matrix (§4) is still measured on the `checkLicense` demo
method; JabRef demonstrates that the source-agnostic injection + the whole protection chain apply to a real application,
now including a live GUI. The sealed payload is a self-contained marker, not wired to a specific JabRef feature.

---

## 5B. RQ2 / EE2 - performance overhead (MEASURED 2026-09-02)

Harness `demo/rq2-bench/` (MethodHandle microbenchmark: reflect+unreflect checkLicense, invokeExact hot loop,
changing input + accumulated sink vs dead-code elimination; JIT warmup + 8 independent JVM forks + tail percentiles).
The virtualized target runs the REAL release path (seed fetched once from the license server over TLS, then cached;
every call still re-runs the per-call decode). Host JDK 18. Driver `run_rq2_bench.sh`, evidence `EVIDENCE.log`.

**Axis 1 - per-call execution time of `checkLicense` (8 forks):**
| target | n | mean | p50 | p90 | p99 |
|---|---|---|---|---|---|
| original | 120 | 4.18 ns | 3.74 ns | 4.29 ns | 13.2 ns |
| virtualized (as shipped) | 480 | 31.6 ms | 30.9 ms | 33.9 ms | 38.6 ms |

Per-call ratio ≈ **8.2e6x**. That headline is real but is **not the interpreter**: it is dominated by the node-lock.

**Axis 2 - where the per-call cost goes (breakdown, `BenchBreakdown.java`):** `BlobCrypto.keyFromSeed` recomputes the
hardware fingerprint on EVERY decrypt.
- `BlobCrypto.fingerprint()` ≈ **35 ms** (OS network-interface / MAC enumeration) - accounts for essentially the whole
  per-call cost. `InetAddress.getHostName()` ≈ 0.24 us (negligible).
- AES-256-GCM decrypt of the 647-byte blob ≈ **0.10 ms**.
- VM parse (`VMLoader.load`) + interpreter execution = a sub-millisecond remainder, swamped by the fingerprint.
So the intrinsic virtualization overhead proper is **sub-millisecond** for this method; the 8.2e6x is the per-call
node-lock computation, not interpretation.

**Axis 3 - footprint:** encrypted VM program `obzcure/cats.meow` = **647 B**; virtualized `LicenseDemo.class` = 1541 B
vs 1195 B original (**+346 B** dispatch shell). Sub-KB per virtualized method (the ~30 VM runtime classes load once and
are shared).

**Deployment framing (the §8.5 claim, now quantified):** `checkLicense` is invoked ONCE per launch (inside `enforce()`),
so the real user-visible cost is a single **~31 ms** delay at startup - imperceptible. Because only rare license methods
are virtualized (not application code), whole-app overhead is negligible.

**Honest engineering note:** the fingerprint is deterministic per machine and could be cached like the seed already is;
caching it would cut the per-call cost by ~99.7% (down to the sub-ms decrypt+parse+interpret), which is also the clean way
to isolate the true virtualization overhead. This is an optimization observation, not a security weakness.

---

## 5C. RQ1 - diversification effectiveness (MEASURED 2026-09-03) - the quantitative Kerckhoffs proof

Experiment `demo/diversification/` (`DivExp.java` replicates the toolchain's EXACT Fisher-Yates seed->permutation from
`VMLoader.buildPermutation`; `run_diversification.sh` builds N variants of the same method, confirms the blobs differ, and
computes the cross-build decode-success rate from the real per-build seeds - no decrypt needed, since a generic devirtualizer
keyed to build A decodes build B's stored `permB[op]` as `invPermA[permB[op]]`, correct only where `permA[op]==permB[op]`).
N = 20 builds of the same `checkLicense`. Host JDK 18.
- **20/20 distinct `cats.meow` blobs, 20/20 distinct seeds, 20/20 distinct permutations** (extends the earlier smell-test
  "BLOBS DIFFER" to a quantified sample).
- **SAME-build decode = 100%** (a build's own table always decodes it; legitimate users are unaffected).
- **CROSS-build decode = 0.405% per opcode** (theory 1/256 = 0.391%); **0.421%** over the program's 15 distinct opcodes;
  **0 of 380 ordered build-pairs** decode the WHOLE program correctly. Theoretical P(whole program) cross-build =
  (1/256)^15 ≈ **7.5e-37**.
- Interpretation: this is the quantitative form of the Kerckhoffs claim - publishing the interpreter (ObzcureVM is public on
  GitHub) is safe; the ONLY secret is the per-build seed. A reused/generic devirtualizer built against one build is worthless
  against the next. HONEST NUANCE: this defeats GENERIC/REUSED tooling, not a per-build dynamic in-process attacker who holds
  the running seed (V2, §5 detail) - diversification raises the required attacker tier; the envelope and the Barak limit govern
  the rest.
- Evidence: screenshot 32 (`32_diversification_effectiveness.jpg`, captured + embedded in docx §8.4); `demo/diversification/EVIDENCE.log`, `seeds.txt`, `blob_hashes.txt`.

---

## 5D. Blob entropy / uniformity (F8, V3) - MEASURED 2026-09-03

Experiment `demo/entropy/` (`EntropyExp.java` parses `cats.meow` = `[count][len][blob]`, computes Shannon entropy +
chi-square on the byte histogram; `run_entropy.sh` builds N variants and concatenates the per-build blobs for statistical
power). Corpus = 20 per-build blobs. Rigorous replacement for the "56/256 vs 239/256 distinct bytes" smell test.
- **PLAINTEXT VM program (C1, diversified, decrypted):** 12,220 B, 179/256 distinct, entropy **4.28 bits/byte**,
  chi2 = 321,177 (df=255) -> strongly NON-uniform (structured: leaks opcodes/operands incl. the constants).
- **ENCRYPTED blob (anti-dump, C3):** 12,780 B, 256/256 distinct, entropy **7.98 bits/byte** (ideal 8.0),
  chi2 = **282.0 < critical 293.2** (alpha=0.05, df=255) -> consistent with UNIFORM / statistically indistinguishable
  from random.
- Closes F8 / the V3 caveat: the AES-256-GCM anti-dump blob leaks no statistical structure; entropy + chi-square supersede
  the distinct-byte count. Reported as prose numbers (no screenshot).

---

## 5E. RQ3 / EE3 - virtualization vs commodity obfuscators (MEASURED 2026-09-03)

Experiment `demo/rq3-compare/` (`run_rq3_compare.sh`: **ProGuard 7.10** + **Allatori 9.9 demo** obfuscate `license-demo.jar`,
keeping `checkLicense`'s name; javap-based static analysis checks whether the license LOGIC survives). Host JDK 18. Axis =
static-decompilation resistance of the license logic (the constant **7919** + the arithmetic imul/ixor/irem).

| variant | const 7919 | arithmetic | vm-dispatch | jar size |
|---|---|---|---|---|
| Original (C0) | YES | YES | no | 3192 B |
| ProGuard | YES | YES | no | 538 B |
| Allatori | no | YES | no | 4298 B |
| Virtualized (C1) | no | no | YES | 68516 B |

- **ProGuard** (shrink + rename): hides NOTHING of the logic - 7919 and the arithmetic are fully recoverable (the jar just
  gets smaller).
- **Allatori** (string-encryption maximum + default control-flow/renaming): hides the literal 7919, but the arithmetic
  STRUCTURE (imul/ixor/irem) stays recoverable - partial protection.
- **Virtualization**: lifts the ENTIRE logic (constant AND arithmetic) into the encrypted `cats.meow`; the `checkLicense`
  body is only an interpreter dispatch. The difference is of CLASS, not degree -> confirms the §8.7 hypothesis
  (Batchelder & Hendren 2007; Ceccato et al. 2014). Cost = size (67 KB, the bundled interpreter runtime) vs a few KB.
- Gotcha: ProGuard/Allatori are Windows java -> need Windows paths (`$ROOT` via `pwd -W`) + quoted "Program Files"; Allatori
  9.9 config: `string-encryption=maximum` (not `enable`), control-flow is default (there is no `control-flow` property).
- The second RQ3 part (hardened C7 vs unhardened C1) is covered by Table 8.1 + §8.8; the external attacker stays pending.
- Evidence: screenshot 33 (`33_rq3_obfuscator_comparison.jpg`, captured + embedded in docx §8.7); `demo/rq3-compare/EVIDENCE.log`.

---

## 6. Screenshot index (01-33)

| # | file | proves |
|---|---|---|
| 01 | build_success | virtualizer builds (Maven BUILD SUCCESS) |
| 02 | differential_test_1M_pass | virtualization is semantics-preserving (1,000,000 inputs, 0 mismatches) |
| 03a/03b | jadx_before/after | V1: unprotected leaks full logic; protected = dispatch shell only |
| 04 | diversification_blobs_differ | two builds -> different `cats.meow` (per-build diversification real) |
| 05 | offmachine_seed_fail_then_pass | blob inert without the external seed; works with it |
| 06 | dynamic_attack_opcodes_recovered | V2: in-process attacker recovers all 27 opcodes+operands |
| 07 | antidebug_blocks_debugger | V5: launch-time debugger detected -> refused |
| 08 | antidebug_argprobe_finding | v1 also catches `JAVA_TOOL_OPTIONS` injection; thread-scan v2 blind |
| 09 | dynamic_attach_gap_live | V6: JDWP late-attach impossible; instrumentation agent attaches, invisible to v1/v2 |
| 10 | antidump_difftest_1M_pass | anti-dump is behavior-neutral (1,000,000/0 after encryption) |
| 11 | antidump_blob_encrypted_before_after | V3 closed: blob ciphertext, 7919+names gone, 56->239 distinct bytes |
| 12 | result_binding_gate_vs_bound | V4: boolean gate 1-byte-defeated; result-bound resists |
| 13 | licenseserver_seed_delivered_pass | seed delivered over HTTP after valid license -> DiffTest pass |
| 14 | licenseserver_invalid_license_locked | wrong/no license -> refused -> locked (fail-secure) |
| 15 | licenseserver_autoregister_pass | build auto-registers its seed; end-to-end pass, no manual step |
| 16 | fingerprint_nodelock_sharing_blocked | node-lock: machine A activates+works, machine B (same key) refused, A works again |
| 17a/17b | tls_pass_and_handshake_fail / tls_handshake_trace_and_plaintext_refused | seed over HTTPS passes; no-truststore -> handshake fails (real cert validation); plaintext refused (400) |
| 18 | preview_lock_removed_flagless_portable | use-case extension: runtime is preview-free (`minor version 0`, was 65535), DiffTest 1M/0 flagless -> protected output runs on any JVM >= 17 with no `--enable-preview` |
| 19 | ingest_ceiling_java21_yes_java23_no | probe 2: Java-21 class (major 65) ingested + virtualized (dispatch shell), Java-23 (major 67) rejected in `ClassReader`; active reader = Maven ASM 9.6 (ingest ceiling Java 22) |
| 20 | f1_seed_widened_64bit_difftest_1M_pass | F1 core hardening: `OBZCURE_SEED` now a 64-bit `SecureRandom` `long` (magnitude outside 32-bit `int` range) + DiffTest 1,000,000/0 -> widening is behaviour-neutral (2^32 -> 2^64 brute-force target) |
| 21 | f2_fingerprint_nodelock_crypto | F2 part 1 (dev build): SAME seed, right machine = DiffTest 1,000,000/0 PASS (behaviour-neutral), wrong machine (`-Dobzcure.fingerprint=EVIL-MACHINE`) = `AEADBadTagException: Tag mismatch!` -> fingerprint is in the AES key = cryptographic node-lock at the cipher layer |
| 22 | f2_dev_override_stripped | F2 part 2 (hardened build, DEV_MODE=false): `-Dobzcure.seed=...` is ignored (branch compiled out) -> falls through to "No license provided" -> lock; the offline one-flag bypass is gone from the release bytecode |
| 23 | f3_antidebug_nop_patch_v5_falsified | F3: `EVIDENCE.log` - `antiDebugCheck()` is plaintext `invokestatic #141`; same `-agentlib:jdwp` launch BLOCKED before the patch, then DiffTest 1,000,000/0 PASS after a verifier-safe 3-byte NOP removes the guard -> V5 "BLOCKED" cell falsified for a tool-combining attacker |
| 24 | f4_result_binding_real_virtualized | F4: `EVIDENCE.log` - feature sealed under `checkLicense(4321)=36659`; app derives its key from the VIRTUALIZED `checkLicense` -> valid 4321 UNLOCKED "PREMIUM: all features enabled", pirate 9999 (=4193) LOCKED; result-binding on the real virtualized method, no boolean gate |
| 25 | f5_register_token_required | F5: `/register` with no token and wrong token -> 403 "invalid or missing vendor token"; correct token -> 200 "registered license=ABC-123" (vendor-secret-gated registration) |
| 26 | f6_f7_tls_enforced_no_oracle | F6+F7: release build auto-registers over HTTPS with the token (HTTP 200); release client over HTTPS + pinned truststore -> DiffTest 1,000,000/0 PASS (seed over TLS, no -Dobzcure.seed); over cleartext HTTP -> locked with the cause suppressed (generic failure only) |
| 27 | lsvr_uniform_403_no_oracle | A1/A2 server hardening: the three refusal reasons (bound to another machine / unknown license / missing fingerprint) all return an IDENTICAL opaque 403 to the client (no server-side oracle); the real reason is logged server-side only. Real terminal capture |
| 28 | jabref_injection_protection | JabRef 4.3.1 real-app injection (headless, §5A a): source-agnostic ASM `enforce()` at `JabRefMain.main` offset 0, guard virtualized, release path (server seed over TLS + node-lock + result-binding). valid 4321 -> UNLOCK "JABREF-PREMIUM-ENABLED", pirate 9999 -> LOCK, no-license -> LOCK |
| 29 | jabref_gui_unlocked | JabRef 5.7 real GUI (modular JavaFX, §5A b) launched LIVE under the full protection chain: valid license -> `[license] verified: JABREF-PREMIUM-ENABLED` -> the real JabRef window OPENS (injection via `--patch-module`, guard on the classpath). Clean run (merged truststore, no PKIX noise) |
| 30 | jabref_gui_locked | JabRef 5.7 GUI, pirate serial 9999 -> `enforce()` fails result-binding -> "refusing to run", exit 1, NO window (fail-secure before the GUI launches) |
| 31 | rq2_perf_breakdown | RQ2/EE2 (§5B, §8.6): per-call time original 3.9 ns vs virtualized ~31.7 ms (p50, 8 forks) = ~8.2e6x, AND the breakdown showing the node-lock `fingerprint()` (~34 ms) dominates while AES-GCM decrypt (~0.12 ms) and the interpreter are negligible |
| 32 | diversification_effectiveness | RQ1 (§5C, §8.4): 20 builds -> 20 distinct blobs/seeds/permutations; same-build decode 100% vs cross-build ~0.4%/opcode (theory 1/256), 0/380 pairs decode the whole program, (1/256)^15 = 7.5e-37 = quantitative Kerckhoffs proof |
| 33 | rq3_obfuscator_comparison | RQ3/EE3 (§5E, §8.7): static-decompile comparison - ProGuard keeps 7919+arithmetic, Allatori hides 7919 but keeps arithmetic, only virtualization lifts the whole logic (vm-dispatch); class-not-degree difference |

---

## 7. Reproducibility (commands)

Bytecode core (container, `/work`):
```bash
mvn clean package -DskipTests
# start license server in background (fixes the "server down between steps" pain):
cd /work/license-server && mvn -q clean package -DskipTests
nohup java -jar target/license-server-0.1.0.jar > /tmp/lsvr.log 2>&1 &
until curl -s -o /dev/null 'http://localhost:8080/seed?license=x'; do sleep 1; done
# build + auto-register + capture seed:
cd /work
java -Dobzcure.register.license=ABC-123 --enable-preview \
     -jar target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar \
     -i license-demo.jar -o license-demo-vm.jar -rf -sd -f
# positive run (seed fetched from server):
java --enable-preview -Dobzcure.license=ABC-123 -cp build/classes DiffTest
# leak check:
javac -d /tmp/bi demo/BlobInspect.java
java -cp /tmp/bi BlobInspect build-a.jar          # BEFORE (leaks)
java -cp /tmp/bi BlobInspect license-demo-vm.jar  # AFTER  (ciphertext)
```
Result-binding demo (JDK-only): `bash demo/result-binding/run_result_binding.sh && cat demo/result-binding/EVIDENCE.log`
Dynamic-attach proof (native Windows / WSL-with-Windows-JDK):
`cd demo/attach-live && bash run_attach_live.sh && cat EVIDENCE.log`

---

## 8. Consolidated honest boundaries / limitations (for the Threats-to-Validity / Limitations section)

1. **Dynamic in-process attacker (V2) beats the core.** At runtime the blob must be decrypted and the
   opcodes de-diversified in memory, so a licensed-process attacker recovers everything. Envelope
   raises the tier (must defeat anti-debug + hook the process) but not absolute (Barak: cannot hide a
   secret on attacker hardware).
2. **Post-launch instrumentation/native agent (V6) not detectable in pure Java.** v1 covers launch-time
   and JDWP entirely; the residual needs native/JVMTI = PhD.
3. **Entropy metric is a smell test, not proof.** Use Shannon entropy/chi-square for a rigorous figure.
4. **License server PoC hardening:** node-lock DONE (16) but fingerprint is CLIENT-COMPUTED -> spoofable
   by a client-controlling attacker (mechanism, not spoof-proof; needs attestation/TPM or mixing the
   fingerprint into the decode key). TLS DONE (17) but self-signed cert + bundled keystore + `changeit`
   password = PoC (production externalizes keystore + CA cert). TLS closes network-sniff only; the
   license key is still a plaintext URL param (inside TLS now, but logged server-side).
5. **Result-binding integrated on a real app (was standalone).** Demonstrated in a dedicated demo (F4) AND wired
   into the real JabRef injection (§5A): `enforce()` unseals `guard.enc` only if the VIRTUALIZED guard reproduces the
   licensed value. Residual: the sealed payload is a self-contained marker, not yet tied to a specific JabRef feature.
6. **Single demo method** (`checkLicense`, pure int arithmetic). ObzcureVM cannot virtualize
   `invokedynamic` (lambdas/string-switch). RUNTIME-version limit LIFTED 2026-08-26: the preview lock
   was removed (see §10 probe 1), so the protected output is now major-61/minor-0 and runs on any JVM
   >= 17 with no `--enable-preview` (previously it required Java 17 + the flag even to load). INGEST
   ceiling is Java 22 (probe 2): the ACTIVE reader is the Maven `org.ow2.asm` 9.6 dependency, not the
   vendored `~9.2` core (whose `>V18` gate is dead/shadowed) - verified, a Java-21-versioned class
   virtualizes while Java-23 is rejected in `ClassReader.<init>`. DIRECTLY TESTED: major 65 accepted,
   major 67 rejected; major 66 (Java 22) is INFERRED from ASM 9.6's documented V22 max + that bracket,
   not itself tested (trivial to confirm). Also note the inputs were 17-level bytecode with a patched
   version field, isolating the version gate (not real Java-21 constructs). So the tool ingests Java 17..22 today;
   Java 23+ = bump ASM to 9.7+. (Validated with a synthetic major-65 class; a real Java-21 jar - e.g.
   JabRef itself - and running a major-65 output on a real JDK >= 21 remain as final confirmations.)
7. **Attacker-is-builder mitigation:** the core is a fixed, independent public artifact (ObzcureVM);
   still, line up >=1 external attacker on >=1 layer.

---

## 9. Load-bearing design decisions (rationale trail)

- **Bytecode virtualization as the technical core** (Session 7). ObzcureVM adopted as a vendored,
  pinned, attributed fork (Session 8); novelty = OPEN + HARDENED + ATTACK-EVALUATED + REPRODUCIBLE
  realization, NOT the technique (prior art: VMProtect/Themida/Tigress, DIVILAR, ASPIRE, ObzcureVM,
  qProtect; closest Java = Pizzolotto/Ceccato oblive).
- **Per-build diversification** = adopted established mechanism (cite DIVILAR/ASPIRE), its role here is
  making a PUBLIC core viable (Kerckhoffs) + the empirical demo.
- **Off-machine seed** (Session 10) chosen over merely encrypting it: the seed leaves the artifact
  entirely; delivered by the server post-auth. One secret, later unified as the AES key too.
- **Anti-debug is portable JVM-level** for Level 2 (getInputArguments); native OS anti-debug = Level
  3/PhD.
- **Result-binding kept; full encrypt-all-features redesign rejected** as over-engineering.
- **Server-first enforcement:** server validates first; local checks enforce; virtualize the
  enforcement so a Level-2 attacker cannot cheaply patch it.

---

_Last updated: 2026-09-03 (envelope complete client-side + server; A1/A2 server hardening = shot 27; JabRef real-app
integration DONE = headless 4.3.1 (shot 28) + LIVE 5.7 GUI (shots 29/30), see §5A; 3 conceptual diagrams embedded. RQ2
(§5B, shot 31), diversification/RQ1 (§5C, shot 32), blob entropy (§5D), RQ3 (§5E, shot 33) all MEASURED + written into the
docx (§8.4/§8.6/§8.7/§7.2) + abstract/§8.1/§9 reconciled. Still pending: >=1 external attacker (needs a person); native
anti-debug = PhD)._

---

## 10. Level-2 hardening backlog (internal audit, 2026-08-26)

An internal Level-2 gap analysis (read-only, source-cited) found the envelope is **not yet
Level-2-complete**. The dominant issue is cryptographic, not native. Sequencing decision (user):
**harden the core (F1, F2), measure F3, then JabRef with F4 built into the injection.**

| id | finding | sev | status |
|---|---|---|---|
| F1 | Whole crypto core rests on a **32-bit seed** (`Obzcure.java:39` `new Random().nextInt()`; AES key = `SHA-256("obzcure-blob:"+seed)`, `BlobCrypto.java:34`). Blob ships in the jar; plaintext always starts with `writeUTF("Meow")` = `00 04 4D 65 6F 77`. => offline known-plaintext brute force over 2^32 seeds recovers the key with no debugger/server/network, collapsing diversification + anti-dump + off-machine-seed + node-lock into one search. `Random()` is clock-seeded (weaker still). | HIGH | **DONE + VERIFIED (2026-08-26).** Widened `int`->`long` and `new Random().nextInt()`/`new Random().nextInt()` -> `new SecureRandom().nextLong()` across all 8 files of the seed path (build/crypto/loader/runtime/server). Brute-force target now 2^64 (offline search infeasible), and the RNG is cryptographic (no clock seed). Behaviour-neutral: DiffTest 1,000,000 inputs / 0 mismatches with the 64-bit seed (whole SecureRandom -> SHA-256 -> AES-256 + opcode-permutation chain). Evidence: screenshot 20. NOTE: the permutation's `new Random(seed)` still draws on the low 48 bits (LCG) by design; the AES key hashes the full 64-bit `long`, so the protected secret has full entropy. |
| F2 | Seed is a replayable, portable license-equivalent; `-Dobzcure.seed` (`ObzcureVM.java:71`) is a plaintext, compiled-in one-flag bypass of server+fingerprint+TLS (returned before any server logic; cached static). Node-lock bypassed by *not asking*, not spoofing. | HIGH | **DONE + VERIFIED (2026-08-26).** (1) Fingerprint mixed into the decode key: `BlobCrypto.keyFromSeed` = `SHA-256("obzcure-blob:"+seed+"|"+fingerprint)`, fingerprint centralised in `BlobCrypto.fingerprint()` (single source of truth; `ObzcureVM.fingerprint()` delegates). A seed on the wrong machine now yields the wrong key -> GCM `AEADBadTagException` -> lock (cryptographic node-lock, not a server 403). (2) Overrides stripped from release: `-Dobzcure.seed`/`-Dobzcure.fingerprint` gated behind compile-time `BlobCrypto.DEV_MODE`, committed **false** (secure-by-default; `javac` elides the branches, so no runtime flag/reflection can re-enable them). VERIFIED: dev build (DEV_MODE=true) same seed, right machine = DiffTest 1,000,000/0 PASS (behaviour-neutral), wrong machine (`-Dobzcure.fingerprint=EVIL-MACHINE`) = `AEADBadTagException: Tag mismatch!` at `BlobCrypto.decrypt`; hardened build (DEV_MODE=false) `-Dobzcure.seed` ignored -> "No license provided" -> lock. Screenshots 21 (crypto node-lock) + 22 (override stripped). BOUNDARY: blob is bound to the fingerprint present at ENCRYPTION time (PoC build+run share the machine); a build-once/activate-later model needs a server-side seed-wrapping envelope (future work); fingerprint still client-computed (spoofable, documented); dynamic in-process attacker who reads the decrypted seed+fingerprint from live memory unaffected (Level-3/native). |
| F3 | `antiDebugCheck()` is unvirtualized plaintext (`ObzcureVM` is excluded from virtualization); one `invokestatic` at top of `fetchSeed()`. A verifier-safe 3-byte NOP patch removes it, then attach `jdb`. **Falsifies the V5 "BLOCKED" cell** for a tool-combining attacker. | HIGH | **DONE + MEASURED (2026-08-26).** New attack row demoed in `demo/anti-debug-patch/` (JDK-only `NopAntiDebug` patcher + `run_anti_debug_patch.sh`, mirrors V4 GateDemo). `javap` of the shipped `ObzcureVM.class` shows `antiDebugCheck()` in cleartext, dispatched by `invokestatic #141` at the top of `fetchSeed`. Same `-agentlib:jdwp` launch: BEFORE = "Debugger or instrumentation agent detected; refusing to run" (V5 BLOCKED); patch NOPs the 1 call site (verifier-safe: no-arg void call, no stack/offset change, StackMapTable stays valid); AFTER = DiffTest 1,000,000/0 PASS with JDWP still listening. **V5 "BLOCKED" is falsified for a tool-combining attacker.** Screenshot 23. FIX DIRECTION (not built; for eval): virtualize the guard, or result-bind it (mix its outcome into the key so removal yields a wrong key), turning "delete the detector" into "defeat the real computation" - i.e. F4's lesson. |
| F4 | Result-binding is absent from the real path: `main` just prints `checkLicense`; nothing consumes it. The demo derives its key from `Vault.licenseValue`, a plaintext re-impl that does NOT use the seed. So "one seed does three jobs" is really two. | HIGH | **DONE + VERIFIED (2026-08-26), integrated on LicenseDemo (user chose this over jumping to JabRef; JabRef will reuse the pattern).** `demo/result-binding-real/`: `FeatureGen` seals a premium payload (AES-256-GCM) under `checkLicense(4321)=36659`, the value of the ACTUAL method loaded from the ORIGINAL jar (no plaintext re-impl); `PremiumApp` derives its decrypt key from the **VIRTUALIZED** `checkLicense` loaded from `license-demo-vm.jar` (runs inside the VM, needs the seed) - no boolean gate, the feature IS the decrypted payload. VERIFIED: valid serial 4321 -> `checkLicense[virtualized](4321)=36659` -> UNLOCKED "PREMIUM: all features enabled"; pirate 9999 -> `=4193` -> LOCKED (feature sealed). Screenshot 24. So the result-bound value is now tied to the real virtualized/diversified/encrypted/seeded/node-locked computation; to unlock a pirate serial the attacker must reproduce checkLicense's licensed output (patching the VM blob only corrupts decode, never yields it). BOUNDARY: checkLicense is modular (%100000) so serials can collide (mechanism demo, not a collision-resistant scheme); dev build supplies the seed offline; a licensed dynamic attacker still reads the decrypted feature from memory (Level-3). |
| F5 | `/register` unauthenticated (`SeedController.java:21`); resets node-lock binding to null fingerprint => binding-hijack / node-lock reset. | MED | **DONE + VERIFIED (2026-08-26).** `/register` now requires a shared vendor token (`SeedController` `@Value("${obzcure.register.secret}")`, checked against `?token=`); missing/wrong/unset -> 403 (secure-by-default). The build's auto-register presents it via `-Dobzcure.register.secret`. VERIFIED (server up): no-token & wrong-token -> 403 "register: invalid or missing vendor token"; good token -> 200 "registered". Screenshot 25. |
| F6 | TLS is opt-in per run; client defaults to cleartext `http://localhost:8080` and never enforces https (`ObzcureVM.java:81`). | MED | **DONE + VERIFIED (2026-08-26).** Release (`DEV_MODE=false`) client + build default to `https://localhost:8443`, REFUSE a non-https server, and REFUSE to fall back to the default (public-CA) trust store - must validate against a pinned trust store (`-Djavax.net.ssl.trustStore`). Dev keeps cleartext localhost for the offline demos (compile-time gated). VERIFIED: release client over HTTPS (pinned truststore) -> seed over TLS -> DiffTest 1,000,000/0 PASS (no `-Dobzcure.seed`); over cleartext HTTP -> locked. Screenshot 26. BOUNDARY: full embedded-cert pinning (bundling the truststore into the artifact so it survives virtualization) is the remaining refinement; today the pin is the operator-supplied truststore (which holds only the server cert) + the release refusal to fall back to public-CA trust. |
| F7 | Failure paths `printStackTrace` (`ObzcureVM.java:173-193`) => attacker failure-mode oracle. | MED | **DONE + VERIFIED (2026-08-26).** All three runtime `printStackTrace` sites (`ObzcureVM` :145/:162/:215, now DEV_MODE-gated) print only in dev; a release build shows only the generic "Failed loading .meow data" and never the cause (AEADBadTagException / "No license provided" / "Debugger detected" / "Release requires https"). VERIFIED: release client over HTTP -> locked with the cause suppressed (frames of the generic failure only, no reason string). Screenshot 26. |
| F8 | Ablation presents seed-derived layers as independent. | eval | **report key entropy (now 64-bit after F1; was 32-bit) alongside the byte-histogram; state that one seed-recovery collapses four layers** |

Correctly scoped, no action: V2 in-process recovery + V6 post-launch agent (Barak / native = PhD),
fingerprint spoofability (documented), invokedynamic/Java-17 capability limits, GCM IV handling
(fresh random 96-bit IV per blob, `BlobCrypto.java:43` = fine).

_Backlog captured 2026-08-26; **F1 DONE + VERIFIED 2026-08-26** (64-bit SecureRandom seed, DiffTest 1M/0); **F2 DONE + VERIFIED 2026-08-26** (fingerprint-in-key crypto node-lock + compile-time strip of `-Dobzcure.*` overrides, secure-by-default); **F3 DONE + MEASURED 2026-08-26** (anti-debug guard NOP-patched -> V5 "BLOCKED" falsified, new attack row); **F4 DONE + VERIFIED 2026-08-26** (result-binding integrated on the real virtualized checkLicense; JabRef will reuse the pattern); **F5+F6+F7 DONE + VERIFIED 2026-08-26** (register vendor token; release TLS enforcement + pinned-truststore requirement; release failure-mode oracle suppressed). **Entire audit backlog F1-F7 addressed; F8 = eval-reporting only (key entropy now 64-bit).**_
