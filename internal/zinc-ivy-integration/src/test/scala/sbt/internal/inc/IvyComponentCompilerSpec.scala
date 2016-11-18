package sbt.internal.inc

import sbt.io.IO
import sbt.util.Logger

class IvyComponentCompilerSpec extends BridgeProviderSpecification {

  val scala211 = "2.11.8"

  it should "compile the bridge for Scala 2.11" in {
    IO.withTemporaryDirectory { tempDir =>
      getCompilerBridge(tempDir, Logger.Null, scala211) should exist
    }
  }

}
