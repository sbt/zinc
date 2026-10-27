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

package sbt.inc

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.{ Analysis, PlainVirtualFileConverter, StringVirtualFile }
import sbt.io.IO
import sbt.io.syntax.*

object JdkDepSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example("not track JDK class files as library deps", jdkClassFiles),
  )

  private val source =
    """package pkg
      |class UsesJdk extends java.util.ArrayList[String] {
      |  def timestamp: java.sql.Timestamp = null
      |}
      |""".stripMargin

  /**
   * Unlike MappedFileConverter, PlainVirtualFileConverter has no mapping for the class files
   * that the JDK serves from its `jrt:` image (sbt/zinc#609). One class name is recorded per
   * library file, and the JDK serves one file per class, so a tracked JDK class shows up here.
   */
  private def jdkClassFiles: Result = IO.withTemporaryDirectory: tmp =>
    val proj = VirtualSubproject(
      tmp.toPath / "p",
      converterOverride = Some(PlainVirtualFileConverter.converter)
    )
    withCompiler(proj.setup.createCompiler()): compiler =>
      val result = compiler.compile(StringVirtualFile("src/pkg/UsesJdk.scala", source))
      val libraryClasses = result.analysis.asInstanceOf[Analysis].relations.libraryClassName._2s
      Result.all(
        List(
          libraryClasses.filter(_.startsWith("java.")) ==== Set.empty,
          Result
            .assert(libraryClasses.exists(_.startsWith("scala.")))
            .log(s"expected the Scala library to be tracked, got $libraryClasses"),
        )
      )
end JdkDepSpec
