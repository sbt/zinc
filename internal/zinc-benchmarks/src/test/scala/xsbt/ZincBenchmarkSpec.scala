/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

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
