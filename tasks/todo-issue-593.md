# Tasks: Faster analysis lookup for Zinc #593

Status: **Approved — implementation underway.** The user approved the task breakdown and
authorized implementation on 2026-09-09.

Spec: [lookup-analysis-index.md](../docs/design/lookup-analysis-index.md).
Plan: [plan-issue-593.md](plan-issue-593.md).

- [x] Specify: requirements and numeric thresholds approved on 2026-09-09.
- [x] Plan: architecture, delivery order, risks, and checkpoints approved on 2026-09-09.
- [x] Tasks draft: acceptance criteria, dependencies, affected files, and verification recorded.
- [x] Tasks review: approved on 2026-09-09.
- [ ] Implementation complete: all task criteria and final evidence verified.

## Execution conventions

Run commands from the relevant checkout root. Commands naming new suites, benchmark classes,
or scripts specify deliverables of the listed tasks; they do not claim those files exist yet.
Use JDK 17, sbt 2.0.8, and dependencies pinned by the recorded base. The approved spec contains
the full build/test/benchmark command set; task commands below narrow it.

Task 1 records candidate/baseline checkout paths and a unique results directory. Expand the
illustrative `/private/tmp/zinc-593-results` prefix below to that directory. Give every run its
own variant/order label and record expanded commands and source hashes in `manifest.json`.
Never overwrite baseline outputs with a candidate run.

Each finished task leaves relevant checks passing. Temporary failing guards and mutations are
completed/restored within their task. Checkpoints A–D are evidence checks, not extra permission
requests: after Tasks approval, proceed automatically when they pass. Changes to approved scope
or thresholds still follow the spec's boundaries. Measurements run sequentially; support-file
changes require synchronization to the baseline and recapture of affected measurements.

## Dependency index

| Task | Plan stage | Depends on | Scope |
|---|---|---|---|
| 1. Isolate the work and record provenance | Setup | — | S: documents/state |
| 2. Establish the existing lookup contract | P1 | 1 | S: 2 files |
| 3. Add lookup lifecycle benchmarks | P2 | 2 | M: up to 3 files |
| 4. Record the original-code baseline | P2 | 3 | S: report/results |
| 5. Add the lazy index and bounded-work guard | P3 | 4, Checkpoint A | S: 2 files |
| 6. Verify hooks, lifetime, and concurrent first use | P4 | 5 | S: up to 2 files |
| 7. Verify compiler-driven invalidation | P4 | 6 | S: up to 2 files |
| 8. Add reproducible measurement analysis | P5 | 7, Checkpoint B | M: up to 4 files |
| 9. Measure targeted performance and memory | P5 | 8 | S: report/results |
| 10. Measure Scalac regressions | P6 | 9, Checkpoint C | S: report/results |
| 11. Measure Shapeless regressions | P6 | 10 | S: report/results |
| 12. Complete checks and the evidence report | P6 | 11 | M: up to 3 documents |

## Task 1: Isolate the work and record provenance

Create an isolated `codex/` branch/worktree from a verified base. Carry the approved #593
documents into it, reserve a baseline checkout, and create a unique results directory.

**Acceptance criteria:**
- [x] Base/candidate revisions, checkout paths, JDK, and results location are recorded.
- [x] The original unrelated `LocateClassFile.scala` edit and other issue documents remain intact.
- [x] The results report distinguishes planned measurements from completed evidence.

**Verify:** Run `git status --short`, `git rev-parse HEAD`, and `java -version` in the recorded
checkouts. Compare the original workspace's unrelated diff before/after setup.

**Files:** `docs/investigations/issue-593-performance.md` (new), plus copies of the approved
spec/plan/task documents. No production source edits. **Dependencies:** None. **Scope:** S.

## Task 2: Establish the existing lookup contract

Create a package-scoped fixture over real `Analysis`, `Relations`, and configuration objects,
with `newLookup()` separate from fixture construction. Add tests against the unmodified
production lookup. Test-only counters must be omitted from benchmark instances.

**Acceptance criteria:**
- [x] Empty classpath, missing/empty analyses, first/middle/last hits, and misses return the
  same analysis identity as an ordered reference search.
- [x] Duplicate names, reversed order, duplicate classpath positions, distinct source/binary
  names, nested/module names, and a selected analysis without an API are covered.
- [x] Construction and reading `analyses` preserve provider laziness/call counts; the fixture
  needs no ignored investigation files and rejects unsupported compiler operations clearly.

**Verify:** `sbt --server --batch 'zinc/testOnly sbt.internal.inc.LookupAnalysisSpec'` passes on
the original lookup. Inspect the duplicate fixture to ensure the analyses have different APIs.

