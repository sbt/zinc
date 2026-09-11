# Implementation plan: Faster analysis lookup for Zinc #593

Status: **Approved — Plan phase complete.** The user approved this plan on 2026-09-09.
The task breakdown was approved on 2026-09-09. Implementation is underway.

Spec: [lookup-analysis-index.md](../docs/design/lookup-analysis-index.md).
Evidence: [issue-593.md](../docs/investigations/issue-593.md).
Task breakdown: [todo-issue-593.md](todo-issue-593.md).

## Outcome and scope

Replace repeated ordered searches of upstream analyses with a lazy, immutable, instance-local
index that returns the same analysis object. Preserve duplicate precedence, binary-name
matching, lazy provider calls, external hooks, concurrent reads, and compilation invalidation.

This plan implements the approved candidate and evaluates it against the approved gates:
50% lower warm mixed-query latency and 20% lower 10,000-query lifecycle latency for the two
large-analysis scenarios; a one-sided 95% upper confidence bound of at most 1.05 for the
specified regression comparisons. Construction, allocation, and retained memory are reported.
An index that fails those gates does not ship.

Provider I/O caching, `Locate`/JAR indexing, public interfaces, compiler bridges, persistence,
and CI configuration are outside scope. The current unrelated edit to
`internal/compiler-bridge/src/main/scala/xsbt/LocateClassFile.scala` belongs to other work.

## Architecture decisions

### Keep the production change in LookupImpl

Add one private lazy binary-name-to-analysis map next to `lookupAnalysis`; make that method
query the map. Leave `analyses` and `lookupAnalyzedClass` unchanged. Build through the existing
`analyses` accessor so subclass overrides retain their meaning.

Use reverse traversal of the ordered analysis vector and each analysis's binary names:
later insertions overwrite earlier ones, so the earliest analysis in the original order wins.
Following the inconclusive six-block Scala-map comparison, evaluate a method-local Java
`HashMap` and publish only its `Collections.unmodifiableMap` view. The backing reference
does not escape initialization; no code can mutate the published contents. Avoid intermediate
pairs and a flattened vector of all B entries. A comment explains reversed order and ownership.
No externally mutable map or query cache is introduced. Archive the first candidate's evidence
and recapture comparisons for the changed construction path.

Keep this logic private rather than extracting a new public helper or adding an `allAnalysis`
provider method. Construction of the lookup and reads of `analyses` do not force the index;
an external fast-path answer likewise does not force it. Failed API resolution after selecting
an analysis must not trigger a search for another analysis.

### Share test data, not a second lookup algorithm

Introduce `LookupAnalysisFixture.scala` under `zinc/src/test/scala/sbt/internal/inc`, with
package-scoped helpers creating real `Analysis`, `Relations`, `MiniSetup`, and a usable
`CompileConfiguration`. Fake compiler/provider boundaries, not the lookup operation. Make
unsupported compiler operations fail clearly if a test unexpectedly calls them.

Separate fixture creation from `newLookup()` so tests and benchmarks can create fresh lookups
over the same immutable analysis graph. Expose optional atomic counters for provider calls
and analysis access to the tests; benchmark instances use ordinary, unwrapped analyses with
no counters or proxies on the timed path. The existing benchmark project already depends on
`zinc` test output, so it can reuse these helpers without a build dependency change.

The ordered reference search is an oracle in tests only. Benchmarks run the real production
implementation on a recorded baseline or candidate revision. Do not benchmark a handwritten
copy of the original scan.

### Keep performance evidence reproducible

Give `LookupAnalysisBenchmark` the approved named scenarios and query mixes. Generate fixed-seed
query arrays in setup. Use distinct source/binary names and enough different hit/miss strings
to avoid measuring only an interned hot string. Consume results through a `Blackhole`.

Keep three operations distinct:

| Operation | Outside timed region | Inside timed region |
|---|---|---|
| `warmBatch` | Fixture, lookup construction, first lookup, query generation | Fixed-size batch on the initialized lookup; report batch size and derived per-query cost. |
| `firstLookup` | Fixture/configuration and query generation | New `LookupImpl` plus its first lookup, including analysis enumeration and index creation. |
| `lifecycle` | Fixture/configuration and query generation | New `LookupImpl` plus the full query batch; default 10,000 queries. |

Use a `queryCount` parameter for the approved lifecycle sweep of 1 through 100,000 queries;
the acceptance case remains 10,000. Treat `empty` queries as misses. Precompute each query
stream's expected hit count and check it outside timing, so missing analyses or misplaced
classpaths cannot silently make a benchmark faster.

Store raw JMH JSON and heap measurements under a dedicated results directory. Preserve a
revision/command manifest and write the interpretation to
`docs/investigations/issue-593-performance.md`. Commit the fixture and report sources; raw
artifacts must be linked or accompanied by exact regeneration commands and hashes.

