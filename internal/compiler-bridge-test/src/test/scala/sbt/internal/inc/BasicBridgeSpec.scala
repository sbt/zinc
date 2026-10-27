package sbt
package internal
package inc

import hedgehog.{ sbt => _, _ }
import hedgehog.runner._
import sbt.io.IO.withTemporaryDirectory
import sbt.io.syntax._
import sbt.internal.util.Util

/** This is a basic test for compiler bridge, mostly wrapped as
 * AnalyzingCompiler.
 */
object BasicBridgeSpec
    extends Properties
    with BridgeProviderTestkit
    with CompilingSpecification {
  override def tests: List[Test] = List(
    example("A compiler bridge should compile", compiles),
    example("A compiler bridge should run doc", runsDoc),
    // ignored: The bridge compiles the initial command :q as code, so the REPL waits for input.
    // example("A compiler bridge should run console", runsConsole),
  )

  private def compiles: Result =
    withTemporaryDirectory { tempDir =>
      compileSrcs(tempDir.toPath, "object Foo")
      val t = tempDir / "target" / "Foo$.class"
      Result.assert(t.exists).log(s"$t does not exist")
    }

  private def runsDoc: Result =
    withTemporaryDirectory { tempDir =>
      doc(tempDir.toPath)(List())
      val t = tempDir / "target" / "index.html"
      /// println((tempDir / "target").listFiles.toList)
      Result.assert(t.exists).log(s"$t does not exist")
    }

  private def runsConsole: Result =
    if (Util.isWindows) Result.success
    else
      withTemporaryDirectory { tempDir =>
        console(tempDir.toPath)(":q")
        Result.success
      }
}