**Files:** `zinc/src/test/scala/sbt/internal/inc/LookupAnalysisFixture.scala` (new),
`zinc/src/test/scala/sbt/internal/inc/LookupAnalysisSpec.scala` (new).
**Dependencies:** 1. **Scope:** S.

## Task 3: Add lookup lifecycle benchmarks

Implement the approved scenario/query matrix with `warmBatch`, `firstLookup`, and `lifecycle`
operations. Make `queryCount` default to 10,000 and support the approved lifecycle sweep.

**Acceptance criteria:**
- [x] Scenario N/A/class counts and expected query hit counts are checked outside timing;
  both repeated and distinct strings are exercised with fixed seeds.
- [x] Timed calls use production lookup with unwrapped analyses. First-use/lifecycle timings
  include lookup initialization and exclude fixture construction; results reach a `Blackhole`.
- [x] Operation/batch units are explicit, and JMH smoke runs need no new dependency/build change.

**Verify:**
```sh
sbt --server --batch 'zincBenchmarks/Jmh/run -l .*LookupAnalysisBenchmark.*'
sbt --server --batch 'zincBenchmarks/Jmh/run -f 1 -wi 1 -i 1 -w 1s -r 1s -p scenario=empty,small -p queryMix=mixed -p queryCount=10000 .*LookupAnalysisBenchmark.*'
```

**Files:** `internal/zinc-benchmarks/src/test/scala/sbt/internal/inc/LookupAnalysisBenchmark.scala`
(new), the test fixture if needed, and performance report.
**Dependencies:** 2. **Scope:** M.

## Task 4: Record the original-code baseline

Freeze a reproducible revision with the original lookup and new benchmark support before
editing production code. Record effective fork options and the source/fixture manifest.

**Acceptance criteria:**
- [x] The independently runnable baseline uses actual original `LookupImpl`, not a copied scan.
- [x] At least three forks per targeted acceptance case produce valid results with normalized
  allocation data and the effective 2 GiB fork heap.
- [x] Source hashes, expanded commands, query parameters, and raw baseline outputs are preserved.

**Verify:** Run the spec's baseline targeted JMH command with `-p queryCount=10000`, saving JSON
in the unique results directory. Run the focused contract suite on the baseline; inspect JMH
headers, dimensions, units, and fork counts. These are initial measurements; later comparisons
must recapture them if support sources change.

**Files:** Performance report and generated result/manifest artifacts.
**Dependencies:** 3. **Scope:** S.

## Checkpoint A: Trustworthy baseline

- [x] Tasks 1–4 are complete; original semantic tests pass and fixtures validate their data.
- [x] Each benchmark times exactly the lookup lifecycle it claims to measure.
- [x] A reproducible original-code revision and raw baseline evidence are available.

## Task 5: Add the lazy index and bounded-work guard

Add the deterministic guard and observe it fail on the scan. Add the private lazy immutable
map from `analyses` in reverse order through iterators, then query it from `lookupAnalysis`.

**Acceptance criteria:**
- [x] After initialization, repeated and previously unseen hit/miss queries visit no analyses
  and make no provider calls, for A = 2 and A = 2,000; the original scan fails this assertion.
- [x] Semantic tests pass without public-signature, provider, persistence, or analyzed-class
  fallback changes; first-match precedence is explicit and no per-query cache is retained.
- [x] A temporary last-wins construction fails the precedence test; all mutations are restored.

**Verify:** Run `sbt --server --batch 'zinc/testOnly sbt.internal.inc.LookupAnalysisSpec'`
before/after the edit and for the last-wins mutation; save expected red/green output. Run
`sbt --server --batch 'zinc/compile'` after restoring the correct candidate.

**Files:** `zinc/src/main/scala/sbt/internal/inc/LookupImpl.scala`, `LookupAnalysisSpec.scala`.
**Dependencies:** 4 and Checkpoint A. **Scope:** S.

## Task 6: Verify hooks, lifetime, and concurrent first use

Extend focused tests through real `LookupImpl` entry points. Use coordinated starts and atomic
counters for concurrency, with bounded waits and guaranteed executor shutdown rather than sleeps.

**Acceptance criteria:**
- [x] External positive/negative answers do not load analyses; missing provenance falls back;
  a subclass-supplied `analyses` vector controls index contents and order.
- [x] Concurrent first use publishes one complete index with expected provider counts and
  oracle-equivalent results for every reader.
- [x] Fresh instances observe added/removed definitions and reordered analyses after previous
  hits/misses; reading `analyses` alone does not build the index.

**Verify:** `sbt --server --batch 'zinc/testOnly sbt.internal.inc.LookupAnalysisSpec'`. Timeouts
detect deadlock, not performance. Synchronize any changed fixture to the baseline and mark
affected Task 4 measurements for recapture.

**Files:** `LookupAnalysisSpec.scala`, optionally `LookupAnalysisFixture.scala`.
**Dependencies:** 5. **Scope:** S.

