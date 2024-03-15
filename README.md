**Zinc** is the incremental compiler for **Scala 2**.

Most Scala developers use it constantly without noticing. It's leveraged by build tools
such as [sbt][], [IntelliJ][], [Maven][], [Gradle][], [Mill][], [Pants][], [Bazel][],
and [Bloop][].

Zinc reduces compilation times without sacrificing
correctness. When you change a source file, Zinc analyzes the structure of
your code and recompiles only the source files affected by your
change. The result should be identical to the output of a clean compile.

## Scala 3?

Zinc isn't used for Scala 3. Instead, the Scala 3 compiler has built-in
ability to do incremental compilation. (To report an issue with that,
visit [lampepfl/dotty](https://github.com/lampepfl/dotty/issues).)

## Code of conduct

All communication in `sbt/*` repositories and chat rooms
is covered by [Scala Code of Conduct][conduct].
Please be kind and courteous to each other.

## Contributing

This project is maintained by Lightbend, the [Scala Center](https://scala.epfl.ch),
and other OSS contributors.

You're welcome to participate. To learn how,
see the [CONTRIBUTING guide](CONTRIBUTING.md).

## Integrating Zinc

Most Scala users don't need to think about Zinc at all. Just use
any of the build tools listed above, and you get Zinc's benefits.

If you're a build tool author, add Zinc to your project with:

```scala
libraryDependencies += "org.scala-sbt" %% "zinc" % "$ZINC_VERSION"
```

where `$ZINC_VERSION` is the latest tag pushed to the GitHub repository.

To integrate Zinc, you have two options:

1. Interface directly with Zinc APIs and maintain your own integration.
2. Use Bloop (which has a compilation server that simplifies tooling integrations).

### Note to compiler bridge authors

The compiler bridge classes are loaded using [java.util.ServiceLoader](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html). In other words, the class implementing `xsbti.compile.CompilerInterface2` must be mentioned in a file named: `/META-INF/services/xsbti.compile.CompilerInterface2`.

## Acknowledgements

| Logo | Acknowledgement |
| ---- | -------------- |
| ![](https://www.yourkit.com/images/yklogo.png) | We thank [Yourkit](https://www.yourkit.com/) for supporting this open-source project with its full-featured profiler. |

[sbt]: https://scala-sbt.org
[sbt/zinc]: https://github.com/sbt/zinc
[Pants]: http://pantsbuild.org
[IntelliJ]: https://github.com/Jetbrains/intellij-scala
[Maven]: https://github.com/davidB/scala-maven-plugin
[Gradle]: https://docs.gradle.org/current/userguide/scala_plugin.html
[Bazel]: https://github.com/higherkindness/rules_scala
[Bloop]: https://github.com/scalacenter/bloop
[conduct]: https://scala-lang.org/conduct/
[Mill]: https://github.com/com-lihaoyi/mill
