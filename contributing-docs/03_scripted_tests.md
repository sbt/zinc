Scripted tests
==============

End-to-end incremental-compilation scenarios. Each test drives a real compile, edits sources, and
recompiles, asserting on what Zinc decided to recompile. The engine is a custom re-implementation in
[`internal/zinc-scripted`](../internal/zinc-scripted), not sbt's `scripted` plugin.

Layout
------

```
zinc/src/sbt-test/<group>/<name>/
  build.json               # optional; project list for multi-project tests
  *.scala, *.java          # the sources being compiled
  changes/                 # edited versions, copied over the originals mid-test
  incOptions.properties    # optional; see below
  test                     # the script of steps
```

Naming the script `pending` instead of `test` marks the scenario as known-failing: it still runs, but
a failure is tolerated, and *passing* is what fails the build (a reminder to rename it back). A
`pending` file takes precedence over a `test` file in the same directory.

Groups: `source-dependencies`, `apiinfo`, `macros`, `pipelining`, `profiler`, `reporter`, `general`.

Without a `build.json` the test is a single project named `root` rooted at the test directory. With
one, each entry declares a `name` and optional `dependsOn`, `in`, and `scalaVersion`. A project's
base directory is `in` when given, otherwise the subdirectory named after the project
([IncHandler.scala:124](../internal/zinc-scripted/src/test/scala/sbt/internal/inc/IncHandler.scala#L124)).

The `test` script is one step per line:

- `> compile` runs a task on the root project, `> use/compile` on the `use` project.
- `-> compile` expects the task to fail.
- `$ copy-file changes/A.scala A.scala` runs a file command (`copy-file`, `delete`, `exists`,
  `absent`, `newer`, `touch`, `sleep`, `pause`).
- Assertions are tasks too: `checkRecompilations`, `checkIterations`, `checkProducts`,
  `checkDependencies`, `checkClasses`, `checkWarnings`, `checkErrors`, `checkSame`, and others.

The full task list is the `commands` map in
[`IncHandler.scala`](../internal/zinc-scripted/src/test/scala/sbt/internal/inc/IncHandler.scala#L229);
file commands come from `ZincFileCommands` and sbt's `FileCommands`.

Running
-------

```bash
sbt scripted                                                # all tests
sbt "scripted source-dependencies/abstract-class-to-trait"   # one test
sbt "scripted source-dependencies/*"                         # one group
```

`incOptions.properties`
-----------------------

Scripted tests have no build definition rich enough to configure Zinc, so this file is how a test
sets a few [`IncOptions`](../internal/compiler-interface/src/main/contraband-java/xsbti/compile/IncOptions.java)
fields and the scalac options for a project. It is read by
[`IncHandler.loadIncProperties`](../internal/zinc-scripted/src/test/scala/sbt/internal/inc/IncHandler.scala#L809)
and parsed by
[`IncOptionsUtil.fromStringMap`](../internal/zinc-core/src/main/java/xsbti/compile/IncOptionsUtil.java#L78).

Place it in the **project** base directory: the test root for a single-project test, or each
subproject directory (`dep/`, `use/`) for a multi-project one. Both `incoptions.properties` and
`incOptions.properties` are accepted, lowercase taking precedence.

Standard `java.util.Properties` syntax (`#` comments, `key = value`). Omitting a key leaves the
option at the effective scripted default shown below, which for some keys is *not* the `IncOptions`
default.

```properties
# source-dependencies/same-source-transitive-invalidation
transitiveStep = 1
```

### Supported keys

These take effect. "Default" is the value you get when the key is absent from a scripted test, which
is what a test author needs; where that differs from the `IncOptions` default, the difference is
noted.

| Key | Value | Default |
| --- | --- | --- |
| `transitiveStep` | int: invalidation cycles before falling back to the transitive closure | 3 |
| `recompileAllFraction` | double: fraction of sources invalidated that triggers a full recompile | **1.0** (`IncOptions` uses 0.5) |
| `relationsDebug` | boolean: verbose invalidation diagnostics, including the pruned relations and detected API changes | false |
| `apiDebug` | boolean: log API diffs | **true** (`IncOptions` uses false) |
| `apiDiffContextSize` | int: context lines in API diffs | 5 |
| `recompileOnMacroDef` | boolean, or `NOTHING` to leave unset | unset, which behaves as true |
| `logRecompileOnMacro` | boolean | true |
| `useOptimizedSealed` | boolean: use the optimized sealed-children invalidation | false |
| `storeApis` | boolean: persist extracted APIs in the analysis | true |
| `pipelining` | boolean: pipelined compilation | **true** (`IncOptions` uses false) |
| `scalac.options` | **space-separated** options passed to the compiler; `[basedir]` expands to the project's absolute base directory. Handled by `IncHandler`, not `IncOptionsUtil` | none |
| `incOptions.storeApis` | boolean; same as `storeApis`, applied after parsing. Handled by `IncHandler` | true |

Two wrinkles in this table:

- `recompileAllFraction = 0.5` cannot be expressed. The engine detects "not set" by comparing the
  parsed value against `IncOptions.defaultRecompileAllFraction()` rather than checking whether the
  key is present
  ([IncHandler.scala:837](../internal/zinc-scripted/src/test/scala/sbt/internal/inc/IncHandler.scala#L837)),
  so writing the default explicitly still yields 1.0. Any other value is honoured.
- With `pipelining` on, `-Ypickle-java -Ypickle-write <earlyOutput>` is appended to `scalac.options`
  automatically. Java-heavy tests set `pipelining = false` to opt out.

### Recognized but ineffective keys

`IncOptionsUtil` parses these, so they look supported, but nothing observes them in a scripted run.

| Key | Why |
| --- | --- |
| `classfileManagerType` | Always overwritten with a transactional manager rooted at `target/classes.bak` ([IncHandler.scala:363](../internal/zinc-scripted/src/test/scala/sbt/internal/inc/IncHandler.scala#L363)) |
| `transactionalManagerBaseDirectory` | Only feeds the manager built from `classfileManagerType`, which is then discarded |
| `allowMachinePath` | Not consulted; the engine hardcodes `true` when it builds the `MappedFileConverter` ([IncHandler.scala:117](../internal/zinc-scripted/src/test/scala/sbt/internal/inc/IncHandler.scala#L117)) |
| `apiDumpDirectory` | Unimplemented in Zinc itself, as `incremental.contra` notes |
| `ignoredScalacOptions` | Only affects whether a *change* in scalac options forces a full recompile ([MiniSetupUtil.scala:156](../internal/zinc-core/src/main/scala/sbt/internal/inc/MiniSetupUtil.scala#L156)), and scalac options cannot change during a scripted run, see caveats |

### Unsupported `IncOptions` fields

`IncOptionsUtil` recognizes only the keys listed above. Other `IncOptions` fields, including
`strictMode`, `enabled`, `useCustomizedFileManager`, `auxiliaryClassFiles`, `extra`, and
`externalHooks`, have no property key and cannot be set from this file.

### Caveats

1. **The file is read once, at project initialization.** `incOptions` and `scalacOptions` are `val`s
   on `ProjectStructure`
   ([IncHandler.scala:350](../internal/zinc-scripted/src/test/scala/sbt/internal/inc/IncHandler.scala#L350))
   and every compilation reuses them
   ([IncHandler.scala:723](../internal/zinc-scripted/src/test/scala/sbt/internal/inc/IncHandler.scala#L723)),
   so copying `changes/incOptions.properties` over the live file partway through a `test` script has
   no effect. This is also why `ignoredScalacOptions` is inert: a test cannot vary its scalac options
   between compilations, so the comparison that key relaxes never sees a difference.
   `source-dependencies/scalac-options` is written as if it did work, and passes for other reasons.
2. **Unknown keys are silently ignored.** There is no validation, so a typo, or one of the
   unsupported fields above, looks exactly like a working setting.
