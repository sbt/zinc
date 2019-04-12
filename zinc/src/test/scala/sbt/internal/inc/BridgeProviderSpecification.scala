package sbt.internal.inc

import java.io.File
import sbt.inc.{ ScalaBridge, ConstantBridgeProvider }
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider

class BridgeProviderSpecification extends UnitSpec with AbstractBridgeProviderTestkit {
  val bridges: List[ScalaBridge] = {
    import sbt.internal.inc.ZincBuildInfo._
    val compilerBridge210 = ScalaBridge(scalaVersion210, scalaJars210, classDirectory210)
    val compilerBridge211 = ScalaBridge(scalaVersion211, scalaJars211, classDirectory211)
    val compilerBridge212 = ScalaBridge(scalaVersion212, scalaJars212, classDirectory212)
    val compilerBridge213 = ScalaBridge(scalaVersion213, scalaJars213, classDirectory213)
    List(compilerBridge210, compilerBridge211, compilerBridge212, compilerBridge213)
  }

  // Create a provider that uses the bridges from the classes directory of the projects
  def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider =
    new ConstantBridgeProvider(bridges, targetDir)
}