## Dependency graph and delivery order

```text
P1: Lookup contract and reusable fixture
  |
  v
P2: Runnable benchmark and original-code baseline
  |
  +-- Checkpoint A: trustworthy baseline
  |
  v
P3: Indexed lookup with a failing-then-passing bounded-work guard
  |
  v
P4: Lifetime, external-hook, concurrency, and compiler-path verification
  |
  +-- Checkpoint B: correctness
  |
  v
P5: Targeted performance and retained-memory comparison
  |
  +-- Checkpoint C: targeted performance gates
  |
  v
P6: Whole-compiler comparisons, required checks, and evidence report
  |
  +-- Checkpoint D: accepted or rejected candidate
```

These approved delivery stages are expanded into the separately reviewed
[task breakdown](todo-issue-593.md), with each task limited to roughly five files or fewer.
No stage is completed merely by writing its checklist.

| Stage | Deliverable and likely files | Verification checkpoint |
|---|---|---|
| P1 | `LookupAnalysisFixture.scala` and `LookupAnalysisSpec.scala`: existing selection, name mapping, duplicate precedence, empty inputs, provider laziness, and analysis identity. | Focused tests pass on the original lookup; reorder duplicate definitions and verify the selected identity changes. |
| P2 | `LookupAnalysisBenchmark.scala`, small fixture extensions, and baseline report/manifest. | JMH discovers and executes all three operations; representative dimensions and expected hit counts are verified. Record the original production revision and benchmark source hashes. |
| P3 | `LookupImpl.scala` plus bounded-work cases in `LookupAnalysisSpec.scala`. | The new guard fails on the scan, then passes on the index for A = 2 and A = 2,000, with repeated and previously unseen misses. Replacing first-match selection with last-wins must fail the precedence case. Restore all mutations before continuing. |
| P4 | Extend focused tests and `MultiProjectIncrementalSpec.scala` only where existing coverage is insufficient. | Coordinated concurrent first use, external positive/negative answers, provenance fallback, fresh instances after upstream changes, and compiler-driven invalidation all pass. |
| P5 | Benchmark measurements, a standalone memory measurement entry point if needed, and report updates. | Approved targeted gates pass with uncertainty reported; construct/first-use costs, query-count break-even range, allocation, and retained-size deltas are recorded. |
| P6 | Whole-compiler measurements and final report; no unrelated source edits. | All required regression bounds and code checks pass; every approved success criterion maps to evidence. |

### Checkpoint A: establish a trustworthy baseline

The original implementation must pass the semantic tests. Benchmark fixture counts and query
results must be correct, and timing must exclude fixture construction while including the
lookup lifecycle claimed by each operation. Run a short discovery/smoke pass before the
three-fork measurements. Save the baseline source revision; a missing tool or broken benchmark
runner is resolved before accepting performance evidence.

### Checkpoint B: prove the index preserves behavior

Add the bounded-work assertion before the production edit within P3; keep each finished slice
green rather than committing an unexplained failing suite. Check zero additional provider
calls and analysis visits after initialization, including fresh misses. Count provider calls
with atomics and coordinate readers with a barrier/latch; use bounded waits and guaranteed
executor shutdown instead of sleeps.

Test a subclass override of `analyses`, duplicate classpath positions, two analyses with
different APIs for the same binary name, and the case where the first definition has no API.
External hooks use `NoopExternalLookup`/`DefaultExternalHooks` fixtures and distinguish a
negative fast-path answer from an API with missing provenance that must fall back.

Run existing `MultiProjectIncrementalSpec` and `BinaryDepSpec`. Add an incremental-versus-clean
comparison where needed, asserting expected compiled units and exposed behavior rather than
raw class-file bytes containing incidental metadata. Do not equate a unit-level selection
test with proof of compiler-driven invalidation.

### Checkpoint C: evaluate the targeted gates before the full benchmark campaign

Use the same benchmark sources, dependencies, JDK, JVM flags, and fixture seeds on both
revisions. Compare baseline/candidate, then candidate/baseline. Run one measurement process
at a time. Record effective fork JVM heap settings: the benchmark project has existing heap
options, and the requested 2 GiB settings must actually reach the JMH forks.

For retained memory, JOL 0.17 is already available in the local cache as a measurement tool;
do not add it to the project dependencies. Use a standalone invocation with its exact version
and classpath recorded. Warm the runtime and initialize `analyses`, then measure the joint
object graph rooted at the fixture and lookup before and after the first lookup. Subtract
the total graph sizes to exclude already-retained shared names and analyses. Report this as
the additional reachable footprint attributable to the live lookup under those controlled
roots, not as the dominator-tree retained size of an arbitrary production JVM.

