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

import java.io.File
import sbt.inc.{ ScalaBridge, ConstantBridgeProvider }
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider

class BridgeProviderSpecification extends UnitSpec with AbstractBridgeProviderTestkit {
  val bridges: List[ScalaBridge] = {
    import sbt.internal.inc.ZincBuildInfo._
    val compilerBridge210 = ScalaBridge(scalaVersion210, scalaJars210.toList, classDirectory210)
    val compilerBridge211 = ScalaBridge(scalaVersion211, scalaJars211.toList, classDirectory211)
    val compilerBridge212 = ScalaBridge(scalaVersion212, scalaJars212.toList, classDirectory212)
    val compilerBridge213 = ScalaBridge(scalaVersion213, scalaJars213.toList, classDirectory213)
    List(compilerBridge210, compilerBridge211, compilerBridge212, compilerBridge213)
  }

  // Create a provider that uses the bridges from the classes directory of the projects
  def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider =
    new ConstantBridgeProvider(bridges, targetDir)
}
