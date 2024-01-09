package sbt.inc.consistent

import java.io.File
import java.util.Arrays
import org.scalatest.funsuite.AnyFunSuite
import sbt.internal.inc.consistent.ConsistentFileAnalysisStore
import sbt.internal.inc.{ Analysis, FileAnalysisStore }
import sbt.io.IO
import xsbti.compile.{ AnalysisContents, AnalysisStore }
import xsbti.compile.analysis.ReadWriteMappers

class ConsistentAnalysisFormatIntegrationSuite extends AnyFunSuite {
  val data =
    Seq("compiler.zip", "library.zip", "reflect.zip").map(f => new File("../../../test-data", f))

  test("Consistent output") {
    for (d <- data) {
      assert(d.exists())
      val api = read(FileAnalysisStore.binary(d))
      val f1 = write("cbin1.zip", api)
      val f2 = write("cbin2.zip", api)
      assert(Arrays.equals(IO.readBytes(f1), IO.readBytes(f2)), s"same output for $d")
    }
  }

  test("Roundtrip") {
    for (d <- data) {
      assert(d.exists())
      val api = read(FileAnalysisStore.binary(d))
      val f1 = write("cbin1.zip", api)
      val api2 = read(ConsistentFileAnalysisStore.binary(f1, ReadWriteMappers.getEmptyMappers))
      val f2 = write("cbin2.zip", api2)
      assert(Arrays.equals(IO.readBytes(f1), IO.readBytes(f2)), s"same output for $d")
    }
  }

  test("Unsorted roundtrip") {
    for (d <- data) {
      assert(d.exists())
      val api = read(FileAnalysisStore.binary(d))
      val f1 = write("cbin1.zip", api)
      val api2 = read(ConsistentFileAnalysisStore.binary(f1, ReadWriteMappers.getEmptyMappers))
      val f2 = write("cbin2.zip", api2, sort = false)
      val api3 = read(ConsistentFileAnalysisStore.binary(f2, ReadWriteMappers.getEmptyMappers))
      val f3 = write("cbin3.zip", api3)
      assert(Arrays.equals(IO.readBytes(f1), IO.readBytes(f3)), s"same output for $d")
    }
  }

  def read(store: AnalysisStore): AnalysisContents = {
    val api = store.unsafeGet()
    // Force loading of companion file and check that the companion data is present:
    assert(api.getAnalysis.asInstanceOf[Analysis].apis.internal.head._2.api() != null)
    assert(api.getMiniSetup.storeApis())
    api
  }

  def write(name: String, api: AnalysisContents, sort: Boolean = true): File = {
    val out = new File(IO.temporaryDirectory, name)
    if (out.exists()) IO.delete(out)
    ConsistentFileAnalysisStore.binary(out, ReadWriteMappers.getEmptyMappers, sort).set(api)
    out
  }
}
