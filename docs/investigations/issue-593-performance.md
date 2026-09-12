# Issue #593 implementation and performance evidence

Status: implementation and affected-project tests pass; all six lookup acceptance gates pass.
Query-pattern/query-count diagnostics and four whole-compiler gates remain pending.

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

The same paired runs quantify the startup tradeoff. These are independent first-use
measurements, including provider enumeration and index construction; they are not obtained
by subtracting warm timing from lifecycle timing.

| Scenario | Original first use (us) | Candidate first use (us) | Original allocation (MiB) | Candidate allocation (MiB) |
|---|---:|---:|---:|---:|
| small | 2.040 | 4.726 | 0.0078 | 0.0182 |
| library-heavy | 139.817 | 140.374 | 0.3710 | 0.3814 |
| upstream-heavy | 158.942 | 5,799.265 | 0.4452 | 5.7056 |
| class-heavy | 43.790 | 9,478.165 | 0.1097 | 10.2518 |

Few-query and first-hit-heavy workloads can pay an index cost without recovering it.
The remaining query-shape measurements and query-count sweep must quantify that boundary.

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

## Validation and remaining evidence

Full affected-project validation at `3a8a366de`: all 43 `zinc/testFull` tests and all 29
`zincCore/testFull` tests pass. `scalafmtCheckAll`, `scalafmtSbtCheck`, `headerCheck`, and
`Test/headerCheck` pass. The intentional malformed-source compiler diagnostic in the test
log is part of a passing test. Log: `final-checks.log`.

The configured `zinc/mimaReportBinaryIssues` cannot resolve
`org.scala-sbt:zinc_3:1.8.0` on either this candidate or the original baseline. This is
recorded separately in `mima-original-baseline.log`; release settings and filters are
unchanged. A scoped MiMa comparison against the actual original classes passes:

```sh
sbt --server --batch 'project zinc' \
  'set mimaPreviousClassfiles := Map(("org.scala-sbt" % "zinc_3" % "issue-593-baseline") -> file("/private/tmp/zinc-593-baseline/target/out/jvm/scala-3.9.0/zinc/classes"))' \
  mimaReportBinaryIssues
```

Log: `mima-local-baseline.log`. The manifest records different baseline/candidate
`LookupImpl.class` hashes, confirming distinct comparison inputs. This checks the scoped
change; it does not claim that the unresolved historical-release check passed.

After the lookup gates, two empty-state diagnostic runs started on battery. The power
mismatch was noticed before inspecting their timings; both raw runs are preserved under
`excluded_measurements` and will be recaptured under AC power. The runner now checks AC
at startup and completion and audits battery events as well as sleep. The user connected
power, but restricted `pmset` reports AC while the actual benchmark environment's `pmset`
reports battery and `ioreg` reports no external connection. Timings were paused until
that discrepancy was resolved. On 2026-09-12, the actual benchmark environment reports
AC power and charging; diagnostic measurements resumed with fresh `empty-ac` labels.
The accepted H1/H2 runs were completed earlier on AC and are unaffected.
Query-shape/break-even diagnostics and all four compiler gates remain open.

The four replacement empty-state runs completed on AC in both orders. Their comparison
(`comparison-empty.json`, six forks per variant, same bootstrap method) quantifies overhead
when there are no definitions to index. Times are microseconds per operation; lifecycle
and warm operations each contain 10,000 queries.

| Empty-state operation | Original | Candidate | Ratio | One-sided 95% upper |
|---|---:|---:|---:|---:|
| first use | 0.236 | 0.294 | 1.2474 | 1.4381 |
| lifecycle | 20.786 | 36.804 | 1.7707 | 2.0474 |
| warm batch | 10.597 | 19.782 | 1.8667 | 2.3295 |

These are diagnostic regressions, not additional acceptance gates. The absolute lifecycle
increase is about 16 microseconds per 10,000 queries. The first query-pattern pair also
completed successfully. The reversed-order candidate run switched to battery at
2026-09-12 16:59:50 +0200, near its end. It is preserved in `excluded_measurements`, with
the exclusion decision made before inspecting its timing results. The queue stopped at
that run boundary; `run-diagnostics-resume.py` recaptures that entire run with a fresh label,
then completes the reversed baseline and query-count sweep. Completed valid runs are retained.

The replacement candidate and reversed baseline query-pattern runs subsequently completed
on AC without sleep. All first/last/miss cases now have six independent forks per variant
across both orders (`comparison-query-patterns.json`, 33 completed cases including prior
mixed/empty results). The six acceptance gates remain passing. The diagnostic lifecycle
results below include initialization and 10,000 queries, in microseconds per operation.

| Scenario / query pattern | Original | Candidate | Ratio | One-sided 95% upper |
|---|---:|---:|---:|---:|
| small / first hit | 157.425 | 60.253 | 0.3827 | 0.3900 |
| small / last hit | 256.570 | 59.650 | 0.2325 | 0.2746 |
| small / miss | 110.532 | 39.703 | 0.3592 | 0.3649 |
| upstream-heavy / first hit | 319.931 | 5,139.196 | 16.0634 | 18.7551 |
| upstream-heavy / last hit | 215,562.556 | 5,168.937 | 0.0240 | 0.0268 |
| upstream-heavy / miss | 199,283.957 | 5,616.339 | 0.0282 | 0.0336 |

The large graph's first-hit workload is a clear regression: about 0.32 ms becomes 5.14 ms,
because the original scan can return from the first analysis while the candidate indexes
all definitions. Its one-sided lower ratio bound is 13.4174. This workload differs from the
approved mixed-query acceptance stream and is retained as a practical limitation, without
changing thresholds. Large-graph last hits and misses save substantially more scanning work.

The first query-count sweep then crossed system sleep, including a 919-second maintenance
sleep. The full `baseline-sweep-1` run is preserved and excluded before examining its timing
results (`sleep-condition-decision-20260912.txt`). No query-count sweep is accepted yet.
The queue is paused; `run-sweep-resume.py` uses a new baseline label and resumes only the
four sweep invocations, preserving every completed valid query-pattern measurement.

The original workspace's unrelated compiler-bridge diff is unchanged (SHA-256
`0dc459f403aae9b67ace276b079ddab89e096c39a393aaaac11401257b91d3cb`). Other new unrelated
workspace files and edits were observed and left untouched.

Compiler fixture preparation completed while timing was paused. The existing setup helper
prepared Scala library at `31539736462078b1da615880ef11890a6538b45e` (569 sources) and
Shapeless coreJVM at `62611554399e0d04466da95591253706b2d3020d` (83 sources). Both Git
revisions and every recorded source/classpath path were verified. The manifest retains
commands, source-input hashes, build metadata, and `scalac-setup-early.log` /
`shapeless-setup-early.log`. Preparation is not a compiler timing or an acceptance result.
