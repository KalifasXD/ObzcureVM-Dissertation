# JVM Software Protection via Bytecode Virtualization — reproduction guide

This repository is a **fork of ObzcureVM** (see [`README.md`](README.md) for the base virtualizer)
extended for the MSc dissertation *"Development and Evaluation of a JVM-Level Software Protection
System via Bytecode Virtualization"* (University of Piraeus).

On top of the base virtualizer the dissertation adds:

- **Per-build opcode diversification** (a secret-seed Fisher-Yates permutation of the VM opcode
  encoding — the core technical contribution).
- A **protection envelope**: anti-debug, anti-dump (AES-256-GCM encrypt-at-rest), result-binding,
  a **license server** (off-machine seed delivery over TLS with a hardware node-lock).
- An **attack-and-harden evaluation** (Level-2 threat model) with reproducible demo drivers.

The paper-facing results ledger and the reproducible evidence behind every figure/screenshot live in the
[`evidence/`](evidence/) directory of this repository (see [`evidence/README.md`](evidence/README.md) for
the screenshot-to-artifact index).

> Status: research prototype / proof-of-concept. Not production-safe. TLS uses a self-signed cert and
> a `changeit` keystore; the hardware fingerprint is client-computed — all documented as PoC boundaries.

---

## 1. Prerequisites

- **JDK 18** on the host (used for building/running the demos; the Java-17 VM runtime runs fine on it).
  The reference build environment is **JDK 17 in Docker** (`maven:3.9-eclipse-temurin-17`); the shipped
  virtualized output is a Java-17 artifact that no longer needs `--enable-preview` (the preview lock was
  removed) and runs on any JVM >= 17.
- **Maven** (or use the Docker image, which bundles it).
- A POSIX shell. On Windows use **Git Bash** (the drivers detect the classpath separator; the JabRef GUI
  demo *must* run on native Windows). Optional: **Docker Desktop** for the reference build.
- `curl`, `sha256sum`, `jar`, `javap`, `keytool` (all in a standard JDK + Git Bash).

### External downloads (fetched per this guide, not committed — see `.gitignore`)
| tool | where | used by |
|---|---|---|
| **jadx** | github.com/skylot/jadx (unzip to `./jadx/`) | V1 static-decompilation attack |
| **JabRef 4.3.1** jar | github.com/JabRef/jabref (`jabref/JabRef-4.3.1.jar`) | headless injection demo (§5.7) |
| **JabRef 5.7 portable** | github.com/JabRef/jabref (unzip in `~/Downloads`) | live-GUI injection demo (§5.7) |
| **ProGuard 7.x** | github.com/Guardsquare/proguard | RQ3 comparison |
| **Allatori 9.x demo** | allatori.com | RQ3 comparison |

The RQ3/JabRef drivers point at the two obfuscators and the JabRef portable via env vars
(`PROGUARD_JAR`, `ALLATORI_JAR`, `JABREF_HOME`) with sensible `~/Downloads` defaults.

---

## 2. Build

```bash
# 1) the virtualizer tool jar (reference: Docker + JDK 17)
docker build -t obzcurevm-dev .
docker run --rm -v "$PWD":/work obzcurevm-dev mvn -q clean package -DskipTests
#    -> target/ObzcureVirtualMachine-1.0.1-jar-with-dependencies.jar
#    (host Maven + JDK 17/18 also works: mvn clean package -DskipTests)

# 2) the license server (Spring Boot)
cd license-server && mvn -q clean package -DskipTests && cd ..
#    -> license-server/target/license-server-0.1.0.jar

# 3) the demo license method (input to most experiments)
#    license-demo.jar (compiled from demo/LicenseDemo.java) is kept in the repo root.
```

The virtualizer usage (`-i in.jar -o out.jar -rf -sd -f`, etc.) is documented in [`README.md`](README.md).

---

## 3. Reproduce the results

Each driver `cd`s to the repo root itself and can be run from anywhere. Most tee their output to a local
`EVIDENCE.log`. Run e.g. `bash demo/rq2-bench/run_rq2_bench.sh 2>&1 | tee demo/rq2-bench/EVIDENCE.log`.