## Task 7: Verify compiler-driven invalidation

Run existing multi-project/binary-dependency tests through compiler callbacks. Add only missing
coverage needed for upstream changes, shadowing, and incremental-versus-clean behavior.

**Acceptance criteria:**
- [x] Existing `MultiProjectIncrementalSpec` and `BinaryDepSpec` pass with the candidate.
- [x] Compiler-driven cases assert expected recompiled units and exposed behavior after an
  upstream change or shadowing, rather than merely absence of errors.
- [x] Incremental and equivalent clean compilation agree, without compiler-bridge or persisted
  analysis-format changes.

**Verify:**
```sh
sbt --server --batch 'zinc/testOnly *MultiProjectIncrementalSpec *BinaryDepSpec'
sbt --server --batch 'zinc/testOnly sbt.internal.inc.LookupAnalysisSpec'
```

**Files:** `zinc/src/test/scala/sbt/inc/MultiProjectIncrementalSpec.scala`, optionally performance
report. Existing adequate coverage need not be rewritten. **Dependencies:** 6. **Scope:** S.

## Checkpoint B: Correctness

- [x] Tasks 5–7 pass; original-scan and last-wins expected failures are saved.
- [x] Selection, lifecycle, hooks, concurrency, and compiler invalidation have direct evidence.
- [x] No debug instrumentation remains in production; benchmark support matches between variants.

## Task 8: Add reproducible measurement analysis

Add a standalone script reading manifests/JMH results and a memory-probe entry point reusing
the fixture with an externally supplied JOL 0.17 jar. Do not add a project dependency.

**Acceptance criteria:**
- [x] The script preserves modes/units, summarizes within forks, respects paired run blocks,
  and computes fixed-seed 10,000-resample one-sided 95% ratio bounds. Missing, incomparable,
  or insufficient independent data produce an explicit inconclusive result.
- [x] The memory probe reports reliable VM layout and shared-root footprint deltas before/after
  indexing and after distinct misses, excluding shared fixture/query-buffer growth and avoiding
  address-based `GraphLayout.subtract`.
- [x] The interfaces below are documented, source hashes enter the manifest, and both variants
  use identical measurement support.

**Verify:** Implement these interfaces, then run their smoke/self-check commands:
```sh
python3 bin/compare-lookup-benchmarks.py --self-test
python3 bin/compare-lookup-benchmarks.py --manifest /private/tmp/zinc-593-results/manifest.json --seed 593 --resamples 10000 --output /private/tmp/zinc-593-results/comparison.json
sbt --server --batch 'zincBenchmarks/Test/runMain sbt.internal.inc.LookupAnalysisMemoryProbe --jol-jar /Users/iceo/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/openjdk/jol/jol-core/0.17/jol-core-0.17.jar --scenario small --output /private/tmp/zinc-593-results/memory-smoke.json'
```
A baseline-only manifest must be inconclusive. Self-check known equal/slower ratios, fork versus
iteration counts, mismatched units, and absent variants. Record required JVM flags; estimated-only
JOL sizing does not complete the memory requirement.

**Files:** `bin/compare-lookup-benchmarks.py` (new),
`internal/zinc-benchmarks/src/test/scala/sbt/internal/inc/LookupAnalysisMemoryProbe.scala` (new),
optionally the shared fixture, and performance report.
**Dependencies:** 7 and Checkpoint B. **Scope:** M.

## Task 9: Measure targeted performance and memory

Run the approved matrix on actual baseline/candidate checkouts in both orders. Recompile and
recapture the baseline if support changed after Task 4. Measure one process at a time.

2026-09-11 continuation: the initial Scala-map candidate remains inconclusive after six
paired blocks, so checkpoint C is still open. The documented construction refinement uses
a local Java hash map published through an unmodifiable view, passes all 16 correctness
tests, and has completed instrumented memory probes for both variants/all five scenarios.
Fresh timing comparisons are recorded separately in
`/private/tmp/zinc-593-hashmap-20260911/manifest.json`; prior candidate evidence is retained.

**Acceptance criteria:**
- [x] Both large scenarios meet ≥50% warm mixed-query and ≥20% 10,000-query lifecycle gains
  beyond uncertainty; small/library-heavy lifecycle upper ratio bounds are ≤1.05. Otherwise
  record failure/inconclusive and stop the shipping path.
- [ ] First/last/miss/mixed cases, empty scenario, full lifecycle query-count sweep, allocation,
  and retained memory are recorded with first-use costs, limitations, and regressions disclosed.
- [ ] Raw results, source hashes, JVM options, reversed order, statistics, bytes per distinct
  name, and the measured break-even range are reproducible.

