# Investigation: Zinc issue #593

Investigated 2026-09-06 at Zinc commit `b4695540f`, using JDK 17.0.20.1 and Scala 3.9.0.

[Issue #593](https://github.com/sbt/zinc/issues/593) remains open. The reported linear scan
still exists. More precisely, initialization visits the entire classpath once, while each
subsequent lookup scans the entries that supplied an analysis. These are different costs.

This investigation changes no production source. A diagnostic harness, temporary index
variants, retrieved integration sources, and output logs live in
[target/issue-593](/Users/iceo/Projects/zinc/target/issue-593/run.py), which is ignored by Git.
The original reporter's build/profile is not available in the issue, so these results establish
the mechanism, not the impact on that original build or on whole-build compilation time.

## Current behavior

Let N be classpath entries, A be entries returning an analysis, Q be class-name queries, and
B be the total binary-class entries recorded across those analyses.

- [LookupImpl.scala:26](/Users/iceo/Projects/zinc/zinc/src/main/scala/sbt/internal/inc/LookupImpl.scala:26)
  lazily builds `analyses` by calling `perClasspathEntryLookup.analysis` for all N entries.
  This happens once per `LookupImpl`, with the provider's loading/deserialization costs added.
  Repeated classpath entries can contribute repeated analyses; this is not a distinct set.
- [LookupImpl.scala:47](/Users/iceo/Projects/zinc/zinc/src/main/scala/sbt/internal/inc/LookupImpl.scala:47)
  uses `analyses.find` and tests `relations.productClassName._2s.contains(binaryClassName)`.
  A miss or last-entry hit examines A analyses; a first-entry hit examines one. There is no
  memoization of lookup results. With inexpensive membership checks, worst-case repeated
  lookup work is O(Q × A), after initialization. A ≤ N, so the issue title gives a valid
  upper bound but obscures the distinction between libraries and analyzed upstream projects.
- The callback path remains live: `AnalysisCallback.binaryDependency` calls
  `externalDependency`, which invokes the supplied `externalAPI` function in
  [Incremental.scala:802](/Users/iceo/Projects/zinc/internal/zinc-core/src/main/scala/sbt/internal/inc/Incremental.scala:802).
  That function is wired to `lookup.lookupAnalyzedClass`; the default implementation in
  [Lookup.scala:51](/Users/iceo/Projects/zinc/internal/zinc-core/src/main/scala/sbt/internal/inc/Lookup.scala:51)
  calls `lookupAnalysis` before resolving the source class and API.
- A compatible `ExternalLookup` can bypass that default API lookup. It does not override every
  direct use of `lookupAnalysis`: unchanged-classpath library checks also call it in
  [IncrementalCommon.scala:799](/Users/iceo/Projects/zinc/internal/zinc-core/src/main/scala/sbt/internal/inc/IncrementalCommon.scala:799).
- `lookupOnClasspath`/`Locate.entry` is a separate classpath scan that invokes `definesClass`.
  A package-to-JAR index could target that path, but it does not directly remove the scan of
  analyses in `lookupAnalysis`.

## Reproduction and experiments

The harness recompiles the repository's actual `LookupImpl.scala` against the local build,
constructs real `Analysis` and `Relations` values, and wraps analyses with counting proxies.
The compiler configuration uses test doubles; no Scala sources are compiled by the fixture.
Each analysis contains one binary class. This isolates the reported lookup operation.

If the dependency export is missing, prepare it with:

```sh
sbt --server --batch 'export zinc/Compile/fullClasspath'
```

The deterministic performance assertion is intentionally red on current Zinc:

```sh
python3 target/issue-593/run.py --compile --assert-indexed
```

Observed output, reproduced in multiple JVMs:

| N | A | Initial provider calls | Later provider calls | First hit visits | Last hit visits | Miss visits | Visits for 100 repeated misses |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 2 | 2 | 2 | 0 | 1 | 2 | 2 | 200 |
| 20 | 20 | 20 | 0 | 1 | 20 | 20 | 2,000 |
| 200 | 200 | 200 | 0 | 1 | 200 | 200 | 20,000 |
| 2,000 | 2,000 | 2,000 | 0 | 1 | 2,000 | 2,000 | 200,000 |
| 2,000 | 2 | 2,000 | 0 | 1 | 2 | 2 | 200 |
| 2,000 | 0 | 2,000 | 0 | 0 | 0 | 0 | 0 |

```text
AssertionError: warm missing-class lookup visited 2000 upstream analyses; budget = 2
```

The constant budget is a diagnostic check for indexed lookup, not an existing Zinc SLA.
All scenarios made zero `definesClass` calls. No analysis provider calls occurred after
initialization. These observations isolate the repeated analysis scan from provider I/O and
JAR inspection.

Three explanations were evaluated: repeated analysis scans, provider loading at initialization,
and separate JAR/class lookup. The scan is reproduced directly; provider behavior depends on
the integration; JAR lookup does not participate in this operation.

A temporary variant builds a lazy binary-name-to-analysis map, preserving the first analysis
for duplicate names. The same assertion passes, with zero analysis visits for every warm
lookup and unchanged initial provider-call counts:

```sh
python3 target/issue-593/run.py --compile --indexed --assert-indexed
```

The optional `--timing` mode removes counting proxies and measures warmed calls against real
analyses: 20,000 warmup calls, five samples of 50,000 calls per query, median reported.
Two fresh-JVM baseline/index comparisons measured a missing-class lookup with A = 2,000 at
about **14.8–16.5 microseconds** for the current implementation and **6–16 nanoseconds** for
the index. This is a deliberately small synthetic dataset, with hot repeated query strings.
It excludes index construction, realistic analysis sizes, allocation profiling, compiler
callbacks, and end-to-end build effects. It is not a JMH result or a promised build speedup.

## Previous PR: performance uncertainty and a correctness problem

[PR #1279](https://github.com/sbt/zinc/pull/1279) was closed without merging. Its local
Scalac/Shapeless benchmarks showed changes within the reported error margins; maintainers
noted that a larger classpath might behave differently. That result neither establishes a
useful build-level speedup nor disproves the scan demonstrated here.

Its proposed `analyses.flatMap(...).toMap` also changes duplicate-name behavior: later pairs
replace earlier pairs, while the current `find` returns the first matching analysis. The
harness verifies this independently using two analyses defining `duplicate.C`:

```sh
python3 target/issue-593/run.py --shadowing
python3 target/issue-593/run.py --indexed --shadowing
python3 target/issue-593/run.py --compile --last-wins --shadowing
```

Current Zinc and the first-preserving index pass. The last-wins variant equivalent to the
PR's construction fails with `first classpath analysis must win`. This establishes a change
to lookup semantics; it is not an end-to-end compilation reproduction for duplicate classes.

[PR #864](https://github.com/sbt/zinc/pull/864), also closed without merging, addressed caching
JAR class definitions. It targets the other lookup path, rather than this analysis scan.

## Build-tool audit

The issue's 2024 discussion described sbt, Bloop, and Mill as map-based providers. The
inspected source revisions show that a map alone does not establish an in-memory analysis
lookup:

| Integration and inspected revision | Analysis provider | Implication |
|---|---|---|
| [sbt Defaults.scala](https://github.com/sbt/sbt/blob/d4507648a57478f15ec2e41ff60b11f97187282d/main/src/main/scala/sbt/Defaults.scala#L2459) | Builds `Map[VirtualFile, CompileAnalysis]` before creating the provider; `analysis` calls `get`. | No deserialization inside this provider method; upstream preparation may still load analyses. |
| [BloopClasspathEntryLookup.scala](https://github.com/scalacenter/bloop/blob/c4477fc019c151cd886b630906b0e886de8ad069/backend/src/main/scala/bloop/BloopClasspathEntryLookup.scala#L22) | Looks up an in-memory `PreviousResult`, then returns its analysis. | No deserialization inside this method. Its `definesClass` caching is separate. |
| [Mill ZincWorker.scala](https://github.com/com-lihaoyi/mill/blob/aa5eb1c948bcbc975057748a14a6e3c772655dc1/libs/javalib/worker/src/mill/javalib/zinc/ZincWorker.scala#L411) | Maps output directories to analysis-file paths; a hit calls `fileAnalysisStore(zincPath).get()`. The helper constructs `ConsistentFileAnalysisStore.binary`. | Provider hits involve analysis-file loading, rather than just returning a cached analysis object. |

[BloopLookup](https://github.com/scalacenter/bloop/blob/c4477fc019c151cd886b630906b0e886de8ad069/backend/src/main/scala/sbt/internal/inc/bloop/internal/BloopLookup.scala)
inherits `lookupAnalysis` unchanged.
[MillExternalLookup](https://github.com/com-lihaoyi/mill/blob/aa5eb1c948bcbc975057748a14a6e3c772655dc1/libs/javalib/worker/src/mill/javalib/zinc/MillExternalLookup.scala)
does not supply an analyzed-class fast path. The local
[ConsistentFileAnalysisStore.scala:89](/Users/iceo/Projects/zinc/internal/zinc-persist/src/main/scala/sbt/internal/inc/consistent/ConsistentFileAnalysisStore.scala:89)
opens and deserializes the file on `get`; the Mill helper does not add a cache wrapper.

This is a source audit, not a benchmark of these tools or a claim about every released
version. Maven, Gradle, IntelliJ, and other integrations were not audited. Mill's actual
loading time and potential reuse across compilation invocations remain unmeasured.

## Recommended next change

Keep two work items distinct:

1. **Repeated analysis lookup:** benchmark a lazy, immutable binary-name index in `LookupImpl`
   that preserves the first analysis. It costs O(B) construction work and map storage and
   should give expected constant-time warm lookups. An immutable map also accommodates the
   concurrent readers used by incremental change detection. Measure realistic B, A, query
   distributions, and first-use cost before choosing it over alternatives such as concurrent
   per-name memoization or an ordered package candidate index.
2. **Analysis loading:** profile the file-backed Mill provider and any other affected
   integration separately. `analyses` already caches results within one lookup instance;
   wrapping that provider with another same-instance cache would not remove the N initial
   calls. Reuse between invocations needs a defined invalidation/lifetime policy.

A public `allAnalysis` method is unnecessary to remove the repeated scan. It would address
enumeration instead and require API compatibility, ordering, and classpath-filtering semantics;
blindly consuming an unordered upstream map can change which analysis is selected.

Before a production patch, add focused tests for missing classes, first/last hits, duplicate
names, binary versus source names (including nested classes), laziness, and a fresh lookup
after upstream changes. Add a representative benchmark with many analyzed upstream projects,
many library-only entries, realistic classes per analysis, and construction plus steady-state
measurements. The existing whole-build PR benchmarks did not establish this workload.

Verification performed: current main build/classpath export succeeded; actual-source diagnostic
baseline failed as intended; indexed diagnostic passed; duplicate-name checks passed for
current/first-preserving behavior and failed for last-wins behavior. No production fix was
installed, and the full test suite was not run for this investigation.
