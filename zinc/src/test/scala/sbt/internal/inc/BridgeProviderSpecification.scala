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

package sbt.internal.inc

import java.nio.file.Path
import sbt.inc.{ ScalaBridge, ConstantBridgeProvider }
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider
import sbt.internal.inc.ZincBuildInfo._

class BridgeProviderSpecification extends UnitSpec with BridgeProviderTestkit {}

trait BridgeProviderTestkit extends AbstractBridgeProviderTestkit {
  lazy val bridges: List[ScalaBridge] = {
    val compilerBridge210 =
      ScalaBridge(scalaVersion210, scalaJars210.toList, Left(classDirectory210))
    val compilerBridge211 =
      ScalaBridge(scalaVersion211, scalaJars211.toList, Left(classDirectory211))
    val compilerBridge212 =
      ScalaBridge(scalaVersion212, scalaJars212.toList, Left(classDirectory212))
    val compilerBridge213 =
      ScalaBridge(scalaVersion213, scalaJars213.toList, Left(classDirectory213))
    val bridge213Bin =
      ScalaBridge(scalaVersion213Bin, scalaJars213Bin.toList, Right(compilerBridge213Bin))
    val bridge3Bin =
      ScalaBridge(
        scalaVersion3Bin,
        scalaJars3Bin.toList.filterNot(_.getName.startsWith("compiler-interface")),
        Right(compilerBridge3Bin)
      )
    List(
      compilerBridge210,
      compilerBridge211,
      compilerBridge212,
      compilerBridge213,
      bridge213Bin,
      bridge3Bin,
    )
  }

  /**
   * Emulate sbt's switch command, and accept 3.x notation.
   */
  def switchScalaVersion(sv: Option[String]): String =
    sv match {
      case Some("2.10.x") => scalaVersion210
      case Some("2.11.x") => scalaVersion211
      case Some("2.12.x") => scalaVersion212
      case Some("2.13.x") => scalaVersion213
      case Some("2.13.y") => scalaVersion213Bin
      case Some("3.x")    => scalaVersion3Bin
      case Some(sv)       => sv
      case None           => scalaVersion212
    }

  // Create a provider that uses the bridges from the classes directory of the projects
  def getZincProvider(targetDir: Path, log: Logger): CompilerBridgeProvider =
    new ConstantBridgeProvider(bridges, targetDir)
}