| # | driver | demonstrates | thesis | evidence |
|---|---|---|---|---|
| M1 | `demo/DiffTest.java` | virtualization is semantics-preserving (1,000,000 inputs / 0 mismatches) | §5.2 | shots 01, 02 |
| V1 | `jadx -d out <jar>` | static decompilation: original leaks logic; virtualized = dispatch shell only | §6.2 | shots 03a/03b |
| V2 | `demo/Attacker.java` | dynamic in-process recovery of the de-diversified opcodes | §6.3 | shot 06 |
| RQ1 | `demo/diversification/run_diversification.sh` | **quantitative Kerckhoffs proof**: 20 builds, same-build decode 100% vs cross-build ~1/256, 0/380 whole-program | §8.4 | shot 32 |
| V3 | `demo/entropy/run_entropy.sh` | **anti-dump blob entropy**: encrypted ~7.98 bits/byte (uniform, chi2 < crit) vs plaintext 4.28 (structured) | §7.2 | (prose) |
| V4 | `demo/result-binding/run_result_binding.sh` | boolean gate 1-byte-patched vs result-bound resists | §6.5 / §7.3 | shot 12 |
| F4 | `demo/result-binding-real/run_result_binding_real.sh` | result-binding on the real virtualized `checkLicense` | §7.11 | shot 24 |
| F3 | `demo/anti-debug-patch/run_anti_debug_patch.sh` | a verifier-safe 3-byte NOP patch removes the anti-debug guard (falsifies V5=BLOCKED) | §7.10 | shot 23 |
| V6 | `demo/attach-live/run_attach_live.sh` | post-launch instrumentation agent attaches, invisible to v1/v2 (native = future work) | §6.6 | shot 09 |
| §5.7 | `demo/jabref-inject/run_jabref_inject.sh` | source-agnostic injection into **JabRef 4.3.1** (headless), full release path | §5.7 | shot 28 |
| §5.7 | `demo/jabref-inject/gui/run_jabref_gui.sh valid` \| `pirate` | **live GUI** on modular **JabRef 5.7** (`--patch-module`), window opens/locks | §5.7 | shots 29/30 |
| RQ2 | `demo/rq2-bench/run_rq2_bench.sh` | **performance**: per-call cost dominated by the node-lock, negligible in practice | §8.6 | shot 31 |
| RQ3 | `demo/rq3-compare/run_rq3_compare.sh` | **vs ProGuard/Allatori**: only virtualization removes the logic from the bytecode | §8.7 | shot 33 |

The license server is started automatically by the drivers that need it (JabRef, RQ2), then stopped.

---

## 3a. Regenerating the PoC TLS material

The self-signed keystores and certificate are **not** committed (see `.gitignore`). They are throwaway
PoC material (`changeit` password, self-signed, `CN=localhost`). Regenerate them once before running any
TLS-dependent driver, from the repo root:

```bash
# 1) server keystore (self-signed, CN/SAN=localhost, alias licenseserver)
keytool -genkeypair -alias licenseserver -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1" \
  -storetype PKCS12 -storepass changeit -keypass changeit \
  -keystore license-server/src/main/resources/server-keystore.p12

# 2) export the server certificate
keytool -exportcert -alias licenseserver -rfc -file license-server-cert.pem \
  -storepass changeit \
  -keystore license-server/src/main/resources/server-keystore.p12

# 3) client truststore that trusts ONLY that cert (real validation, not trust-all)
keytool -importcert -noprompt -alias licenseserver -file license-server-cert.pem \
  -storetype PKCS12 -storepass changeit -keystore client-truststore.p12
```

The vendor registration token and keystore password are read from the environment
(`OBZCURE_REGISTER_SECRET`, `OBZCURE_KEYSTORE_PASSWORD`); set `OBZCURE_REGISTER_SECRET` before starting
the server or `/register` refuses every request (secure by default). The JabRef GUI demo additionally
needs a *merged* truststore (platform CAs + the pinned server cert); build it by copying
`$JAVA_HOME/lib/security/cacerts` to `demo/jabref-inject/gui/merged-truststore.p12` and importing
`license-server-cert.pem` into it with step 3's `keytool -importcert` command.

---

## 4. Environment notes / gotchas

- **The license server must run on a CLEAN JDK.** JabRef's bundled runtime links `org.tinylog.slf4j`,
  which hijacks the server's Logback. The GUI driver launches the server with the host JDK 18 and JabRef
  with its own bundled runtime.
- **The JabRef GUI demo must run on native Windows** (Git Bash), not the Docker/Linux container: it opens
  a real window on the desktop and uses JabRef's Windows bundled runtime.
- **Windows path handling:** external Windows tools (ProGuard, Allatori) need Windows-style paths; the
  drivers use `pwd -W` / relative paths rather than MSYS `/c/...` paths.
- **Process hygiene on Windows:** bash `kill` is unreliable for native `java.exe`. If port 8443 is stuck:
  `powershell -Command "Get-CimInstance Win32_Process -Filter \"name='java.exe'\" | ? CommandLine -match 'license-server' | Stop-Process -Force"`.
- **Node-lock:** the encrypted blob is bound to the machine's fingerprint at build time, so build and run
  on the same machine (a build-once/activate-later model is future work).

---

## 5. Repository layout (dissertation additions)

```
src/main/java/obzcu/re/            base virtualizer + our changes (diversification in vm/translator +
                                   virtualmachine/VMLoader; envelope in virtualmachine/{BlobCrypto,ObzcureVM})
license-server/                    Spring Boot off-machine seed server (TLS, node-lock, vendor token)
demo/                              one folder per experiment/attack (drivers + sources; build outputs gitignored)
```

The paper-facing results ledger (threat model, configurations, RQ1-RQ3, screenshot index) and the raw
evidence artifacts behind each figure live in the [`evidence/`](evidence/) directory of this repository.
