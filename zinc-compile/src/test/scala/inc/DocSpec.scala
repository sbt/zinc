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
import java.nio.file.{ Path, Paths }

import sbt.inc.Doc.JavadocGenerationFailed
import sbt.io.IO
import sbt.internal.inc.javac.{ JavaCompiler, JavaTools, Javadoc }
import sbt.internal.inc.javac.JavaCompilerSpec
import sbt.internal.inc.ManagedLoggedReporter
import sbt.internal.inc.PlainVirtualFile
import sbt.internal.inc.PlainVirtualFileConverter.converter
import sbt.internal.inc.UnitSpec
import xsbti.compile.IncToolOptionsUtil
import sbt.util.CacheStoreFactory

import org.scalatest.{ Assertion, Succeeded }

class DocSpec extends UnitSpec {
  behavior of "Doc.cachedJavadoc"

  it should "generate Javadoc" in {
    docAndAssert(knownSampleGoodFile) { (_, out) =>
      assert((new File(out, "index.html")).exists)
      assert((new File(out, "good.html")).exists)
    }
  }

  it should "generate cache input" in {
    docAndAssert(knownSampleGoodFile) { (cacheDir, _) =>
      assert((new File(cacheDir, "inputs")).exists)
    }
  }

  it should "throw when generating javadoc fails" in {
    assertThrows[JavadocGenerationFailed] {
      docAndAssert(knownSampleBadFile)((_, _) => Succeeded)
    }
  }

  def docAndAssert(file: Path)(assert: (File, File) => Assertion): Assertion = {
    IO.withTemporaryDirectory { cacheDir =>
      IO.withTemporaryDirectory { out =>
        val opts = IncToolOptionsUtil.defaultIncToolOptions()
        Doc
          .cachedJavadoc("Foo", CacheStoreFactory(cacheDir), JavaTools(javac, javadoc))
          .run(List(PlainVirtualFile(file)), Nil, converter, out.toPath, Nil, opts, log, reporter)
        assert(cacheDir, out)
      }
    }
  }

  def javac = JavaCompiler.local.getOrElse(sys.error("Need a JDK, cannot run on a JRE"))
  def javadoc = Javadoc.local.getOrElse(Javadoc.fork())
  lazy val reporter = new ManagedLoggedReporter(10, log)
  def cls = classOf[JavaCompilerSpec]
  def knownSampleGoodFile = Paths.get(cls.getResource("good.java").toURI)
  def knownSampleBadFile = Paths.get(cls.getResource("bad.java").toURI)
}
