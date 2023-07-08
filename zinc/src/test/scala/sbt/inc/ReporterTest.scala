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

package sbt.inc

import sbt.internal.inc.ZincBuildInfo._
import sbt.internal.inc.{ BridgeProviderSpec, StringVirtualFile }
import sbt.io.IO.{ withTemporaryDirectory => withTmpDir }
import scala.collection.JavaConverters._
import xsbti.Action

object ReporterTest extends BridgeProviderSpec {
  test("report action on procedure syntax") {
    withCode("""def foo { print("") }""") { a =>
      assert(a.title == "procedure syntax (defn)")
    }
  }

  test("report action on missing parens") {
    withCode("""println""") { a =>
      assert(a.title == "auto-application of empty-paren methods")
    }
  }

  def withCode(code: String)(f: Action => Unit): Unit =
    withTmpDir { tmpDir =>
      val c1 = VirtualSubproject(tmpDir.toPath / "sub1")
        .setup
        .copy(scalacOptions = Seq("-deprecation"))
        .createCompiler(scalaVersion213_next)
      val reporter = c1.reporter
      try {
        val _ = c1.compile(StringVirtualFile(
          "A.scala",
          s"""class A {
  $code
}"""
        ))
        assert(reporter.problems.length == 1)
        val actions = reporter.problems.head.actions.asScala
        assert(actions.length == 1)
        val action = actions.head
        f(action)
      } finally c1.close()
    }
}
