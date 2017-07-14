/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc.cached

import java.io.File
import java.nio.file.Path

import sbt.internal.inc.cached._
import sbt.io.IO
import xsbti.compile.{ MiniSetup, CompileAnalysis }

class ExportedCacheSpec extends CommonCachedCompilation("Exported Cache") {

  var cacheLocation: Path = _
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cacheLocation = remoteProject.baseLocation.resolveSibling("cache")
    val remoteCache = new ExportableCache(cacheLocation)

    remoteCache.exportCache(remoteProject.baseLocation.toFile, remoteAnalysisStore) match {
      case None => fail(s"Analysis file was not exported.")
      case _    =>
    }
  }

  override def remoteCacheProvider(): CacheProvider = new CacheProvider {
    override def findCache(
        previous: Option[(CompileAnalysis, MiniSetup)]): Option[CompilationCache] =
      Some(new ExportableCache(cacheLocation))
  }

  private class NonEmptyOutputFixture(tmpDir: File) {
    private def nonEmptyFile(parent: File, name: String) = {
      val f = new File(parent, name)
      IO.write(f, "some-text")
      f
    }

    val plainFileProjectMock = new File(tmpDir, "plain-file")
    val plainFileAsOutput =
      nonEmptyFile(plainFileProjectMock, remoteProject.defaultClassesDir.getName)

    val nonemptyDirProjectMock = new File(tmpDir, "non-empty-dir")
    val nonemptyDirOutputDir =
      new File(nonemptyDirProjectMock, remoteProject.defaultClassesDir.getName)
    val contentFile: File = nonEmptyFile(nonemptyDirOutputDir, "some-file")
    val contentDirectory = new File(nonemptyDirOutputDir, "some-dir")
    val contentDirectoryMember = nonEmptyFile(contentDirectory, "another")
    val contentDirectoryClassMember = nonEmptyFile(contentDirectory, "another.class")
  }

  it should "fail non empty output with FailOnNonEmpty" in IO.withTemporaryDirectory { tmpDir =>
    val fixture = new NonEmptyOutputFixture(tmpDir)
    import fixture._

    intercept[IllegalStateException] {
      new ExportableCache(cacheLocation, cleanOutputMode = FailOnNonEmpty)
        .loadCache(nonemptyDirProjectMock)
    }

    assert(contentFile.exists())
    assert(contentDirectory.exists())
    assert(contentDirectory.list().nonEmpty)
  }

  it should "fail on plain file as classes dir" in IO.withTemporaryDirectory { tmpDir =>
    val fixture = new NonEmptyOutputFixture(tmpDir)
    import fixture._

    intercept[IllegalStateException] {
      new ExportableCache(cacheLocation).loadCache(plainFileProjectMock)
    }
    assert(plainFileProjectMock.exists())
  }

  it should "handle output with CleanOutput" in IO.withTemporaryDirectory { tmpDir =>
    val fixture = new NonEmptyOutputFixture(tmpDir)
    import fixture._

    new ExportableCache(cacheLocation).loadCache(nonemptyDirProjectMock)

    assert(!contentFile.exists())
    assert(!contentDirectory.exists())
  }

  it should "handle output with CleanClasses" in IO.withTemporaryDirectory { tmpDir =>
    val fixture = new NonEmptyOutputFixture(tmpDir)
    import fixture._

    new ExportableCache(cacheLocation, cleanOutputMode = CleanClasses)
      .loadCache(nonemptyDirProjectMock)

    assert(contentFile.exists())
    assert(contentDirectory.exists())
    assert(contentDirectoryMember.exists())
    assert(!contentDirectoryClassMember.exists())
  }

}
