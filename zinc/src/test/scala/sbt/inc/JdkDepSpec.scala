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

import sbt.internal.inc.{ Analysis, PlainVirtualFileConverter, StringVirtualFile }
import sbt.io.IO

class JdkDepSpec extends BaseCompilerSpec {
  private val source =
    """package pkg
      |class UsesJdk extends java.util.ArrayList[String] {
      |  def timestamp: java.sql.Timestamp = null
      |}
      |""".stripMargin

  it should "not track JDK class files as library deps" in (IO.withTemporaryDirectory { tmp =>
    // Unlike MappedFileConverter, this converter has no mapping for the JDK's own class files,
    // which since JDK 9 are served from the `jrt:` runtime image. See sbt/zinc#609.
    val converter = PlainVirtualFileConverter.converter
    val proj = VirtualSubproject(tmp.toPath / "p", converterOverride = Some(converter))
    val compiler = proj.setup.createCompiler()
    try {
      val result = compiler.compile(StringVirtualFile("src/pkg/UsesJdk.scala", source))
      val analysis = result.analysis.asInstanceOf[Analysis]
      // One representative class name is recorded per library file, and the JDK serves one
      // file per class, so a tracked JDK class shows up here under its own name.
      val libraryClasses = analysis.relations.libraryClassName._2s
      libraryClasses.filter(_.startsWith("java.")) shouldBe Symbol("empty")
      assert(
        libraryClasses.exists(_.startsWith("scala.")),
        s"expected the Scala library to be tracked, got $libraryClasses"
      )
    } finally compiler.close()
  })
}
