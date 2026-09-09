# Issue #593 implementation and performance evidence

Status: implementation underway; no acceptance performance measurements completed.

Base: `f4a48b2375e38089967d78ad8b480fba4f76ce7d`. Candidate checkout:
`/private/tmp/zinc-593-candidate`, branch `codex/issue-593-lookup-analysis`.
Baseline reserved at `/private/tmp/zinc-593-baseline`. Raw results and provenance:
`/private/tmp/zinc-593-results-20260909/manifest.json`.

The original workspace compiler-bridge diff is preserved separately and excluded.
Approved requirements: [spec](../design/lookup-analysis-index.md). Progress:
[tasks](../../tasks/todo-issue-593.md).

## Evidence

Measurements below will distinguish completed checks from pending gates. No speedup
or whole-build regression claim is made until the approved gates have been measured.

JDK: Homebrew OpenJDK 17.0.20.1+0, 64-bit Server VM. sbt 2.0.8; Scala 3.9.0.

2026-09-09: Original `LookupImpl` passes all 6 contract tests. Command:
`sbt --server --batch 'zinc/testOnly sbt.internal.inc.LookupAnalysisSpec'`.
Log: `contract-baseline.log` in the results directory. No production source changes.

JMH discovery and the six empty/small smoke cases pass (`benchmark-smoke.log`).
JMH 1.37 reports `us/op`: one operation is one first lookup, or one whole query batch.
Smoke timings use a 600 MiB heap and are not acceptance evidence.
