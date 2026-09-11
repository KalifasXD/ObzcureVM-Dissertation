# 08 - RQ2: performance overhead

A MethodHandle microbenchmark (JIT warmup, 8 independent JVM forks, tail percentiles, dead-code sink)
measures the per-call cost of the virtualized `checkLicense` on the real release path.

## What the screenshot proves
- **31** - per-call time original about 4 ns vs virtualized about 31 ms (p50), a ratio near 8.2e6x - but
  the breakdown shows this is almost entirely the per-call node-lock fingerprint (about 35 ms of OS
  network-interface enumeration). AES-GCM decrypt is about 0.1 ms and the interpreter is a sub-millisecond
  remainder. Because `checkLicense` runs once per launch, the real user-visible cost is a single ~31 ms
  startup delay.

## Files
- `rq2-bench.EVIDENCE.log` - the benchmark run and the breakdown.

## Reproduce
`bash demo/rq2-bench/run_rq2_bench.sh`.

## Honest boundary
The fingerprint is deterministic per machine and could be cached like the seed (which would cut about
99.7% of the per-call cost); this is an optimization observation, not a security weakness. The intrinsic
virtualization overhead is sub-millisecond for this method.
