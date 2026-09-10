#!/usr/bin/env python3
# Zinc - The incremental compiler for Scala.
# Copyright Scala Center, Lightbend, and Mark Harrah
# Licensed under Apache License 2.0
# SPDX-License-Identifier: Apache-2.0
# See the NOTICE file distributed with this work for additional ownership information.

"""Compare JMH JSON from manifest runs using independent JVM forks, never iterations.

Each run has variant (baseline/candidate), block (paired run block), order, json,
and sources (repository-relative path -> SHA-256). Paths may be absolute or relative
to the manifest. All support sources must match; LookupImpl.scala may differ.
Results retain JMH units: lookup batches are not individual lookups.
"""

import argparse
import json
import math
import random
import statistics
from collections import defaultdict
from pathlib import Path


def fork_means(metric):
    """JMH rawData has one row per fork, with iteration summaries inside it."""
    rows = metric.get("rawData", [])
    if not rows or any(not row for row in rows):
        raise ValueError("missing independent fork data")
    if any(not isinstance(x, (float, int)) or not math.isfinite(x) or x <= 0
           for row in rows for x in row):
        raise ValueError("nonpositive, nonnumeric, or nonfinite timing data")
    return [statistics.mean(row) for row in rows]


def threshold(benchmark, params):
    method = benchmark.rsplit(".", 1)[-1]
    if params.get("queryMix") != "mixed" or params.get("queryCount") != "10000":
        return None
    scenario = params.get("scenario")
    if scenario in ("upstream-heavy", "class-heavy"):
        return {"warmBatch": 0.5, "lifecycle": 0.8}.get(method)
    if scenario in ("small", "library-heavy") and method == "lifecycle":
        return 1.05
    return None


def ratio_bounds(blocks, seed, resamples):
    rng = random.Random(seed)
    pairs = list(blocks.values())
    mean = statistics.mean
    base = mean(mean(p["baseline"]) for p in pairs)
    candidate = mean(mean(p["candidate"]) for p in pairs)
    samples = []
    for _ in range(resamples):
        selected = rng.choices(pairs, k=len(pairs))
        sampled = {}
        for variant in ("baseline", "candidate"):
            sampled[variant] = mean(
                mean(rng.choices(p[variant], k=len(p[variant]))) for p in selected
            )
        samples.append(sampled["candidate"] / sampled["baseline"])
    samples.sort()
    return {
        "baseline_mean": base, "candidate_mean": candidate,
        "ratio": candidate / base,
        "lower_95_one_sided": samples[int(0.05 * (len(samples) - 1))],
        "upper_95_one_sided": samples[math.ceil(0.95 * (len(samples) - 1))],
    }


