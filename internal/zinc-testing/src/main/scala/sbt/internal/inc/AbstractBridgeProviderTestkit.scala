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

import java.nio.file.{ Files, FileAlreadyExistsException, Path, StandardCopyOption }
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider

trait AbstractBridgeProviderTestkit extends LogTestkit {
  def getZincProvider(targetDir: Path, log: Logger): CompilerBridgeProvider

  def getCompilerBridge(targetDir: Path, log: Logger, scalaVersion: String): Path =
    AbstractBridgeProviderTestkit.lock.synchronized {
      val provider = getZincProvider(targetDir, log)
      val scalaInstance = provider.fetchScalaInstance(scalaVersion, log)
      val bridge = provider.fetchCompiledBridge(scalaInstance, log).toPath
      val target = targetDir.resolve(s"target-bridge-$scalaVersion.jar")
      if (!Files.exists(target)) {
        try {
          Files.copy(bridge, target, StandardCopyOption.REPLACE_EXISTING)
        } catch {
          case _: FileAlreadyExistsException => ()
        }
      }
      target
    }

  import xsbti.compile.ScalaInstance
  def scalaInstance(scalaVersion: String, targetDir: Path, logger: Logger): ScalaInstance =
    getZincProvider(targetDir, logger).fetchScalaInstance(scalaVersion, logger)

  implicit class RichPath(p: Path) {
    def /(sub: String): Path = p.resolve(sub)
  }
}

object AbstractBridgeProviderTestkit {
  private val lock = new AnyRef
}
