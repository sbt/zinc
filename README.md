Zinc
====

[![Build Status](https://ci.scala-lang.org/api/badges/sbt/zinc/status.svg)](https://ci.scala-lang.org/sbt/zinc)

Zinc is the incremental compiler for Scala. Most Scala developers use it daily
without noticing -- it's embedded in key build tools like sbt, CBT and pants.

The primary goal of Zinc is to make your compilation times faster without sacrificing
correctness. When you change a source file, Zinc analyses the dependencies of
your code and compiles the minimum subset of source files affected by your
change. The generated code should be identical to the output of a clean compile.

## History

[sbt]: https://github.com/sbt/sbt
[typesafehub/zinc]: https://github.com/typesafehub/zinc
[sbt/zinc]: https://github.com/sbt/zinc
[pants]: https://github.com/pantsbuild/pants
[CBT]: https://github.com/cvogt/cbt
[Intellij]: https://github.com/Jetbrains/intellij-scala
[Scala IDE]: https://github.com/scala-ide/scala-ide
[Maven Plugin]: https://github.com/random-maven/scalor-maven-plugin
[Bazel Rule]: https://github.com/higherkindness/rules_scala
[bloop]: https://github.com/scalacenter/bloop
[conduct]: https://www.lightbend.com/conduct
[mill]: https://github.com/com-lihaoyi/mill

Originally this project was part of [sbt][], referred to as the incremental compiler module of sbt.

To allow for build tools outside of sbt to use it, the project [typesafehub/zinc][] was created to re-export the
whole of sbt to utilise the incremental compiler module.

With the effort for sbt 1, the sbt team extracted the incremental compiler from the sbt repo, to the
[sbt/zinc][] repo, recycling the name "zinc".

This new repository is an effort driven by Lightbend to allow any build tool
use the Scala incremental compiler, as [sbt 1.0][sbt], [pants][], [bazel][Bazel Rule], [cbt][],
[Intellij][], [Scala IDE][] and [Maven Plugin][].

## Current status

The Zinc 1.0 incremental compiler implements significant improvements over
0.13.13's version when it comes to performance, correctness and dependency
analysis.

Besides it's use in [sbt](https://github.com/sbt/sbt), Zinc 1.0 is also used by many build tools in the Scala ecosystem:

* Bazel via [rules_scala][Bazel Rule]
* [Bloop][bloop] - a compile server using zinc to compile Scala and Java code
* Maven via the [Scalor Plugin](https://github.com/random-maven/scalor-maven-plugin)
* [Mill][mill] - a Scala-based build tools using zinc to compile Java and Scala code
* [Pants][pants] - a Python-based build tool for monorepos

If you want to create your own integration, you have two options:

1. Interface directly with Zinc APIs and maintain your own integration.
2. Use Bloop (which has a compilation server that simplifies tooling integrations).

## Installation and use

If you're a build tool author, add it to your project with:

```scala
libraryDependencies += "org.scala-sbt" %% "zinc" % "$ZINC_VERSION"
```

where `$ZINC_VERSION` is the latest tag pushed to the GitHub repository.

## Code of conduct

All communication in `sbt/*` repositories, including Zinc, and Gitter chat rooms
such as sbt/zinc-contrib are covered by [Lightbend Community Code of Conduct][conduct].
Please be kind and courteous to each other.

## Contributing

This project is maintained by Lightbend, the [Scala Center](https://scala.epfl.ch)
and other OSS contributors.

You're welcome to contribute to this repository. For information on how to contribute,
please check the [CONTRIBUTING guide](CONTRIBUTING.md).

This software is released under the following [LICENSE](LICENSE).

### Note to compiler bridge authors

The compiler bridge classes are loaded using [java.util.ServiceLoader](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html). In other words, the class implementing `xsbti.compile.CompilerInterface2` must be mentioned in a file named: `/META-INF/services/xsbti.compile.CompilerInterface2`.

## Acknowledgements

| Logo | Acknowledgement |
| ---- | -------------- |
| ![](https://www.yourkit.com/images/yklogo.png) | We thank [Yourkit](https://www.yourkit.com/) for supporting this open-source project with its full-featured profiler. |