**Verify:** Run the spec's targeted/query-shape commands for each variant/order with
`queryCount=10000`. Run `lifecycle` separately with
`-p queryCount=1,10,100,1000,10000,100000` and the same fork/warmup/measurement settings. Run the
memory probe for all five scenarios and both variants, then the Task 8 comparison script.

**Files:** Performance report and raw results/manifests. **Dependencies:** 8. **Scope:** S.

## Checkpoint C: Targeted evidence

- [x] Task 9's improvement and non-regression gates pass with independent-run uncertainty.
- [ ] Construction/memory costs are documented; distinct misses retain no accumulating state.
- [x] Failed or inconclusive gates trigger more evidence/design work, not relaxed thresholds.

## Task 10: Measure Scalac regressions

Run existing hot and cold Scalac workloads on both revisions, preserving modes and compiler
setup. Repeat independent runs and reverse variant order.

**Acceptance criteria:**
- [ ] Hot and cold Scalac one-sided 95% upper candidate/baseline ratio bounds are each ≤1.05.
- [ ] Compensate for the hot benchmark's one-fork default with at least three independent runs
  per variant across both run orders, adding runs when needed. Preserve cold single-shot modes.
- [ ] Raw results, workload revision, effective flags, and calculations are in the manifest/report.

**Verify:** Run `sbt --server --batch '-Dbenchmark.pattern=.*Scalac.*' runBenchmarks` per
variant/run, capturing complete output. Feed fork/run summaries to the comparison script. An
equivalent explicit JMH invocation may provide JSON/fork control; record its full setup and
confirm the same workloads and benchmark modes.

**Files:** Performance report and raw results/manifests.
**Dependencies:** 9 and Checkpoint C. **Scope:** S.

## Task 11: Measure Shapeless regressions

Apply Task 10's independent-run protocol to the hot and cold Shapeless workloads.

**Acceptance criteria:**
- [ ] Hot and cold Shapeless one-sided 95% upper candidate/baseline ratio bounds are each ≤1.05.
- [ ] Independent runs, reversed order, modes, setup, and JVM settings follow Task 10's protocol;
  collect additional measurements if uncertainty requires them.
- [ ] The report distinguishes passing non-regression from a claimed whole-build speedup.

**Verify:** Run `sbt --server --batch '-Dbenchmark.pattern=.*Shapeless.*' runBenchmarks` per
variant/run, retain outputs, and compute independent-run bounds with the Task 8 script. Record
any equivalent explicit JMH setup as in Task 10.

**Files:** Performance report and raw results/manifests. **Dependencies:** 10. **Scope:** S.

## Task 12: Complete checks and the evidence report

Run affected-project checks and finalize a criterion-to-evidence index. Review API, provider,
external-hook, and persistence compatibility. Mark only evidence-backed requirements complete.

Validation progress (2026-09-11): all 43 zinc and 29 zinc-core tests pass, as do formatting
and license-header checks. The historical MiMa command cannot resolve `zinc_3:1.8.0` on
either the original baseline or candidate. A scoped MiMa comparison against the compiled
original baseline passes; the report preserves both the limitation and the successful local
check. Remaining performance diagnostics/compiler gates still prevent overall completion.

**Acceptance criteria:**
- [ ] Relevant tests, formatting, headers, and binary-compatibility checks pass; unrelated
  failures are documented separately without out-of-scope production edits.
- [ ] Every approved success criterion links to evidence; all targeted gates and four compiler
  bounds pass, and the report includes failed attempts and practical limitations.
- [ ] Only intended #593 changes remain; approved documents and durable tests/benchmarks/report
  are version-controlled and reproducible without ignored investigation files.

**Verify:**
```sh
sbt --server --batch 'zinc/testFull' 'zincCore/testFull'
sbt --server --batch scalafmtCheckAll scalafmtSbtCheck
sbt --server --batch headerCheck 'Test/headerCheck' 'zinc/mimaReportBinaryIssues'
git diff --check
git status --short
```
Correct formatting/headers as needed and rerun affected checks. Broaden CI only when scope,
failures, or unresolved concerns justify it. Review the evidence manually; the original tiny
investigation timings do not satisfy the gates.

**Files:** Performance report, approved spec completion status, and this task list. Source/test
fixes return to their owning task and invalidate affected evidence.
**Dependencies:** 11. **Scope:** M.

## Checkpoint D: Completion

- [ ] Tasks 1–12 are complete with evidence for all approved requirements.
- [ ] No production mutation, debug instrumentation, or unrelated edit is included.
- [ ] The report states measured gains, first-use/memory costs, confidence bounds, exact
  commands/revisions, and limits without promising an unmeasured build speedup.

If acceptance fails, record that outcome and retain useful benchmark/test work. Do not mark
this checkpoint or the spec complete merely because measurements finished. Publishing or
merging is not a task in this breakdown.
