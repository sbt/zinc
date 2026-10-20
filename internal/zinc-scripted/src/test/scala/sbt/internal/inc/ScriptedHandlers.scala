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

import sbt.internal.scripted.*
import sbt.internal.util.ManagedLogger

class IncScriptedHandlers(globalCacheDir: Path, compileToJar: Boolean) extends HandlersProvider:
  def getHandlers(config: ScriptConfig): Map[Char, StatementHandler] = Map(
    '$' -> new ZincFileCommands(config.testDirectory, config.logger),
    '#' -> CommentHandler,
    '>' -> {
      val logger = config.logger().asInstanceOf[ManagedLogger]
      new IncHandler(config.testDirectory().toPath, globalCacheDir, logger, compileToJar)
    }
  )
