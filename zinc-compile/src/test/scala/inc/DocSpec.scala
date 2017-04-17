package sbt
package inc

import java.io.File

import sbt.io.IO
import sbt.internal.inc.javac.{ JavaCompiler, JavaTools, Javadoc }
import sbt.internal.inc.javac.JavaCompilerSpec
import sbt.internal.inc.{ LoggerReporter, UnitSpec }
import xsbti.compile.IncToolOptionsUtil
import sbt.util.CacheStoreFactory

class DocSpec extends UnitSpec {
  "Doc.cachedJavadoc" should "generate Java Doc" in {
    IO.withTemporaryDirectory { cacheDir =>
      IO.withTemporaryDirectory { out =>
        val javadoc = Doc.cachedJavadoc("Foo", CacheStoreFactory(cacheDir), local)
        javadoc.run(List(knownSampleGoodFile), Nil, out, Nil, IncToolOptionsUtil.defaultIncToolOptions(), log, reporter)
        assert((new File(out, "index.html")).exists)
        assert((new File(out, "good.html")).exists)
      }
    }
  }
  it should "generate cache input" in {
    IO.withTemporaryDirectory { cacheDir =>
      IO.withTemporaryDirectory { out =>
        val javadoc = Doc.cachedJavadoc("Foo", CacheStoreFactory(cacheDir), local)
        javadoc.run(List(knownSampleGoodFile), Nil, out, Nil, IncToolOptionsUtil.defaultIncToolOptions(), log, reporter)
        assert((new File(cacheDir, "inputs")).exists)
      }
    }
  }

  def local =
    JavaTools(
      JavaCompiler.local.getOrElse(sys.error("This test cannot be run on a JRE, but only a JDK.")),
      Javadoc.local.getOrElse(Javadoc.fork())
    )
  lazy val reporter = new LoggerReporter(10, log)
  def knownSampleGoodFile =
    new File(classOf[JavaCompilerSpec].getResource("good.java").toURI)
}
