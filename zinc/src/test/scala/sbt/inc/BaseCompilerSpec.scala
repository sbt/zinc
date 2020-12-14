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
import xsbti.compile.IncOptions

class BaseCompilerSpec extends BridgeProviderSpecification {
  val scalaVersion: String = scala.util.Properties.versionNumberString

  val incOptions: IncOptions = IncOptions.of().withPipelining(true)

  def assertExists(p: Path) = assert(Files.exists(p), s"$p does not exist")

  implicit class ProjectSetupOps(setup: ProjectSetup) {
    def createCompiler(): CompilerSetup = setup.createCompiler(scalaVersion, incOptions)

    private def createCompiler(sv: String, incOptions: IncOptions): CompilerSetup = {
      val si = scalaInstance(sv, setup.baseDir, Logger.Null)
      val bridge = getCompilerBridge(setup.baseDir, Logger.Null, sv)
      setup.createCompiler(sv, si, bridge, incOptions, log)
    }
  }
}
