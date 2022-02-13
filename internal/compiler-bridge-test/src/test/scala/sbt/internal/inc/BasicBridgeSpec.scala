package sbt
package internal
package inc

import verify._
import sbt.io.IO.withTemporaryDirectory
import sbt.io.syntax._
import sbt.internal.util.Util

/**
 * This is a basic test for compiler bridge, mostly wrapped as
 * AnalyzingCompiler.
 */
object BasicBridgeSpec
    extends BasicTestSuite
    with BridgeProviderTestkit
    with CompilingSpecification {

  test("A compiler bridge should compile") {
    withTemporaryDirectory { tempDir =>
      compileSrcs(tempDir.toPath, "object Foo")
      val t = tempDir / "target" / "Foo$.class"
      assert(t.exists)
    }
  }

  test("A compiler bridge should run doc") {
    withTemporaryDirectory { tempDir =>
      doc(tempDir.toPath)(List())
      val t = tempDir / "target" / "index.html"
      /// println((tempDir / "target").listFiles.toList)
      assert(t.exists)
    }
  }

  test("A compiler bridge should run console") {
    if (Util.isWindows) ()
    else
      withTemporaryDirectory { tempDir =>
        console(tempDir.toPath)(":q")
      }
  }
}
