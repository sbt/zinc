/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.io.File
import java.util.function.Supplier
import scala.collection.mutable
import sbt.util.InterfaceUtil
import xsbti.{ Position, Problem, Severity }

object LoggedReporterSpec extends Properties:
  override def tests: List[Test] = List(
    example("keep rendered when a mapper only moves the position to another path", rebased),
    example("drop rendered when a mapper moves the position elsewhere", dropped),
  )

  private val id = "${BASE}/src/A.scala"
  private val absolute = new File(new File("machine").getAbsoluteFile, "A.scala")

  private def position(line: Int, path: String, file: File): Position = InterfaceUtil.position(
    line0 = Some(line),
    content = "val a: Int = \"\"",
    offset0 = Some(13),
    pointer0 = Some(13),
    pointerSpace0 = Some(" " * 13),
    sourcePath0 = Some(path),
    sourceFile0 = Some(file),
    startOffset0 = None,
    endOffset0 = None,
    startLine0 = None,
    startColumn0 = None,
    endLine0 = None,
    endColumn0 = None
  )

  private object NoLogger extends xsbti.Logger:
    def error(msg: Supplier[String]): Unit = ()
    def warn(msg: Supplier[String]): Unit = ()
    def info(msg: Supplier[String]): Unit = ()
    def debug(msg: Supplier[String]): Unit = ()
    def trace(exception: Supplier[Throwable]): Unit = ()

  private def logged(mapper: Position => Position): Option[String] =
    val seen = mutable.ListBuffer.empty[Problem]
    val reporter = new LoggedReporter(10, NoLogger, mapper):
      override protected def logError(problem: Problem): Unit = seen += problem
    reporter.log(
      InterfaceUtil.problem(
        "typer",
        position(1, id, new File(id)),
        "type mismatch",
        Severity.Error,
        Some(s"$id:1:14: type mismatch"),
        None,
        Nil,
        Nil
      )
    )
    InterfaceUtil.jo2o(seen.head.rendered)

  private def rebased: Result =
    logged(_ => position(1, absolute.getPath, absolute)) ====
      Some(s"${absolute.getPath}:1:14: type mismatch")

  private def dropped: Result =
    logged(_ => position(2, absolute.getPath, absolute)) ==== None
end LoggedReporterSpec
