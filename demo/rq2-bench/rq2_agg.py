# -*- coding: utf-8 -*-
"""Aggregate RQ2 Bench SAMPLE lines (per-call ns) across all forks.
Reads the tee'd log on argv[1] (or stdin); prints per-target count/mean/min/p50/p90/p99
and the virtualized/original slowdown factor (on medians and on means)."""
import sys, statistics as st

def pct(xs, p):
    if not xs: return float('nan')
    xs = sorted(xs); k = (len(xs)-1)*p/100.0
    lo = int(k); hi = min(lo+1, len(xs)-1)
    return xs[lo] + (xs[hi]-xs[lo])*(k-lo)

data = {}
src = open(sys.argv[1], encoding='utf-8', errors='ignore') if len(sys.argv) > 1 else sys.stdin
for line in src:
    parts = line.split()
    if len(parts) == 3 and parts[0] == 'SAMPLE':
        try: data.setdefault(parts[1], []).append(float(parts[2]))
        except ValueError: pass

def row(name, xs):
    return (name, len(xs), st.mean(xs), min(xs), pct(xs,50), pct(xs,90), pct(xs,99))

print("=" * 78)
print("RQ2 / EE2 - per-call execution time of checkLicense (nanoseconds)")
print("=" * 78)
print("%-13s %6s %12s %12s %12s %12s %12s" % ("target","n","mean","min","p50","p90","p99"))
for t in ("original", "virtualized"):
    xs = data.get(t, [])
    if not xs: print("%-13s   (no samples)" % t); continue
    _, n, mean, mn, p50, p90, p99 = row(t, xs)
    print("%-13s %6d %12.2f %12.2f %12.2f %12.2f %12.2f" % (t, n, mean, mn, p50, p90, p99))

o = data.get("original", []); v = data.get("virtualized", [])
if o and v:
    print("-" * 78)
    print("slowdown (median virtualized / median original): %.1fx" % (pct(v,50)/pct(o,50)))
    print("slowdown (mean   virtualized / mean   original): %.1fx" % (st.mean(v)/st.mean(o)))
    print("absolute added cost per call (median): %.1f ns  (%.2f us)"
          % (pct(v,50)-pct(o,50), (pct(v,50)-pct(o,50))/1000.0))
print("=" * 78)
