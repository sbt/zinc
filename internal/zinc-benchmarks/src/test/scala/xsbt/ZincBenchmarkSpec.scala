package xsbt

import org.scalatest.FunSuite
import sbt.io.IO

import scala.util.control.NonFatal

class ZincBenchmarkSpec extends FunSuite {
  def enrichResult[T](result: Either[Throwable, T]) = {
    result.left.map { throwable =>
      s"""
         |Result is not Right, got: ${throwable.getMessage}
         |${throwable.getStackTrace.mkString("\n")}
       """.stripMargin
    }
  }

  test("Check that setup for small project is successful and zinc compiles") {
    object Stoml
      extends BenchmarkProject(
        "jvican/stoml",
        "deb10309809912fbf38cf891af2ac61342024632",
        List("stoml"),
        useJavaCp = false
      )

    val stoml = new ZincBenchmark(Stoml)
    val tempDir = IO.createTemporaryDirectory

    try {
      val writeResult = enrichResult(stoml.writeSetup(tempDir))
      assert(writeResult.isRight, writeResult)

      // Delete cloned projects when tests fail
      val readResult = enrichResult(stoml.readSetup(tempDir).result)
      assert(readResult.isRight, readResult)
      readResult.foreach { setups =>
        assert(setups.nonEmpty)
        setups.foreach { setup =>
          val sources = setup.compilationInfo.sources
          assert(sources.nonEmpty)
          assert(sources.exists(_.contains("TomlParser")))
        }
      }

      readResult.foreach { setups =>
        setups.foreach { setup =>
          val info = setup.compilationInfo
          println(s"Compiling ${info.sources}")
          println(s"> Classpath: ${info.classpath}")
          setup.compile()
        }
      }

      // Clean if successful
      IO.delete(tempDir)
    } catch {
      case NonFatal(e) =>
        // Clean if it fails
        IO.delete(tempDir)
        throw e
    }
  }
}
