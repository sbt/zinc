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

## Measurement quality and initial valid comparison

Power-event audit (`power-events.json`) found that baseline A and candidate B crossed
system sleep. Baseline A also ran on battery. Their raw data remain in the manifest's
`excluded_measurements` with objective reasons. The uninterrupted candidate A and
baseline B retry form reverse-order block B; replacement block C runs baseline then
candidate. New runs inhibit idle sleep with `caffeinate -i`, record power state and
check sleep events after completion. No valid slow sample is omitted.

Baseline B initially failed before JMH: the contraband generator cached an empty output
list although all tracked generated sources existed. Preserving and invalidating only
that local `gen-api` cache restored 67 generated output records. Generation and test
compilation then passed with no tracked baseline source change. The failed attempt
and cached metadata are retained.

The initial valid comparison (`comparison-initial-valid.json`) passes five of six
targeted gates. Class-heavy lifecycle is inconclusive: mean ratio 0.7509, one-sided
95% upper bound 0.8975 versus required 0.80. Four additional paired blocks D/E/F/G
are predeclared for only this case, with three forks per variant per block and
alternating run order. All existing valid observations remain in the final comparison.

The analysis script also supports JMH 1.37 sample-time histograms (verified against
its JSONResultFormat source): samples are pooled within each fork, then forks remain
independent and equally weighted. A weighted-histogram self-check failed before
implementation and passes afterward.

The fixed additional sampling is complete. All six paired blocks are retained; the
class-heavy lifecycle mean ratio is 0.8853 with a one-sided 95% upper bound of 1.0758,
so this candidate does not establish the required 0.80 bound. Other five targeted gates
pass. Source-equivalent runs vary substantially; no valid slow samples were discarded.
`comparison-targeted-final.json` records this inconclusive outcome. Compiler acceptance
runs have not started because checkpoint C has not passed.

Next construction candidate: a locally built Java hash map, published through an
unmodifiable view without retaining any mutable alias. The current Scala-map candidate
allocates about 32.7 MiB for the class-heavy lifecycle. Measure whether avoiding its
trie construction and intermediate pairs reduces first-use cost while preserving the
same contract. Existing semantic/concurrency/integration tests remain the correctness
guards. Previous candidate timings will not be pooled with the new implementation.

## Revised construction measurements

The revised implementation passes all 16 focused and integration tests
(`hashmap-correctness.log` in the original results directory). Its evidence is isolated in
`/private/tmp/zinc-593-hashmap-20260911/manifest.json`, including the exact production patch
against `f695304ed`, source hashes, commands, and links to the prior candidate's evidence.

An exploratory class-heavy run (three forks per method, approved timing/heap settings)
averages 13.15 ms first-use, 10.43 ms lifecycle, and 83.0 microseconds per warm batch of
10,000 queries. Lifecycle allocation is 10.33 MiB/op, compared with about 32.7 MiB/op
for the previous candidate. This pilot is retained separately and is not pooled with
the acceptance runs. Fresh fixed paired blocks H1/H2 run in both orders.

Instrumented JOL measurements cover both revisions and all five scenarios with identical
probe sources. The original scan adds zero bytes after the already-loaded analyses.
The revised candidate's additional reachable footprint is:

| Scenario | Distinct names U | Additional bytes | Bytes per name |
|---|---:|---:|---:|
| empty | 0 | 80 | — |
| small | 200 | 8,544 | 42.72 |
| library-heavy | 200 | 8,544 | 42.72 |
| upstream-heavy | 100,000 | 4,248,672 | 42.49 |
| class-heavy | 200,000 | 8,497,248 | 42.49 |

For every scenario and both variants, initialized footprint is unchanged after 10,000
and 110,000 distinct misses. These are measurements with the same shared roots, not
unit-test byte budgets. Exact commands, tool hash, VM details, and raw outputs are in
the new manifest's `memory_runs` entries.

Fresh paired blocks H1/H2 are complete with no sleep interruptions. All six lookup gates
pass (`comparison-acceptance.json`, seed 593, 10,000 hierarchical bootstrap resamples).
Each row has six independent JVM forks per variant across both run orders. Time is
microseconds per 10,000-query operation, including initialization for lifecycle rows.

| Scenario / operation | Original | Candidate | Ratio | One-sided 95% upper | Limit |
|---|---:|---:|---:|---:|---:|
| class-heavy lifecycle | 35,627.34 | 11,272.10 | 0.3164 | 0.3471 | 0.80 |
| upstream-heavy lifecycle | 145,329.88 | 6,382.59 | 0.0439 | 0.0563 | 0.80 |
| small lifecycle | 226.50 | 59.91 | 0.2645 | 0.2913 | 1.05 |
| library-heavy lifecycle | 370.51 | 194.46 | 0.5248 | 0.5596 | 1.05 |
| class-heavy warm batch | 39,705.31 | 81.80 | 0.0021 | 0.0025 | 0.50 |
| upstream-heavy warm batch | 188,987.17 | 86.56 | 0.0005 | 0.0007 | 0.50 |

Bounds in the table are rounded upward; the JSON retains full precision. First-use,
query-shape, break-even, and compiler results must still be considered before completion.
No whole-build non-regression claim follows from these lookup measurements.

Tooling audit: `show zincBenchmarks/Test/discoveredMainClasses` reports the new memory
probe and `xsbt.GlobalBenchmarkSetup`; `Test/mainClass` is `None` with a multiple-main
warning. The `runBenchmarks` alias now names `Test/runMain xsbt.GlobalBenchmarkSetup`
explicitly, synchronized to both checkouts. The completed timings invoked `Jmh/run`
directly and never used this alias; benchmark sources, settings, and runtime production
sources are unchanged by this setup-selection fix.

The shortcut smoke passes: `sbt --server --batch '-Dbenchmark.pattern=-l' runBenchmarks`.
Here `-l` matches no setup project and asks JMH to list benchmarks, so the command verifies
explicit main selection, bridge packaging, JMH discovery, and temporary-directory cleanup
without cloning a workload or collecting timing data. Log: `benchmark-entrypoint-after.log`.
