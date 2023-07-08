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

package sbt.internal.inc

import java.nio.file.Path
import sbt.inc.{ CompilerSetup, ScalaBridge, ConstantBridgeProvider, ProjectSetup }
import sbt.util.Logger
import xsbti.compile.{ CompilerBridgeProvider, IncOptions }

trait BridgeProviderSpec extends verify.BasicTestSuite with BridgeProviderTestkit

class BridgeProviderSpecification extends UnitSpec with BridgeProviderTestkit {}

trait BridgeProviderTestkit extends AbstractBridgeProviderTestkit {
  def incOptions: IncOptions = IncOptions.of().withPipelining(true)

  implicit class ProjectSetupOps(setup: ProjectSetup) {
    import sbt.internal.inc.ZincBuildInfo._

    def createCompiler(): CompilerSetup =
      createCompiler(scalaVersion213, incOptions)

    def createCompiler(scalaVersion: String): CompilerSetup =
      createCompiler(scalaVersion, incOptions)

    private def createCompiler(sv: String, incOptions: IncOptions): CompilerSetup = {
      val si = scalaInstance(sv, setup.baseDir, Logger.Null)
      val bridge = getCompilerBridge(setup.baseDir, Logger.Null, sv)
      setup.createCompiler(sv, si, bridge, incOptions, log)
    }
  }

  lazy val bridges: List[ScalaBridge] = {
    import sbt.internal.inc.ZincBuildInfo._
    val compilerBridge210 = ScalaBridge(scalaVersion210, scalaJars210.toList, classDirectory210)
    val compilerBridge211 = ScalaBridge(scalaVersion211, scalaJars211.toList, classDirectory211)
    val compilerBridge212 = ScalaBridge(scalaVersion212, scalaJars212.toList, classDirectory212)
    val compilerBridge213 = ScalaBridge(scalaVersion213, scalaJars213.toList, classDirectory213)
    val compilerBridge213_next =
      ScalaBridge(scalaVersion213_next, scalaJars213_next.toList, classDirectory213_next)
    List(
      compilerBridge210,
      compilerBridge211,
      compilerBridge212,
      compilerBridge213,
      compilerBridge213_next
    )
  }

  // Create a provider that uses the bridges from the classes directory of the projects
  def getZincProvider(targetDir: Path, log: Logger): CompilerBridgeProvider =
    new ConstantBridgeProvider(bridges, targetDir)
}
