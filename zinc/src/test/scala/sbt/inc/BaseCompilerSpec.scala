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

  def assertExists(p: Path) = assert(Files.exists(p), s"$p does not exist")

  implicit class ProjectSetupOps(setup: ProjectSetup) {
    def createCompiler(): CompilerSetup = createCompiler(scalaVersion)

    def createCompiler(sv: String): CompilerSetup = {
      val si = scalaInstance(sv, setup.baseDir, Logger.Null)
      val bridge = getCompilerBridge(setup.baseDir, Logger.Null, sv)
      setup.createCompiler(sv, si, bridge, true, log)
    }

  }

}