def compare(manifest, directory, seed=593, resamples=10000):
    groups = defaultdict(lambda: defaultdict(dict))
    errors = defaultdict(list)
    orders = defaultdict(dict)
    supports = defaultdict(dict)
    run_errors = []
    for run in manifest["runs"]:
        variant, block = run["variant"], run["block"]
        if variant not in ("baseline", "candidate"):
            raise ValueError(f"unknown variant: {variant}")
        orders[block][variant] = run["order"]
        support = {p: h for p, h in run["sources"].items()
                   if not p.endswith("/LookupImpl.scala")}
        supports[block][variant] = support
        try:
            records = json.loads((directory / run["json"]).read_text())
        except (OSError, ValueError) as error:
            run_errors.append(f"{block}/{variant}: {error}")
            continue
        for record in records:
            metric = record["primaryMetric"]
            params = {k: str(v) for k, v in record.get("params", {}).items()}
            key = (record["benchmark"], record["mode"], metric["scoreUnit"],
                   tuple(sorted(params.items())))
            try:
                values = fork_means(metric)
                if len(values) != record["forks"]:
                    raise ValueError("fork count differs from JMH header")
                if variant in groups[key][block]:
                    raise ValueError("duplicate case in paired run block")
                groups[key][block][variant] = values
            except ValueError as error:
                errors[key].append(f"{block}/{variant}: {error}")

    results = []
    for key in sorted(set(groups) | set(errors)):
        benchmark, mode, unit, params_tuple = key
        params = dict(params_tuple)
        blocks = groups[key]
        reasons = list(errors[key]) + run_errors
        seen_orders = set()
        all_support = []
        for block, pair in blocks.items():
            if set(pair) != {"baseline", "candidate"}:
                reasons.append(f"{block}: missing variant (check dimensions/modes/units)")
                continue
            if any(len(values) < 3 for values in pair.values()):
                reasons.append(f"{block}: fewer than three independent forks per variant")
            if orders[block]["baseline"] == orders[block]["candidate"]:
                reasons.append(f"{block}: ambiguous run order")
            seen_orders.add(orders[block]["baseline"] < orders[block]["candidate"])
            all_support.extend(supports[block].values())
        if seen_orders != {False, True}:
            reasons.append("before/after and reversed-order blocks are both required")
        if all_support and any(s != all_support[0] for s in all_support):
            reasons.append("benchmark support source hashes differ")
        limit = threshold(benchmark, params)
        result = {"benchmark": benchmark, "mode": mode, "unit": unit, "params": params,
                  "threshold": limit, "forks": {b: {v: len(x) for v, x in p.items()}
                                                for b, p in blocks.items()}}
        if reasons:
            result.update(status="inconclusive", reasons=reasons)
        else:
            result.update(ratio_bounds(blocks, seed, resamples))
            if limit is None:
                status = "diagnostic"
            elif result["upper_95_one_sided"] <= limit:
                status = "pass"
            elif result["lower_95_one_sided"] > limit:
                status = "fail"
            else:
                status = "inconclusive"
            result["status"] = status
        results.append(result)
    return {"seed": seed, "resamples": resamples,
            "sampling_unit": "fork mean, nested within paired run blocks",
            "run_errors": run_errors, "cases": results}


def self_test():
    import tempfile
    assert fork_means({"rawData": [[1, 3], [4, 6], [7, 9]]}) == [2, 5, 8]
    for ratio in (1.0, 1.2):
        blocks = {b: {"baseline": [10.0] * 3, "candidate": [10.0 * ratio] * 3}
                  for b in ("A", "B")}
        result = ratio_bounds(blocks, 593, 100)
        assert result["ratio"] == ratio
        assert result["upper_95_one_sided"] == ratio
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        manifest = {"runs": []}
        for block in ("A", "B"):
            for variant in ("baseline", "candidate"):
                path = root / f"{block}-{variant}.json"
                path.write_text(json.dumps([{
                    "benchmark": "LookupAnalysisBenchmark.lifecycle", "mode": "avgt",
                    "params": {"scenario": "small", "queryMix": "mixed", "queryCount": "10000"},
                    "forks": 3, "primaryMetric": {"scoreUnit": "us/op", "rawData": [[10] * 8] * 3}
                }]))
                manifest["runs"].append({"block": block, "variant": variant,
                    "order": int((block == "A") == (variant == "candidate")),
                    "json": str(path), "sources": {"Fixture.scala": "same"}})
        result = compare(manifest, root, resamples=100)["cases"][0]
        assert result["status"] == "pass"
        assert result["forks"]["A"]["baseline"] == 3  # Not 24 iterations.
        only_base = {"runs": [r for r in manifest["runs"] if r["variant"] == "baseline"]}
        assert compare(only_base, root, resamples=100)["cases"][0]["status"] == "inconclusive"
        path = Path(manifest["runs"][1]["json"])
        data = json.loads(path.read_text()); data[0]["primaryMetric"]["scoreUnit"] = "ns/op"
        path.write_text(json.dumps(data))
        assert all(c["status"] == "inconclusive"
                   for c in compare(manifest, root, resamples=100)["cases"])
    print("PASS: ratio bounds, independent forks, missing variants and mismatched units")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--seed", type=int, default=593)
    parser.add_argument("--resamples", type=int, default=10000)
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if args.manifest is None or args.output is None or args.resamples < 100:
        parser.error("provide --manifest, --output and at least 100 resamples")
    result = compare(json.loads(args.manifest.read_text()), args.manifest.parent,
                     args.seed, args.resamples)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    for case in result["cases"]:
        print(case["benchmark"].rsplit(".", 1)[-1], case["params"], case["status"],
              case.get("ratio"), case.get("upper_95_one_sided"))


if __name__ == "__main__":
    main()
