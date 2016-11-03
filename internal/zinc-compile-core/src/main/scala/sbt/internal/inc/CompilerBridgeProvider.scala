package sbt
package internal
package inc

import java.io.File
import sbt.util.Logger

trait CompilerBridgeProvider {
  def apply(scalaInstance: xsbti.compile.ScalaInstance, log: Logger): File
}
object CompilerBridgeProvider {
  def constant(file: File): CompilerBridgeProvider = new CompilerBridgeProvider {
    def apply(scalaInstance: xsbti.compile.ScalaInstance, log: Logger): File = file
  }
}
