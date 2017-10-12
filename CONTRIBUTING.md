# Welcome

Zinc is a piece of software used by all Scala developers all around the globe.
Contributing to it has a far-reaching impact in all these Scala developers,
and the Zinc team tries to make it a fun and motivating experience.

## Reading up

If this is your first time contributing to Zinc, take some time to get familiar
with Zinc. To get you started as soon as possible, we have written a series of
guides that explain the underlying concepts of Zinc and how incremental
compilation works in 1.0.

Guides:

* [Understanding Incremental Recompilation](http://www.scala-sbt.org/0.13/docs/Understanding-Recompilation.html).
* [Scala Lang blog post on Zinc release](https://www.scala-lang.org/blog/2017/01/03/zinc-blog-post.html).
* All the issues and PRs labelled as `docs` will help you understand different
  aspects of Zinc. They both count with concrete information that are helpful
  to understand tradeoffs and implementation details.

The Zinc team is actively updating this information and creating more guides
to make it easier to hack on Zinc. if you find this information outdated,
please let us know in our Gitter room [sbt/zinc-contrib][].

## Hacking on Zinc

### Getting your hands dirty

Once you understand the basics of incremental compilation, start having a look
at open tickets you can help with. All issues are labelled and will give you an
idea about its difficulty and scope.

Hacking on Zinc should not seem like a difficult task. Zinc does not implement a compiler, it defines the logic to analyse dependencies based on the compiler
API and creates all the required infrastructure around it to let build tools
use it.

Zinc is split into different subprojects. The compiler interface and
implementation can be found in `internal/compiler-interface` and `internal/compiler-bridge`,
while general infrastructure, sbt internal APIs and high-level compiler APIs for
Zinc are available in the rest of projects inside `internal`.

Zinc also has a JMH benchmark suite. This benchmark suite can benchmark
any project that runs on 2.12.x/2.11.x. The Zinc team uses it
to make sure that there's not a performance regression in the Zinc compiler phases.

We encourage all the contributors hacking on the compiler bridge to run these
benchmarks and include them in *both* the commit message and PR description.
The richer the descriptions the better. If you're not changing the compiler
bridge, you don't need to run these benchmarks.

### Reaching out for help

If you need any help, the Zinc team hangs out in [sbt/zinc-contrib][].
Feel free to ask any question.

### Getting familiar with the build

#### Project structure

As of now, the current project structure is not as simple as it can be and we
believe that it can be simpler in the future. However, as of now, no work is
happening in this area because it's deemed to have low impact on the overall
quality of the project.

How is the Zinc build structured? Let's see it.

|Project name| Project description|
|------------|--------------------|
|zincRoot| The root of the project. Aggregates all projects except the benchmarks.|
|zinc|The user-facing Zinc incremental compiler.|
|zincTesting|The project that defines testing facilities.|
|zincCompile|A thin wrapper that provides doc capabilities.|
|zincPersist|The project that persists incremental compiler's data into a binary file.|
|zincCore|The project that defines relations, analysis, stamps, and essential core utils.| 
|zincBenchmarks|The project that defines the benchmarks.|
|zincIvyIntegration|The project that defines the ivy utilities to fetch compiler bridges.|
|zincCompileCore|The project that interfaces with the compiler API and provides compilation capabilities.| 
|zincApiInfo|The project that defines name hashes and provides way to interpret api changes.|
|zincClassfile|The project that parses class files to provide Java incremental compilation.|
|zincClasspath|The project that provides basic utilities to load libraries with classloaders and represents Scala instances.|
|zincScripted|The project that defines the scripted logic to run Zinc's integration test suite.|
|compilerInterface|The public binary interface used to connect the bridges with the Zinc modules. It is written in Java and uses Contraband.|
|compilerBridge|The module that defines the compiler plugin phases that provide incrementality for all Scala versions.|

#### Build-specific commands/keys

The sbt build defines several keys that help contributors run and test Zinc.
Zinc's build requires the compiler bridges to be published before tests are run
(compiler bridges are compiler-specific Scala sources that need to be fetched
to perform incremental compilation).

|Key|Use|
|---|---|
|crossTestBridges|Runs compiler bridge unit tests for all scala versions.|
|publishBridgesAndTest|Publish bridges and test the whole incremental compiler.|
|publishBridgesAndSet|Publish bridges and set the current Scala version.|

### Benchmarking Zinc

To run JMH benchmarks, run the sbt task `runBenchmarks`. By default,
it will run a benchmark for Shapeless, but all benchmarks are welcome to be run
on the Scala standard library and other well-known projects in the community,
like Akka.

If you add a new benchmark, make sure that you define the new benchmarking repo
in [BenchmarkProjects.scala](https://github.com/sbt/zinc/blob/d532d15139f9f6e8346c8ffb649e564b25d7e897/internal/zinc-benchmarks/src/main/scala/xsbt/BenchmarkProjects.scala)
and that you define how the benchmarks should be run (have a look at the
[Shapeless JMH definition](https://github.com/sbt/zinc/blob/d532d15139f9f6e8346c8ffb649e564b25d7e897/internal/zinc-benchmarks/src/main/scala/xsbt/ShapelessBenchmark.scala)).
Finally, add your project to the [GlobalBenchmarkSetup.scala](https://github.com/sbt/zinc/blob/d532d15139f9f6e8346c8ffb649e564b25d7e897/internal/zinc-benchmarks/src/main/scala/xsbt/GlobalBenchmarkSetup.scala).

### How to build with other sbt 1.0 module

Zinc depends on concrete versions of sbt 1.0 modules. If you want to run Zinc
with a newer version of them, you can do it with:

```
$ sbt -Dsbtio.path=../io -Dsbtutil.path=../util -Dsbtlm.path=../librarymanagement
```

## Signing the CLA

Contributing to Zinc requires you or your employer to sign the
[Lightbend Contributor License Agreement](https://www.lightbend.com/contribute/cla).

To make it easier to respect our license agreements, we have added an sbt task
that takes care of adding the LICENSE headers to new files. Run `createHeaders`
and sbt will put a copyright notice into it.

[sbt/zinc-contrib]: https://gitter.im/sbt/zinc-contrib
