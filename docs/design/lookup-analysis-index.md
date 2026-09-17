# Spec: Faster analysis lookup for Zinc #593

Status: **Approved — Specify, Plan and Tasks phases complete.** The user approved the
requirements, thresholds and implementation on 2026-09-09.
Created 2026-09-09 against `f4a48b237`.

Final campaign assessment (2026-09-14): implementation verified; nine performance gates pass,
cold Shapeless remains inconclusive (upper ratio 1.088252 > 1.05). Overall acceptance remains
incomplete; the candidate is not ready to ship. See the [final report](../investigations/issue-593-performance.md).

## Objective

Reduce the repeated search cost of `LookupImpl.lookupAnalysis` for builds with many analyzed
upstream projects, while returning exactly the same analyses and preserving incremental
compilation behavior.

The users are Scala and Java developers whose build tools invoke Zinc with large upstream
dependency graphs. The agreed performance bar is **lookup improvement plus no material
whole-build regression**; a measurable whole-build speedup is welcome but not required.

The [investigation](../investigations/issue-593.md) reproduced the current behavior: with
2,000 upstream analyses, 100 missing-class queries visit 200,000 analyses. A temporary index
eliminated those repeated visits. It also demonstrated that the construction proposed in
[PR #1279](https://github.com/sbt/zinc/pull/1279) changes duplicate-name precedence.
The investigation's timings exclude index construction and realistic analysis sizes, so
they do not satisfy this spec's performance criteria. The relevant lookup source is unchanged
between the investigation revision and this draft.

This is one capability: selecting an upstream analysis efficiently. Benchmarks and regression
tests validate that capability; they are not separate modules. Mill's analysis-file loading,
JAR/package lookup, and public provider enumeration APIs are outside this scope.

## Behavioral contract

Let N be classpath entries, A be entries that provide an analysis, and B be the total binary
class-name entries across those analyses. Repeated analysis objects and names contribute to
these counts; they are not assumed distinct.

| Requirement | Observable behavior |
|---|---|
| Exact result | Return the same analysis instance as the existing ordered `analyses.find` for every binary name, or `None` when absent. |
| Duplicate names | The first analysis in classpath-derived order wins. Reversing that order reverses the winner. Repeated identical entries preserve behavior. |
| Binary names | Match `relations.productClassName._2s`, including nested classes and object/module binary names; do not substitute source/API names or package prefixes. |
| API fallback | If the selected analysis lacks a corresponding API, preserve the existing `lookupAnalyzedClass` result. Do not silently select a later analysis with an API. |
| Laziness | Constructing `LookupImpl` must not load analyses or build the index. Reading `analyses` retains its current lazy behavior and does not itself require indexing. |
| Provider calls | On first evaluation of `analyses`, preserve one `analysis(entry)` call per classpath position, including duplicate positions. Subsequent queries reuse that vector. |
| External hooks | Preserve successful and negative external fast-path answers without forcing analysis/index initialization. An API without provenance still uses the existing fallback. |
| Concurrent readers | Concurrent first use and later reads return correct results without partial publication or duplicate successful initialization. |
| Lifetime | Derived state belongs to one `LookupImpl`. A new instance observes its own analysis snapshot and classpath order, including changes after earlier hits or misses. |
| Compatibility | Preserve public method signatures, `analyses` ordering, its use by subclasses, persistence formats, and classpath-provider contracts. |

Once the index is initialized, both hits and misses must avoid scanning the analysis vector,
including previously unseen names. Querying arbitrary missing names must not grow retained
state with the number of queries. No JAR inspection or new file reads belong in this path.

## Proposed approach and tradeoffs

Use a private, per-instance, lazily initialized immutable map from binary class name to the
first analysis defining it, derived from the existing `analyses` accessor. The construction
algorithm must preserve first-match precedence explicitly. A forward traversal followed by
plain `toMap` does not preserve it.

Construction refinement (2026-09-11): the initial Scala immutable-map builder did not
establish the class-heavy lifecycle gate after six paired blocks. Evaluate a method-local
Java `HashMap` populated in reverse analysis order and published through
`Collections.unmodifiableMap`. The mutable backing reference never escapes initialization
and is never retained separately, so published contents remain immutable. This preserves
the approved contract and thresholds while reducing construction allocation; it introduces
no dependency or adaptive lookup policy. Keep the initial candidate's evidence separate.

Expected costs are O(B) index construction, O(U) additional entries for U distinct binary
names, and expected constant-time warm lookups. Existing O(N) provider enumeration remains.
The index references existing names and analyses; it must not clone analysis graphs or retain
queried misses. Index allocation can outweigh saved work for small builds or few queries;
that tradeoff is part of acceptance, not a reason to omit first-use measurements.

This is the candidate for evaluation, not a claim that it has already met the performance bar.
If the performance gates fail, retain the benchmark evidence, revise the design, and do not
ship the candidate. A public `allAnalysis` API, global cache, or heuristic switching policy
requires a scope/design revision before implementation.

## Tech stack

- Zinc main and test sources: Scala 3.9.0; sbt 2.0.8; JDK 17 for the initial comparison.
- Tests: ScalaTest 3.2.20, using existing `UnitSpec` and `BaseCompilerSpec` fixtures.
- Benchmarks: existing `zincBenchmarks` project and sbt-jmh 0.4.8, with JMH GC profiling.
- Formatting: scalafmt 3.8.3, Scala 3 dialect, 100-column limit.
- No new production or test dependency is required by the proposed design.

## Commands

Run from the repository root, with the same JDK and JVM settings for both benchmark variants.
`LookupAnalysisSpec` and `LookupAnalysisBenchmark` below are proposed deliverables; their
commands become runnable after those files are implemented. This phase does not run tests
or benchmarks for a production change. Project IDs, `zincBenchmarks/Jmh/run`, and
`zinc/testFull` were confirmed by sbt project/task inspection on 2026-09-09.

```sh
cd /Users/iceo/Projects/zinc

# Confirm project IDs and build the affected project.
sbt --server --batch projects
sbt --server --batch 'zinc/compile' 'zinc/Test/compile'

# Focused new contract tests, then existing integration coverage and full affected suites.
sbt --server --batch 'zinc/testOnly sbt.internal.inc.LookupAnalysisSpec'
sbt --server --batch 'zinc/testOnly *MultiProjectIncrementalSpec *BinaryDepSpec'
sbt --server --batch 'zinc/testFull' 'zincCore/testFull'

# Formatting, headers, and binary compatibility.
sbt --server --batch scalafmtAll
sbt --server --batch scalafmtCheckAll scalafmtSbtCheck
sbt --server --batch headerCreate 'Test/headerCreate'
sbt --server --batch headerCheck 'Test/headerCheck' 'zinc/mimaReportBinaryIssues'

# Representative targeted measurements, repeated on baseline and candidate revisions.
# The output paths must differ to preserve both runs.
sbt --server --batch 'zincBenchmarks/Jmh/run -f 3 -wi 5 -i 8 -w 1s -r 1s -jvmArgsAppend "-Xms2g -Xmx2g" -prof gc -p scenario=small,library-heavy,upstream-heavy,class-heavy -p queryMix=mixed -rf json -rff /tmp/zinc-593-baseline.json .*LookupAnalysisBenchmark.*'
sbt --server --batch 'zincBenchmarks/Jmh/run -f 3 -wi 5 -i 8 -w 1s -r 1s -jvmArgsAppend "-Xms2g -Xmx2g" -prof gc -p scenario=small,library-heavy,upstream-heavy,class-heavy -p queryMix=mixed -rf json -rff /tmp/zinc-593-candidate.json .*LookupAnalysisBenchmark.*'

# Separate first-hit, last-hit, and miss measurements.
sbt --server --batch 'zincBenchmarks/Jmh/run -f 3 -wi 5 -i 8 -w 1s -r 1s -jvmArgsAppend "-Xms2g -Xmx2g" -prof gc -p scenario=small,upstream-heavy -p queryMix=first,last,miss -rf json -rff /tmp/zinc-593-query-shapes.json .*LookupAnalysisBenchmark.*'

# Existing compiler workloads: execute on both revisions on the same machine.
sbt --server --batch '-Dbenchmark.pattern=.*Shapeless.*' runBenchmarks
sbt --server --batch '-Dbenchmark.pattern=.*Scalac.*' runBenchmarks
```

The benchmark report must record the exact revision for each command, including the benchmark
fixtures shared by baseline and candidate. Recompile the original lookup for baseline runs;
do not substitute a hand-written approximation or reuse stale classes from the investigation.
The full repository CI reference is `bash bin/run-ci.sh`; run broader checks when changed
scope or relevant failures justify them.

## Project structure

| Path | Role |
|---|---|
| `zinc/src/main/scala/sbt/internal/inc/LookupImpl.scala` | Production lookup and private derived index. |
| `internal/zinc-core/src/main/scala/sbt/internal/inc/Lookup.scala` | Existing analyzed-class fallback contract; reference for tests. |
| `zinc/src/test/scala/sbt/internal/inc/LookupAnalysisSpec.scala` | New focused contract and bounded-work tests against the real lookup. |
| `zinc/src/test/scala/sbt/inc/MultiProjectIncrementalSpec.scala` | Existing compiler-driven shadowing and upstream-change coverage; extend where necessary. |
| `internal/zinc-benchmarks/src/test/scala/sbt/internal/inc/LookupAnalysisBenchmark.scala` | New lookup lifecycle benchmark, discovered through JMH. |
| `internal/zinc-benchmarks/src/test/scala/xsbt/` | Existing whole-compiler benchmarks and setup. |
| `docs/investigations/issue-593.md` | Prior evidence, limitations, and integration audit. |
| `docs/design/lookup-analysis-index.md` | This specification. |
| `docs/investigations/issue-593-performance.md` | Planned results, environment, memory measurements, and acceptance verdict. |

The diagnostic harness under `target/issue-593` is reference material only. Durable regression
tests and benchmark fixtures must work from a clean checkout without those ignored artifacts.
No new external benchmark project is required for the targeted JMH fixture.

## Code style

Preserve the surrounding explicit return types, braces, descriptive camelCase names, and
small methods. Add the standard Apache header to new source files. Explain the duplicate-name
ordering rule next to index construction; avoid comments that merely restate operations.

For example, the existing fallback in `Lookup.scala` shows the local style and behavior to
preserve:

```scala
for {
  analysis0 <- lookupAnalysis(binaryClassName)
  analysis = analysis0 match { case a: Analysis => a }
  className <- analysis.relations.productClassName.reverse(binaryClassName).headOption
  analyzedClass <- analysis.apis.internal.get(className)
} yield analyzedClass
```

Keep test names behavioral, such as `select the first analysis defining a binary class`.
Avoid global mutable caches, unnecessary abstractions, and changes to generated sources.

## Testing strategy

### Functional and bounded-work tests

Use real `Analysis`/`Relations` fixtures and the production `LookupImpl`; fake only external
configuration and providers. Assert all rows of the behavioral contract, including empty
classpath, no analyses, an empty analysis, first/middle/last hits, and misses. Test distinct
source and binary names and duplicate names with different API contents so incorrect analysis
selection cannot pass accidentally.

Compare returned analysis identity against an ordered reference search. Use deterministic
counters to show that construction stays lazy, provider calls occur only during the first
analysis load, and warm queries do not visit upstream analyses. Include at least A = 2 and
A = 2,000, repeated misses, and fresh miss names. No elapsed-time assertions belong in unit
tests. Use coordinated concurrent callers for initialization and verify results and provider
call counts; do not rely on sleeps or GC timing.

Run existing multi-project tests through real compiler callbacks to guard upstream invalidation
and shadowing. Extend that coverage if the new focused cases expose a gap. The acceptance
criterion is unchanged expected outputs and recompiled-source sets, not merely compilation
without an exception. Confirm an incremental upstream API change behaves like the equivalent
clean build. No new compiler-bridge implementation is part of this work.

### Performance workloads

Use these named scenarios rather than an unnecessarily large Cartesian product:

| Scenario | N | A | Binary classes per analysis | Purpose |
|---|---:|---:|---:|---|
| `empty` | 0 | 0 | 0 | Empty-state overhead. |
| `small` | 32 | 2 | 100 | Small analyzed dependency graph. |
| `library-heavy` | 2,000 | 2 | 100 | Vary classpath length without increasing analyses. |
| `upstream-heavy` | 2,000 | 1,000 | 100 | Many analyzed upstream entries. |
| `class-heavy` | 500 | 200 | 1,000 | Construction and storage cost at larger B. |

Benchmark first hits, last hits, misses, and a fixed-seed mixed stream of 50% uniformly
distributed hits and 50% misses. Include repeated and distinct query strings and consume
results with JMH's `Blackhole`. Define separate operations for an already initialized lookup,
a newly constructed lookup plus its first query, and a newly constructed lookup plus 10,000
queries. Fixture/analysis creation belongs in setup; lookup initialization and index creation
must remain inside the latter two measurements.

For the lifecycle measurement, also vary query count across 1, 10, 100, 1,000, 10,000, and
100,000 to establish a measured break-even range. These diagnostic points do not replace the
fixed 10,000-query acceptance case. Record their exact parameterized commands in the results.

Report time, normalized allocation, index construction allocation, and retained heap delta
for an initialized lookup relative to the same retained fixture graph. Use an available JVM
heap-analysis tool for retained size; allocation rate alone is not a retained-memory measure.
Document the measurement command and tool in the results file. Hold live index instance count
constant, report U and additional bytes per distinct name, and verify no retained per-query
state accumulates as missing-query count grows. Byte counts are measurements, not unit tests.

### Approved numeric acceptance gates

These thresholds operationalize the agreed performance bar and were approved on 2026-09-09:

1. For both `upstream-heavy` and `class-heavy`, warm mixed-query latency improves by at least
   **50%**, and new-instance-plus-10,000-query latency improves by at least **20%**.
2. For the existing hot and cold Scalac/Shapeless compiler benchmarks, candidate time per
   operation does not increase materially: the one-sided 95% upper confidence bound on the
   candidate/baseline ratio must be **≤ 1.05** for each benchmark. Use independent fork/run
   summaries as the sampling units. If uncertainty crosses that bound, evidence is insufficient
   to declare the gate passed; obtain better measurements or revise the candidate.
3. Report small-build, library-heavy, first-use, allocation, and retained-memory results even
   where they regress. Small-build and library-heavy 10,000-query lifecycle measurements must
   also satisfy the ≤ 1.05 ratio bound. A first-query cost increase is allowed if the lifecycle
   gates pass, and its absolute cost and measured break-even query count are disclosed.
4. Improvements must exceed measured run-to-run uncertainty, with at least three independent
   JVM forks per targeted case. Repeat the before/after comparison in reversed order; use the
   same fixtures, JDK, hardware, heap, and workload state. Retain raw results and failed attempts.

The original reporter's build is unavailable. These workloads supply reproducible evidence
for the mechanism and regression checks; the report must not present them as that original
build or promise a particular user-visible compilation speedup.

## Boundaries

- **Always:** Preserve ordered selection and laziness; keep state instance-local; test actual
  call paths; compare like-for-like benchmark runs; record evidence and limitations; respect
  existing unrelated edits; run relevant checks before committing an implementation.
- **Ask first:** Expand into provider I/O caching, `Locate`/JAR indexing, public interface or
  persistence changes, new dependencies, CI changes, global caches, or different acceptance
  thresholds. Routine implementation choices within this contract do not require reapproval.
- **Never:** Change duplicate precedence for speed; reuse index state across compilation
  configurations; mask test failures; claim whole-build speedups from isolated lookup timings;
  edit compiler bridges or generated `contraband-java` as part of this scoped change.

## Success criteria

- [x] All behavioral contracts are covered and pass against the optimized production path.
- [x] The bounded-work assertion distinguishes the original scan from the candidate without timing.
- [x] The duplicate-name test fails with the previous PR's last-wins construction.
- [x] Compiler-driven upstream-change and shadowing checks pass; relevant existing suites pass.
- [ ] Targeted and whole-compiler benchmark gates pass, with construction and memory documented.
- [x] Public APIs, persisted data, provider behavior, and external hooks remain compatible.
- [x] Tests, benchmarks, and the results report are reproducible without ignored investigation files.
- [x] Formatting and headers pass; local-baseline binary compatibility passes. Historical
  MiMa artifact resolution fails on both revisions and remains a documented limitation.

## Review and open questions

The user selected **lookup gain plus no material whole-build regression** on 2026-09-09.
The user subsequently approved this specification's complete contract and numeric thresholds.
There are no further product requirements blocking planning. Absolute construction/memory costs
and the index's measured break-even point are evidence to collect during implementation, not
facts assumed by this specification.

The [implementation plan](../../tasks/plan-issue-593.md) and
[task breakdown](../../tasks/todo-issue-593.md) track the approved plan and implementation.
Keep the approved spec in version control alongside the eventual change and link its acceptance
criteria from the PR. Do not overwrite existing plans or task lists for unrelated work.
