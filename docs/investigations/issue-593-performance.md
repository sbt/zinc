# Issue #593 implementation and performance evidence

Status: implementation underway; baseline recorded; candidate acceptance comparison pending.

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

Baseline A completed successfully at revision `60303402e3afe1f9062440dbfca53e3ec2bf16fc`.
All 12 targeted cases contain three independent forks with eight measured iterations each;
JMH reports final heap flags `-Xms2g -Xmx2g`. Contract suite: 6/6 passing.
Raw JSON: `baseline-A.json`; exact command and support hashes in the manifest.
This first-order baseline alone cannot establish an acceptance verdict.

2026-09-10: bounded-work guards fail on the original scan (550 visits in the small
warm-query case; 480,800 visits for concurrent readers), then all 11 focused tests
pass with the lazy index. A temporary forward iterator (last definition wins) fails
four precedence/lifetime tests and is restored. Logs: `guard-original.log`,
`guard-indexed.log`, `guard-last-wins.log`.

The index is private, immutable and lazy; it derives from the existing overridable
`analyses` accessor. Reverse traversal preserves the earliest analysis for duplicate
binary names. Provider loading, external-hook dispatch and analyzed-class fallback
are unchanged.

Compiler integration: 16/16 tests passed, including the new comparison of downstream
API hashes and product-class relations after incremental and clean compilation.
Command: `sbt --server --batch 'compilerBridge2_13/compile'
'zinc/testOnly sbt.internal.inc.LookupAnalysisSpec *MultiProjectIncrementalSpec *BinaryDepSpec'
'zinc/compile'`. Log: `integration-bridge213.log`. Initial attempts lacked the
Scala 2.13.16 bridge selected by `BaseCompilerSpec`; building Scala 2.12 was insufficient.
The symlink/JAR hypothesis was disproved by direct packaging inspection. No bridge
source or build configuration was changed. Fresh checkouts must compile the selected
bridge before these integration suites.

Hardware: Apple M3 Pro, 36 GiB physical memory.

Measurement tooling: comparison self-tests pass for known ratios, independent fork counts,
missing variants and mismatched units. A baseline-only manifest correctly yields inconclusive
cases. `LookupAnalysisMemoryProbe` loads external JOL 0.17 and requires successful JVM
Instrumentation. It compares total reachable sizes of the same fixture/lookup roots before
and after initialization, and after 10,000 and 110,000 distinct misses. This is additional
reachable footprint under controlled shared roots, not a general dominator retained-size
claim. Object sizes are measured by Instrumentation; JOL's warning concerns guessed addresses,
which this probe never subtracts or otherwise uses. Alignment: 8 bytes; compressed references.

Memory smoke (small, 200 unique names): 6,280 additional bytes, unchanged after both
miss batches. This is an initial probe check; all-scenario paired measurements remain pending.
Log: `memory-smoke-project.log`; data: `memory-smoke.json`. Run from the concrete project:

```sh
sbt --server --batch 'project zincBenchmarks' \
  'set Test / javaOptions ++= Seq("-Djdk.attach.allowAttachSelf=true", "-Djol.skipHotspotSAAttach=true", "-Xms2g", "-Xmx2g")' \
  'Test/runMain sbt.internal.inc.LookupAnalysisMemoryProbe --jol-jar /Users/iceo/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/openjdk/jol/jol-core/0.17/jol-core-0.17.jar --scenario small --output /private/tmp/zinc-593-results-20260909/memory-smoke.json'
```

The initial `set zincBenchmarks / ...` command failed because that build symbol is a
ProjectMatrix; selecting `project zincBenchmarks` before `set Test / ...` resolves it.
No persistent build settings or dependencies changed.
