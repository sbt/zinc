# Welcome

Zinc is a piece of software used by all Scala developers all around the globe.
Contributing to it has a far-reaching impact in all these Scala developers,
and the Zinc team tries to make it a fun and motivating experience.

## Reporting bugs to Zinc

When you find a bug in sbt we want to hear about it. Your bug reports play an important part in making sbt more reliable and usable.

Effective bug reports are more likely to be fixed. These guidelines explain how to write such reports.

Please open a GitHub issue when you are 90% sure it's an actual bug.

### What to report

The developers need three things from you: **steps**, **problems**, and **expectations**.

The most important thing to remember about bug reporting is to clearly distinguish facts and opinions.

### Steps

What we need first is **the exact steps to reproduce your problems on our computers**. This is called *reproduction steps*, which is often shortened to "repro steps" or "steps." Describe your method of running sbt. Provide `build.sbt` that caused the problem and the version of sbt or Scala that was used. Provide sample Scala code if it's to do with incremental compilation. If possible, minimize the problem to reduce non-essential factors.

Repro steps are the most important part of a bug report. If we cannot reproduce the problem in one way or the other, the problem can't be fixed. Telling us the error messages is not enough.

### Problems

Next, describe the problems, or what *you think* is the problem. It might be "obvious" to you that it's a problem, but it could actually be an intentional behavior for some backward compatibility etc. For compilation errors, include the stack trace. The more raw info the better.

### Expectations

Same as the problems. Describe what *you think* should've happened.

### Notes

Add any optional notes section to describe your analysis.

### Subject

The subject of the bug report doesn't matter. A more descriptive subject is certainly better, but a good subject really depends on the analysis of the problem, so don't worry too much about it. "Undercompilation after changing Java enum" is good enough.

### Formatting

If possible, please format code or console outputs.

On GitHub it's:

    ```scala
    name := "foo"
    ```

On StackOverflow, it's:

```
<!-- language: lang-scala -->

    name := "foo"
```

Here's a simple sample case: [#830](https://github.com/sbt/zinc/issues/830).
Finally, thank you for taking the time to report a problem.

## Reading up

If this is your first time contributing to Zinc, take some time to get familiar
with Zinc. To get you started as soon as possible, we have written a series of
guides that explain the underlying concepts of Zinc and how incremental
compilation works in 1.0.

Guides:

* [Understanding Incremental Recompilation](https://www.scala-sbt.org/1.x/docs/Understanding-Recompilation.html).
* [Scala Lang blog post on Zinc release](https://www.scala-lang.org/blog/2017/11/03/zinc-blog-1.0.html).
* All the issues and PRs labelled as `docs` will help you understand different
  aspects of Zinc. They both count with concrete information that are helpful
  to understand tradeoffs and implementation details.

The Zinc team is actively updating this information and creating more guides
to make it easier to hack on Zinc. if you find this information outdated,
open a pull request, issue, or discussion.

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

Zinc also has a JMH benchmark suite. This benchmark suite can benchmark
any project that runs on 2.12.x/2.11.x. The Zinc team uses it
to make sure that there's not a performance regression in the Zinc compiler phases.

We encourage all the contributors hacking on the compiler bridge to run these
benchmarks and include them in *both* the commit message and PR description.
The richer the descriptions the better. If you're not changing the compiler
bridge, you don't need to run these benchmarks.

### Reaching out for help

If you need any help, consider opening a draft pull request and asking
for help moving it forward. (Please be specific about where you're
stuck.)  You can also ask questions in the Discussions tab on GitHub.

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
$ sbt -Dsbtio.path=../io -Dsbtutil.path=../util
```

## Headers

To make it easier to respect our license agreements, we have added an sbt task
that takes care of adding the LICENSE headers to new files. Run `headerCreate`
and sbt will put a copyright notice into it.

## Signing the CLA

Contributing to Zinc requires you or your employer to sign the
[Lightbend Contributor License Agreement](https://www.lightbend.com/contribute/cla).