Measure the equivalent baseline delta and repeat after many distinct misses while retaining
one lookup. Keep a fixed query-buffer footprint and release per-query results. Do not use
address-based `GraphLayout.subtract` across snapshots: JOL's own source warns that GC movement
can make the identity comparison invalid. Verify the VM layout/instrumentation used for sizes;
if only estimated sizes are available, label them and obtain reliable heap/layout evidence
before declaring the memory requirement complete.

Compute candidate/baseline ratios from independent JVM-fork summaries, not individual JMH
iterations. A proposed reproducible analysis is a fixed-seed bootstrap of the fork/run
summaries (10,000 resamples) with a one-sided 95% upper ratio bound for non-regression. Keep
paired run blocks paired when resampling. Low sample counts or unstable bounds require more
independent runs; do not turn uncertainty into a passing verdict. Preserve the analysis script
or exact calculations with the report.

If either large-workload improvement gate or either small/library-heavy lifecycle regression
gate fails, stop the shipping path. Record the attempt and revisit the candidate within the
approved scope; changing the threshold or introducing a switching policy needs spec review.

### Checkpoint D: whole-compiler evidence and completion

Run the existing hot and cold Scalac/Shapeless workloads on both revisions. The hot benchmark
annotations specify only one fork, so one default `runBenchmarks` run cannot establish a
reliable independent-run confidence bound. Repeat complete, independently launched runs or
use an explicit equivalent JMH fork override with the setup and parameters recorded. Retain
the existing workloads' hot/cold semantics; do not replace cold single-shot runs with a
warmed average-time experiment.

Pass all four compiler-workload ratio bounds (one-sided 95% upper bound ≤ 1.05). An overlap
with the threshold is inconclusive, not a pass. Link baseline/candidate JSON or complete raw
outputs, exact commands, source/fixture revisions, compiler workload revisions, JVM flags,
hardware, and the statistical calculation from the report.

Run focused and affected-project suites, formatting, headers, and relevant binary-compatibility
checks using the approved spec's commands. Do not broaden into compiler-bridge edits to fix
unrelated failures. Record any existing environment/test failure separately from this change.
Finalize the spec checklist only when the corresponding evidence exists.

## Execution setup and isolation

At implementation time, inspect the then-current Git state and use an isolated `codex/` branch
or worktree from a verified base; keep the unrelated working-tree edit intact. Preserve a
baseline checkout with the same fixture, benchmark, and measurement support files as the
candidate. They should differ in the production lookup change being evaluated, not build-tool
versions or benchmark wiring. Retest semantic cases on both where applicable; the optimized
bounded-work assertion is expected to fail only on the baseline.

Use the approved spec's commands as the starting point. Record fully resolved per-checkout
commands when setting up measurements. Do not depend on the earlier ignored diagnostic runner
or its cached Scala dependency paths. Public API or generated-source changes are not required.

## Parallelization

Fixture/test drafting and report preparation can be separated after the fixture contract is
stable, and the compiler integration verification can be developed independently of report
analysis. They share files and require coordination if work is delegated later. No delegation
is needed by this plan.

Index implementation depends on the valid baseline fixture. Benchmarks and heap measurements
must run sequentially on the same otherwise idle machine; running them alongside builds or
tests would contaminate the comparison. Performance acceptance follows correctness.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Index construction outweighs lookup savings | Measure fresh-instance work and the approved query-count sweep; fail the lifecycle gates rather than hiding startup cost. |
| Duplicate definitions silently change the selected API | Identity-based tests with different API payloads and a last-wins mutation check. |
| New laziness or concurrent-initialization behavior | Leave existing lazy analyses intact; use an independent lazy immutable index and synchronized test starts. |
| Synthetic workloads misrepresent practical behavior | Vary N, A, and B independently; cover multiple query mixes; run the existing compiler workloads and disclose the original profile's absence. |
| Allocation is mistaken for retained memory | Measure shared-root graph deltas separately from GC allocation metrics; record VM size accuracy. |
| Small samples or environment drift produce a false pass | Independent forks/runs, reversed run order, identical source/setup hashes, explicit confidence bounds, and an inconclusive outcome when warranted. |
| Baseline and candidate use different test support or stale classes | Separate recorded revisions/checkouts with identical support source hashes and fresh compilation. |
| Scope expands to Mill or other compiler work | Keep provider loading and the pre-existing bridge edit out of this branch; handle them separately. |

## Phase handoff

The spec and this plan are approved. The detailed acceptance/verification/file checklist is
in `tasks/todo-issue-593.md` and was approved on 2026-09-09. Existing #432 contents in
`tasks/plan.md` and `tasks/todo.md` are retained, with navigation links to the scoped #593 files
so default entry points remain useful.

After the task breakdown is validated, implement incrementally, record each checkpoint's
evidence, and keep only a candidate that meets the approved contract and performance gates.
