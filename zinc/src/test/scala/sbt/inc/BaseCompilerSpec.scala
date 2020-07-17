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

import java.nio.file.{ Files, Path }
import sbt.internal.inc.BridgeProviderSpecification
import sbt.util.Logger

class BaseCompilerSpec extends BridgeProviderSpecification {

  val scalaVersion = scala.util.Properties.versionNumberString
  val maxErrors = 100
  val noLogger = Logger.Null

  def assertExists(p: Path) = assert(Files.exists(p), s"$p does not exist")

  implicit class TestProjectSetupMod(underlying: TestProjectSetup) {
    def createCompiler(): TestProjectSetup.CompilerSetup =
      createCompiler(scalaVersion)

    def createCompiler(sv: String): TestProjectSetup.CompilerSetup =
      underlying.createCompiler(
        sv,
        scalaInstance(sv, underlying.baseLocation, noLogger),
        getCompilerBridge(underlying.baseLocation, noLogger, sv),
        pipelining = true,
        log
      )
  }

  implicit val compilerSetupHelper: TestProjectSetup.CompilerSetupHelper =
    new TestProjectSetup.CompilerSetupHelper {
      def apply(sv: String, setup: TestProjectSetup): TestProjectSetup.CompilerSetup =
        setup.createCompiler(sv)
    }

  // to avoid rewriting existing tests
  object ProjectSetup {
    def simple(baseLocation: Path, classes: Seq[String]): TestProjectSetup =
      TestProjectSetup.simple(baseLocation, classes)
  }
}
