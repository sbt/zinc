package sbt
package internal
package inc

import verify._
import sbt.io.IO
import xsbti.InteractiveConsoleResult

// This is a specification to check the REPL block parsing.
/*
object InteractiveConsoleInterfaceSpecification
    extends BasicTestSuite
    with BridgeProviderTestkit
    with CompilingSpecification {

  test("Scala interpreter should evaluate arithmetic expression") {
    withInteractiveConsole { repl =>
      val response = repl.interpret("1+1", false)
      assert(response.output.trim == "val res0: Int = 2")
      assert(response.result == InteractiveConsoleResult.Success)
    }
  }

  test("it should evaluate list constructor") {
    withInteractiveConsole { repl =>
      val response = repl.interpret("List(1,2)", false)
      assert(response.output.trim == "val res0: List[Int] = List(1, 2)")
      assert(response.result == InteractiveConsoleResult.Success)
    }
  }

  test("it should evaluate import") {
    withInteractiveConsole { repl =>
      val response = repl.interpret("import xsbt._", false)
      assert(response.output.trim == "import xsbt._")
      assert(response.result == InteractiveConsoleResult.Success)
    }
  }

  test("it should mark partial expression as incomplete") {
    withInteractiveConsole { repl =>
      val response = repl.interpret("val a =", false)
      assert(response.result == InteractiveConsoleResult.Incomplete)
    }
  }

  test("it should not evaluate incorrect expression") {
    withInteractiveConsole { repl =>
      val response = repl.interpret("1 ++ 1", false)
      assert(response.result == InteractiveConsoleResult.Error)
    }
  }

  val postfixOpExpression = "import scala.concurrent.duration._\nval t = 1 second"

  test("it should evaluate postfix op without warning when -language:postfixOps arg passed") {
    IO.withTemporaryDirectory { tempDir =>
      val repl = interactiveConsole(tempDir.toPath)("-language:postfixOps")
      try {
        val response = repl.interpret(postfixOpExpression, false)
        assert(!response.output.trim.startsWith("warning"))
        assert(response.result == InteractiveConsoleResult.Success)
      } finally {
        repl.close()
      }
    }
  }

}
 */
