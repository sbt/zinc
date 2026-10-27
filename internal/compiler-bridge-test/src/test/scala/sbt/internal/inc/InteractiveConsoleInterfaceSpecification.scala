package sbt
package internal
package inc

import hedgehog.{ sbt => _, _ }
import hedgehog.runner._
import sbt.io.IO
import xsbti.InteractiveConsoleResult

// This is a specification to check the REPL block parsing.
object InteractiveConsoleInterfaceSpecification
    extends Properties
    with BridgeProviderTestkit
    with CompilingSpecification {
  override def tests: List[Test] = List(
    example("Scala interpreter should evaluate arithmetic expression", arithmetic),
    example("it should evaluate list constructor", listConstructor),
    example("it should evaluate import", importStatement),
    example("it should mark partial expression as incomplete", partialExpression),
    example("it should not evaluate incorrect expression", incorrectExpression),
    example(
      "it should evaluate postfix op without warning when -language:postfixOps arg passed",
      postfixOp
    ),
  )

  private def arithmetic: Result =
    withInteractiveConsole { repl =>
      val response = repl.interpret("1+1", false)
      Result.all(
        List(
          response.output.trim ==== "val res0: Int = 2",
          response.result ==== InteractiveConsoleResult.Success,
        )
      )
    }

  private def listConstructor: Result =
    withInteractiveConsole { repl =>
      val response = repl.interpret("List(1,2)", false)
      Result.all(
        List(
          response.output.trim ==== "val res0: List[Int] = List(1, 2)",
          response.result ==== InteractiveConsoleResult.Success,
        )
      )
    }

  private def importStatement: Result =
    withInteractiveConsole { repl =>
      val response = repl.interpret("import scala.collection.mutable._", false)
      Result.all(
        List(
          response.output.trim ==== "import scala.collection.mutable._",
          response.result ==== InteractiveConsoleResult.Success,
        )
      )
    }

  private def partialExpression: Result =
    withInteractiveConsole { repl =>
      val response = repl.interpret("val a =", false)
      response.result ==== InteractiveConsoleResult.Incomplete
    }

  private def incorrectExpression: Result =
    withInteractiveConsole { repl =>
      val response = repl.interpret("1 ++ 1", false)
      response.result ==== InteractiveConsoleResult.Error
    }

  val postfixOpExpression = "import scala.concurrent.duration._\nval t = 1 second"

  private def postfixOp: Result =
    IO.withTemporaryDirectory { tempDir =>
      val repl = interactiveConsole(tempDir.toPath)("-language:postfixOps")
      try {
        val response = repl.interpret(postfixOpExpression, false)
        Result.all(
          List(
            Result.assert(!response.output.trim.startsWith("warning")),
            response.result ==== InteractiveConsoleResult.Success,
          )
        )
      } finally {
        repl.close()
      }
    }

}
