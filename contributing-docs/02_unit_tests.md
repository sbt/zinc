Zinc Unit tests
===============

Various functional and unit tests are defined throughout the
project.

- To run all of them, run `test` in the sbt shell.
- You can run a single test suite with `testOnly **.FooTest`.
- You can run an incremental test as `testQuick`.
- You can run an incremental test scoped to a subproject as `zincCore/testQuick`

Zinc contains two testing frameworks: HedgeHog and Verify.
Our preferred unit testing framework for new tests is HedgeHog.

Property-based testing with HedgeHog
------------------------------------

HedgeHog is a test dependency of every Scala 3 subproject, added by `addTestDependencies`
in `project/Dependencies.scala`. sbt detects the HedgeHog test framework automatically.

Zinc's test sources live in the `sbt` package, and `import hedgehog.*` brings in
a `hedgehog.sbt` package that shadows it, breaking imports such as `sbt.io.IO`.
Mask it in the import with `import hedgehog.{ sbt as _, * }`.

See [internal/zinc-core/src/test/scala/sbt/internal/inc/MappedFileConverterRefSpec.scala](../internal/zinc-core/src/test/scala/sbt/internal/inc/MappedFileConverterRefSpec.scala) for an example.

```scala
package sbt
package internal
package inc

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.nio.file.{ Files, Path }
import sbt.io.IO

object MappedFileConverterRefSpec extends Properties:
  override def tests: List[Test] = List(
    property("the id agrees with toVirtualFile", propAgreesWithToVirtualFile),
  )

  private def genSegments: Gen[List[String]] =
    Gen.list(Gen.string(Gen.alphaNum, Range.linear(1, 8)), Range.linear(1, 4))

  def propAgreesWithToVirtualFile: Property =
    for
      underRoot <- Gen.boolean.forAll
      segments <- genSegments.forAll
    yield withTempDir { dir =>
      val out = Files.createDirectories(dir.resolve("out"))
      val base = if underRoot then out else dir.resolve("elsewhere")
      val path = segments.foldLeft(base)(_.resolve(_)).resolve("x.jar")
      val converter = MappedFileConverter(Map("OUT" -> out), allowMachinePath = true)
      (converter.toVirtualFileRef(path).id ==== converter.toVirtualFile(path).id)
        .log(s"path: $path")
    }

  private def withTempDir(f: Path => Result): Result =
    IO.withTemporaryDirectory(d => f(d.toPath))
end MappedFileConverterRefSpec
```

Unit testing with HedgeHog
--------------------------

HedgeHog can be used for simple unit testing as well using `example(...)`:

```scala
object MappedFileConverterRefSpec extends Properties:
  override def tests: List[Test] = List(
    example("a directory under a root is encoded without listing it", directoryNotListed),
  )

  private def directoryNotListed: Result = withTempDir { dir =>
    val out = Files.createDirectories(dir.resolve("out"))
    val classes = Files.createDirectories(out.resolve("a/classes"))
    val converter = MappedFileConverter(Map("OUT" -> out), allowMachinePath = false)
    val ref = converter.toVirtualFileRef(classes)
    Result.all(
      List(
        ref.id ==== "${OUT}/a/classes",
        Result.assert(!ref.isInstanceOf[VirtualFile]).log(s"ref: ${ref.getClass}"),
      )
    )
  }
end MappedFileConverterRefSpec
```

Helpers for HedgeHog
--------------------

`HedgehogSupport` in `zinc-testing` fills a few gaps in HedgeHog:

- `assertThrows[E](body)` and `intercept[E](body)` check that `body` throws `E`.
- `contains(actual, expected)` and `notContains(actual, unexpected)` check substrings, and log both strings on failure.
- `pendingUntilFixed(result)` succeeds while `result` fails, so a fix shows up as a failure that asks to make it a regular test.
- `shuffle(xs)` generates a permutation of `xs`.
- `withTempDir(f)` runs `f` in a temporary directory.

HedgeHog reports a thrown `Exception` as a failure, but an `Error` escapes the test runner.
Return a `Result` instead of calling `Predef.assert`, which throws `AssertionError`.

Tests that compile Scala or Java code are slow, so lower their sample count with `withTests`:

```scala
property("extract every class of any nesting", propNesting).withTests(10)
```

Suites that need a compiler bridge mix `BridgeProviderTestkit` (and `CompilingSpecification` in `compiler-bridge-test`, or `BaseCompilerSpec` in `zinc`) into the `Properties` object:

```scala
object ExtractAPISpecification
    extends Properties
    with CompilingSpecification
    with BridgeProviderTestkit {
  ...
}
```

`.scalafmt.conf` formats `compiler-bridge-test` with the Scala 2.13 dialect, so write those sources in Scala 2 syntax, for example `import hedgehog.{ sbt => _, _ }`.

Unit testing with Verify
------------------------

Verify is available in every subproject, and can be used for unit tests:

```scala
package sbt
package internal
package inc

import verify.*

object VirtualDirectoryTest extends BasicTestSuite:
  test("subdirectory") {
    val root = BasicVirtualDirectory.newRoot
    val sub1 = root.subdirectoryNamed("sub1")
    assert(sub1.id == "/sub1/")
    assert(sub1.names.toList == List("sub1"))
  }
end VirtualDirectoryTest
```
