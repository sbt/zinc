package sbt
package internal
package inc

import verify._
import sbt.io.IO.withTemporaryDirectory
import scala.collection.JavaConverters._

/** This is a basic test for compiler bridge, mostly wrapped as
 * AnalyzingCompiler.
 */
object ActionTest
    extends BasicTestSuite
    with BridgeProviderTestkit
    with CompilingSpecification {

  test("foo") {
    withTemporaryDirectory { tempDir =>
      val reporter = mkReporter
      compileSrcs(tempDir.toPath, reporter)(List(List("""
object Foo {
  def foo {}
}""")))
      val problems = reporter.problems.toList
      val problem = problems.head
      assert(problem.actions.asScala.nonEmpty)
    }
  }
}
