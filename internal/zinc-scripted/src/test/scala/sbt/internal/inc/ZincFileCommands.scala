package sbt.internal.inc

import java.io.File

import sbt.internal.scripted.FileCommands

class ZincFileCommands(baseDirectory: File) extends FileCommands(baseDirectory) {
  override def commandMap: Map[String, List[String] => Unit] = {
    super.commandMap + {
      "pause" noArg {
        // Redefine pause not to use `System.console`, which is too restrictive
        println(s"Pausing in $baseDirectory. Press enter to continue.")
        scala.io.StdIn.readLine()
        println("Restarting the execution.")
      }
    }
  }
}
