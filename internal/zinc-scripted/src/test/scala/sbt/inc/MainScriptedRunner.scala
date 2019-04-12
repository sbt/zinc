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
