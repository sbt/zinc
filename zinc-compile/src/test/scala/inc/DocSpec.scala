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

package sbt
package inc

import java.io.File
import java.nio.file.Paths

import sbt.inc.Doc.JavadocGenerationFailed
import sbt.io.IO
import sbt.internal.inc.javac.{ JavaCompiler, JavaTools, Javadoc }
import sbt.internal.inc.javac.JavaCompilerSpec
import sbt.internal.inc.{
  ManagedLoggedReporter,
  UnitSpec,
  PlainVirtualFile,
  PlainVirtualFileConverter
}
import xsbti.compile.IncToolOptionsUtil
import sbt.util.CacheStoreFactory

class DocSpec extends UnitSpec {
  "Doc.cachedJavadoc" should "generate Java Doc" in {
    IO.withTemporaryDirectory { cacheDir =>
      IO.withTemporaryDirectory { out =>
        val javadoc = Doc.cachedJavadoc("Foo", CacheStoreFactory(cacheDir), local)
        javadoc.run(
          List(PlainVirtualFile(knownSampleGoodFile)),
          Nil,
          PlainVirtualFileConverter.converter,
          out.toPath,
          Nil,
          IncToolOptionsUtil.defaultIncToolOptions(),
          log,
          reporter
        )
        assert((new File(out, "index.html")).exists)
        assert((new File(out, "good.html")).exists)
      }
    }
  }
  it should "generate cache input" in {
    IO.withTemporaryDirectory { cacheDir =>
      IO.withTemporaryDirectory { out =>
        val javadoc = Doc.cachedJavadoc("Foo", CacheStoreFactory(cacheDir), local)
        javadoc.run(
          List(PlainVirtualFile(knownSampleGoodFile)),
          Nil,
          PlainVirtualFileConverter.converter,
          out.toPath,
          Nil,
          IncToolOptionsUtil.defaultIncToolOptions(),
          log,
          reporter
        )
        assert((new File(cacheDir, "inputs")).exists)
      }
    }
  }
  it should "throw when generating javadoc fails" in {
    assertThrows[JavadocGenerationFailed] {
      IO.withTemporaryDirectory { cacheDir =>
        IO.withTemporaryDirectory { out =>
          val javadoc = Doc.cachedJavadoc("Foo", CacheStoreFactory(cacheDir), local)
          javadoc.run(
            List(PlainVirtualFile(knownSampleBadFile)),
            Nil,
            PlainVirtualFileConverter.converter,
            out.toPath,
            Nil,
            IncToolOptionsUtil.defaultIncToolOptions(),
            log,
            reporter
          )
        }
      }
    }
  }

  def local =
    JavaTools(
      JavaCompiler.local.getOrElse(sys.error("This test cannot be run on a JRE, but only a JDK.")),
      Javadoc.local.getOrElse(Javadoc.fork())
    )
  lazy val reporter = new ManagedLoggedReporter(10, log)
  def knownSampleGoodFile =
    Paths.get(classOf[JavaCompilerSpec].getResource("good.java").toURI)
  def knownSampleBadFile =
    Paths.get(classOf[JavaCompilerSpec].getResource("bad.java").toURI)
}
