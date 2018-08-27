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
* [Scala Lang blog post on Zinc release](https://www.scala-lang.org/blog/2017/11/03/zinc-blog-1.0.html).
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

Hacking on Zinc should not seem like a difficult task. Zinc does not implement
a compiler, it defines the logic to analyse dependencies based on the compiler
API and creates all the required infrastructure around it to let build tools
use it.

Zinc is split into different subprojects. The compiler interface and
implementation can be found in `internal/compiler-interface` and `internal/compiler-bridge`,
while general infrastructure, sbt internal APIs and high-level compiler APIs for
Zinc are available in the rest of projects inside `internal`.

Before running tests please execute `publishBridges` command.

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
that takes care of adding the LICENSE headers to new files. Run `headerCreate`
and sbt will put a copyright notice into it.

[sbt/zinc-contrib]: https://gitter.im/sbt/zinc-contrib
