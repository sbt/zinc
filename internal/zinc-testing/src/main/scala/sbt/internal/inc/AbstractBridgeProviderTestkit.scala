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
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider

trait AbstractBridgeProviderTestkit {
  def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider

  def getCompilerBridge(targetDir: File, log: Logger, scalaVersion: String): File = {
    val provider = getZincProvider(targetDir, log)
    val scalaInstance = provider.fetchScalaInstance(scalaVersion, log)
    val bridge = provider.fetchCompiledBridge(scalaInstance, log)
    val target = targetDir / s"target-bridge-$scalaVersion.jar"
    IO.copyFile(bridge, target)
    target
  }

  import xsbti.compile.ScalaInstance
  def scalaInstance(scalaVersion: String, targetDir: File, logger: Logger): ScalaInstance =
    getZincProvider(targetDir, logger).fetchScalaInstance(scalaVersion, logger)
}
