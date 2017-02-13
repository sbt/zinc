package xsbt

import org.scalatest.FunSuite

import scala.util.control.NonFatal

class ZincBenchmarkSpec extends FunSuite {
  test("Check that setup for small project is successful and zinc compiles") {
    object Stoml
      extends BenchmarkProject(
        "jvican/stoml",
        "deb10309809912fbf38cf891af2ac61342024632",
        List("stoml"),
        useJavaCp = false
      )

    val stoml = new ZincBenchmark(Stoml)
    val result = stoml.prepare.result
    val resultWithErrorMessage = result.left.map { throwable =>
      s"""
         |Result is not Right, got: ${throwable.getMessage}
         |${throwable.getStackTrace.mkString("\n")}
       """.stripMargin
    }

    // Delete cloned projects when tests fail
    try {
      assert(resultWithErrorMessage.isRight, resultWithErrorMessage)
      resultWithErrorMessage.foreach { setups =>
        assert(setups.nonEmpty)
        setups.foreach { setup =>
          val sources = setup.srcs
          assert(sources.nonEmpty)
          assert(sources.exists(_.contains("TomlParser")))
        }
      }

      resultWithErrorMessage.foreach { setups =>
        setups.foreach { setup =>
          println(s"Compiling ${setup.srcs}")
          println(s"> Classpath: ${setup.classpath}")
          setup.run.compile(setup.srcs)
        }
      }
    } catch {
      case NonFatal(e) =>
        resultWithErrorMessage.foreach { setups =>
          sbt.io.IO.delete(setups.head.at)
        }
        throw e
    }
  }
}
