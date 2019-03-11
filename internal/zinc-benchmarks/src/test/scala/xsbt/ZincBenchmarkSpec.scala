package xsbt

import org.scalatest.FunSuite

// import sbt.io.IO
// import scala.util.control.NonFatal

class ZincBenchmarkSpec extends FunSuite {
  def enrichResult[T](result: Either[Throwable, T]) = {
    result.left.map { throwable =>
      s"""
         |Result is not Right, got: ${throwable.getMessage}
         |${throwable.getStackTrace.mkString("\n")}
       """.stripMargin
    }
  }
}
