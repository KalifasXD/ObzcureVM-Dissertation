# JVM Software Protection via Bytecode Virtualization (MSc dissertation)

This repository contains the implementation and the experimental evidence for my MSc dissertation,
*"Development and Evaluation of a JVM-Level Software Protection System via Bytecode Virtualization"*
(University of Piraeus, MSc in Cybersecurity and Data Science).

It is **based on the original ObzcureVM project** by HoverCatz:
**https://github.com/HoverCatz/ObzcureVirtualMachine**. ObzcureVM provides the base bytecode virtualizer
(a stack-machine interpreter that lifts a method body into a custom instruction set stored as a resource).
The dissertation takes that project as its starting point and evolves it into a source-agnostic software
protection system, then attacks and hardens it under a defined threat model. The original project's README
is preserved here as [`README-ObzcureVM-upstream.md`](README-ObzcureVM-upstream.md), and the base is
licensed under GPL-3.0 (see [`LICENSE.md`](LICENSE.md)); this repository keeps that license.

## What the dissertation adds

On top of the base virtualizer:

- **Per-build opcode diversification** - a secret, per-build Fisher-Yates permutation of the VM opcode
  encoding, so a devirtualizer built against one build does not work against the next (a Kerckhoffs
  framing: the interpreter is public, only the per-build seed is secret).
- **A protection envelope** around the VM: anti-debug, anti-dump (AES-256-GCM encrypt-at-rest of the VM
  program), result-binding (the licensed computation yields a value the application needs, so there is no
  boolean gate to patch), and an **off-machine license server** that delivers the per-build seed over TLS
  only after a valid license, bound to the machine (hardware node-lock).
- **Source-agnostic injection** demonstrated on a real application (JabRef 4.3.1 headless and JabRef 5.7
  with a live GUI).
- **An attack-and-harden evaluation** under a Level-2 threat model (decompiler, debugger, instrumentation
  agent, hex/bytecode patcher), including an independent AI-assisted red-teaming attack.

The contribution is the open, hardened, attack-evaluated and reproducible realisation and its evaluation,
not the virtualization technique itself (which is prior art).

## Repository layout

```
src/                 the evolved virtualizer (diversification in vm/translator + virtualmachine/VMLoader;
                     envelope in virtualmachine/{BlobCrypto, ObzcureVM})
license-server/      Spring Boot off-machine seed server (TLS, node-lock, vendor token; secrets externalized)
demo/                one folder per experiment/attack (drivers + sources; transient build outputs gitignored)
evidence/            the experimental evidence: EVIDENCE.log files, statistics, attack transcripts,
                     screenshots, and a screenshot-to-artifact index (evidence/README.md)
README-DISSERTATION.md      detailed build + reproduction guide (prerequisites, keytool, driver table)
README-ObzcureVM-upstream.md  the original ObzcureVM README, preserved for attribution
```

## Quick start

```bash
# build the virtualizer (reference: Docker + JDK 17; host Maven + JDK 17/18 also works)
mvn clean package -DskipTests

# build the license server
cd license-server && mvn -q clean package -DskipTests && cd ..

# reproduce an experiment (each driver tees an EVIDENCE.log)
bash demo/diversification/run_diversification.sh     # RQ1: quantitative diversification effectiveness
```

The full prerequisites, the TLS-material regeneration (`keytool`), and a driver-by-driver table are in
[`README-DISSERTATION.md`](README-DISSERTATION.md). The evidence behind every figure and screenshot in the
thesis, mapped screenshot-by-screenshot to its artifact, is in [`evidence/`](evidence/).

> Status: research prototype / proof-of-concept, not production-safe. TLS uses a self-signed certificate
> and a `changeit` keystore, and the hardware fingerprint is client-computed - all documented as PoC
> boundaries in the thesis and the evidence.
