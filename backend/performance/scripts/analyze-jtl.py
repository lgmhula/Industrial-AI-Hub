#!/usr/bin/env python3
"""Day 102 — JMeter JTL 结果分析器（不依赖 pandas，仅标准库）。

用法:
    python3 analyze-jtl.py results/day102-<ts>.jtl [--json]

输出每个 label 的：样本数 / HTTP 状态码分布 / 成功率 / 吞吐 / 延迟分位（ms）。
JTL 为 CSV 格式（JMeter -l 默认），关键列：
    timeStamp,elapsed,label,responseCode,responseMessage,threadName,...,success
"""
import csv
import json
import statistics
import sys
from collections import Counter, defaultdict


def percentile(sorted_values, pct):
    if not sorted_values:
        return 0.0
    if len(sorted_values) == 1:
        return float(sorted_values[0])
    k = (len(sorted_values) - 1) * (pct / 100.0)
    lo = int(k)
    hi = min(lo + 1, len(sorted_values) - 1)
    return float(sorted_values[lo] + (sorted_values[hi] - sorted_values[lo]) * (k - lo))


def analyze(path):
    by_label = defaultdict(lambda: {
        "elapsed": [], "codes": Counter(), "ts": [], "success": Counter()
    })
    with open(path, newline="", encoding="utf-8", errors="replace") as fh:
        for row in csv.DictReader(fh):
            label = row.get("label", "?")
            rec = by_label[label]
            try:
                rec["elapsed"].append(float(row.get("elapsed") or 0))
            except ValueError:
                continue
            rec["codes"][row.get("responseCode", "?")] += 1
            rec["success"][str(row.get("success", "")).lower()] += 1
            try:
                rec["ts"].append(int(row.get("timeStamp") or 0))
            except ValueError:
                pass

    out = {}
    for label, rec in by_label.items():
        n = len(rec["elapsed"])
        if n == 0:
            continue
        srt = sorted(rec["elapsed"])
        span = (max(rec["ts"]) - min(rec["ts"])) / 1000.0 if rec["ts"] else 0.0
        codes = dict(rec["codes"])
        ok = sum(c for code, c in rec["codes"].items() if code.startswith("2"))
        out[label] = {
            "samples": n,
            "codes": codes,
            "ok_2xx": ok,
            "ok_pct": round(ok * 100.0 / n, 2),
            "span_s": round(span, 1),
            "throughput_per_s": round(n / span, 1) if span else None,
            "ms": {
                "min": min(srt),
                "avg": round(statistics.fmean(srt), 1),
                "p50": round(percentile(srt, 50), 1),
                "p90": round(percentile(srt, 90), 1),
                "p95": round(percentile(srt, 95), 1),
                "p99": round(percentile(srt, 99), 1),
                "max": max(srt),
            },
        }
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        print(__doc__)
        return 2
    result = analyze(args[0])
    if "--json" in sys.argv:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    print(f"{'label':38} {'n':>7} {'2xx%':>6} {'span':>6} {'req/s':>8} "
          f"{'avg':>6} {'p50':>5} {'p95':>6} {'p99':>6} {'max':>6}  codes")
    for label, r in sorted(result.items()):
        m = r["ms"]
        codes = ",".join(f"{k}:{v}" for k, v in sorted(r["codes"].items()))
        print(f"{label:38} {r['samples']:>7} {r['ok_pct']:>6} {r['span_s']:>6} "
              f"{(r['throughput_per_s'] or 0):>8} {m['avg']:>6} {m['p50']:>5} "
              f"{m['p95']:>6} {m['p99']:>6} {m['max']:>6}  {codes}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
