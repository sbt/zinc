Zinc
====

Zinc is the incremental compiler for Scala. Most Scala developers use it daily
without noticing -- it's embedded in key build tools like sbt, CBT and pants.
  
Zinc has one end goal: to make your compilation times faster without sacrificing
correctness. When you change a source file, Zinc analyses the dependencies of
your code and compiles the minimum subset of source files affected by your
change. The generated code should be identical to the output of a clean compile.

## History

Zinc was originally part of `sbt` and the Lightbend tooling team made it
independent from the previous [sbt repo](https://github.com/sbt/sbt).
Now, Zinc development lives in this repo and
[`typesafehub/zinc`](https://github.com/typesafehub/zinc) has been deprecated.

This new repository is an effort driven by Lightbend to allow any build tool
use the Scala incremental compiler, as [sbt 1.0](https://github.com/sbt/sbt),
[pants](https://github.com/pantsbuild/pants), and [CBT](https://github.com/cvogt/cbt).

## Current status

The Zinc 1.0 incremental compiler implements significant improvements over
0.13.13's version when it comes to performance, correctness and dependency
analysis.

Zinc 1.0 is in experimental status, with a pre-stable release "1.0.0-X10". Over
the next weeks, the Zinc team will work on getting Zinc 1.0 production-ready
so that build tools and Scala developers alike can benefit from these improvements
in a stable way.

## Installation and use

If you're a build tool author, add it to your project with:

```scala
libraryDependencies += "org.scala-sbt" %% "zinc" % "1.0.0-X10"
```

If you're a Scala developer that wants to try Zinc 1.0, please [head to the
installation guide of the sbt 0.13.13 plugin](https://github.com/jvican/zinc/blob/sbt-plugin/README.md) that enables you to use Zinc 1.0
in any 0.13.x sbt installation.

## Contributing

This project is maintained by the Lightbend tooling team and often receives
non-trivial contributions from the [Scala Center](https://scala.epfl.ch) and
several other OSS contributors.

You're very welcome to contribute to this repository as Zinc is currently under
active development. For information on how to contribute, please check the
[CONTRIBUTING guide](CONTRIBUTING.md).

This software is released under the following [LICENSE](LICENSE).
