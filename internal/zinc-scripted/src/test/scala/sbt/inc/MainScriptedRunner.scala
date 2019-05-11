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

import java.io.File

import sbt.io.IO
import sbt.internal.inc._

// WARNING: called via reflection
final class MainScriptedRunner {
  def run(
      resourceBaseDirectory: File,
      bufferLog: Boolean,
      compileToJar: Boolean,
      tests: Array[String]
  ): Unit = {
    IO.withTemporaryDirectory { tempDir =>
      // Create a global temporary directory to store the bridge et al
      val handlers = new IncScriptedHandlers(tempDir, compileToJar)
      ScriptedRunnerImpl.run(resourceBaseDirectory, bufferLog, tests, handlers, 4)
    }
  }
}
