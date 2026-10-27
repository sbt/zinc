package sbt
package internal
package inc

import hedgehog.{ sbt => _, _ }
import hedgehog.runner._
import sbt.io.IO.withTemporaryDirectory
import scala.jdk.CollectionConverters._

/** This is a basic test for compiler bridge, mostly wrapped as
 * AnalyzingCompiler.
 */
object ActionTest
    extends Properties
    with BridgeProviderTestkit
    with CompilingSpecification {
  override def tests: List[Test] = List(
    example("foo", foo),
  )

  private def foo: Result =
    withTemporaryDirectory { tempDir =>
      val reporter = mkReporter
      compileSrcs(tempDir.toPath, reporter)(List(List("""
object Foo {
  def foo {}
}""")))
      val problems = reporter.problems.toList
      problems.headOption match {
        case Some(problem) => Result.assert(problem.actions.asScala.nonEmpty)
        case None          => Result.failure.log("no problems were reported")
      }
    }
}
