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
import sbt.internal.inc.ZincBuildInfo.*

class BridgeProviderSpecification extends UnitSpec with BridgeProviderTestkit {}

trait BridgeProviderTestkit extends AbstractBridgeProviderTestkit:
  final val Label210 = "2.10.x"
  final val Label211 = "2.11.x"
  final val Label212 = "2.12.x"
  final val Label213 = "2.13.x"
  final val Label213Bin = "2.13.y"
  final val Label3Bin = "3.x"

  /**
   * The bridges scripted tests can select, by the label a `build.json` gives as `scalaVersion`.
   * Selecting by label rather than by Scala version keeps "2.13.x" and "2.13.y" apart even when
   * the compiler-bridge sources and scala2-sbt-bridge are built for the same Scala version.
   */
  lazy val bridgesByLabel: Map[String, ScalaBridge] = Map(
    Label210 -> ScalaBridge(
      scalaVersion210,
      scalaJars210.toList,
      Left(classDirectory210 +: resourceDirectories210)
    ),
    Label211 -> ScalaBridge(
      scalaVersion211,
      scalaJars211.toList,
      Left(classDirectory211 +: resourceDirectories211)
    ),
    Label212 -> ScalaBridge(
      scalaVersion212,
      scalaJars212.toList,
      Left(classDirectory212 +: resourceDirectories212)
    ),
    Label213 -> ScalaBridge(
      scalaVersion213,
      scalaJars213.toList,
      Left(classDirectory213 +: resourceDirectories213)
    ),
    Label213Bin -> ScalaBridge(
      scalaVersion213Bin,
      scalaJars213Bin.toList,
      Right(compilerBridge213Bin)
    ),
    Label3Bin -> ScalaBridge(
      scalaVersion3Bin,
      scalaJars3Bin.toList.filterNot(_.getName.startsWith("compiler-interface")),
      Right(compilerBridge3Bin)
    ),
  )

  private val bridgeLabels = List(Label210, Label211, Label212, Label213, Label213Bin, Label3Bin)

  lazy val bridges: List[ScalaBridge] = bridgeLabels.map(bridgesByLabel)

  /**
   * Emulate sbt's switch command, and accept 3.x notation. Returns the label that identifies the
   * bridge, which is the Scala version itself when `sv` names one.
   */
  def bridgeLabel(sv: Option[String]): String =
    sv match
      case Some(label) if bridgesByLabel.contains(label) => label
      case Some(version)                                 =>
        bridgeLabels
          .find(bridgesByLabel(_).version == version)
          .getOrElse(sys.error(s"No bridge for Scala $version in ${bridges.map(_.version)}"))
      case None => Label212

  // Create a provider that uses the bridges from the classes directory of the projects
  def getZincProvider(targetDir: Path, log: Logger): CompilerBridgeProvider =
    new ConstantBridgeProvider(bridges, targetDir)

  /** A provider that can only return the bridge `label` selects. */
  def getZincProvider(targetDir: Path, label: String): CompilerBridgeProvider =
    new ConstantBridgeProvider(List(bridgesByLabel(label)), targetDir)
end BridgeProviderTestkit
