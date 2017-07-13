package sbt.internal.inc

import java.io.File

import sbt.internal.scripted.ScriptedRunnerImpl
import sbt.io.IO

class IncScriptedRunner {
  def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String]): Unit = {
    IO.withTemporaryDirectory { tempDir =>
      // Create a global temporary directory to store the bridge et al
      val handlers = new IncScriptedHandlers(tempDir)
      ScriptedRunnerImpl.run(resourceBaseDirectory, bufferLog, tests, handlers);
    }
  }
}
