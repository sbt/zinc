/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc.cached

import java.io.File
import java.nio.file.{ Files, Path, Paths }

import org.scalatest.BeforeAndAfterAll
import sbt.internal.inc.cached.{ CacheAwareStore, CacheProvider }
import sbt.internal.inc.{ Analysis, AnalysisStore, FileBasedStore}
import sbt.io.IO
import sbt.inc.BaseCompilerSpec

abstract class CommonCachedCompilation(name: String)
    extends BaseCompilerSpec
    with BeforeAndAfterAll {

  behavior of name

  object SetupCommons {
    val sourceDir1 = Paths.get("src/main/scala")
    val sourceDir2 = Paths.get("src/main/generated-scala")
    val sourceDir3 = Paths.get("src-old")

    object Sources {
      val A = Paths.get("a/A.scala")
      val AA = Paths.get("a/AA.scala")
      val B = Paths.get("b/B.scala")
      val C = Paths.get("b/c/C.scala")
    }

    object Bin {
      val Jar1 = Paths.get("jar1.jar")
      val Jar2 = Paths.get("jar2.jar")
      val ClassesDir1 = Paths.get("classesDep1.zip")
    }

    import Sources._

    val baseSourceMapping = Map(
      sourceDir1 -> Seq(A, C),
      sourceDir2 -> Seq(AA),
      sourceDir3 -> Seq(B)
    )

    val baseCpMapping: Seq[Path] = Seq(Bin.Jar1, Bin.Jar2, Bin.ClassesDir1)
  }

  var remoteProject: ProjectSetup = _
  var remoteCompilerSetup: CompilerSetup = _
  var remoteAnalysisStore: AnalysisStore = _

  def remoteCacheProvider(): CacheProvider

  /**
    * Redefine what `currentManaged` is based on the project base location.
    *
    * The `lib_managed` folder stores all the cached libraries. It's an equivalent of
    * `.ivy2` or `.coursier`. The analysis file provides simple read and write mappers
    * that turn all paths relative to a **concrete position**.
    *
    * Note that the assumption that all paths can be made relative to a concrete position
    * is not correct. There is no guarantee that the paths of the caches, the artifacts,
    * the classpath entries, the outputs, et cetera share the same prefix.
    *
    * Such assumption is broken in our test infrastructure: `lib_managed` does not share
    * the same prefix, and without redefining it, it fails. Note that this is a conscious
    * design decision of the relative read and write mappers. They are focused on simplicity.
    * Build tools that need more careful handling of paths should create their own read and
    * write mappers.
    *
    * @return The file where all the libraries are stored.
    */
  override def currentManaged: File = {
    import sbt.io.syntax.fileToRichFile
    remoteProject.baseLocation.toFile./("target/lib_managed")
  }

  override protected def beforeAll(): Unit = {
    val basePath = IO.createTemporaryDirectory.toPath.resolve("remote")
    Files.createDirectory(basePath)

    remoteProject =
      ProjectSetup(basePath, SetupCommons.baseSourceMapping, SetupCommons.baseCpMapping)
    remoteCompilerSetup = remoteProject.createCompiler()
    remoteAnalysisStore = FileBasedStore.binary(remoteProject.defaultStoreLocation)

    val result = remoteCompilerSetup.doCompileWithStore(remoteAnalysisStore)
    assert(result.hasModified)
    ()
  }

  override protected def afterAll(): Unit =
    IO.delete(remoteProject.baseLocation.toFile.getParentFile)

  it should "provide correct analysis for empty project" in IO.withTemporaryDirectory { tempDir =>
    val cache = remoteCacheProvider().findCache(None)
    assert(cache.nonEmpty)

    val result = cache.get.loadCache(tempDir)

    assert(result.nonEmpty)

    val analysis = result.get._1.asInstanceOf[Analysis]

    val prefix = tempDir.toPath.toString

    // TODO(jvican): Add files that are missing...
    val allFilesToMigrate = analysis.stamps.sources.keySet ++
      analysis.stamps.products.keySet ++ analysis.stamps.binaries.keySet

    val globalTmpPrefix = tempDir.getParentFile.toPath.toString
    def isGlobal(f: File): Boolean =
      !f.toPath.toString.startsWith(globalTmpPrefix)

    allFilesToMigrate.filterNot(isGlobal).foreach { source =>
      source.toString should startWith(prefix)
    }
  }

  it should "not run compilation in local project" in namedTempDir("localProject") { projectRoot =>
    val projectSetup =
      ProjectSetup(projectRoot.toPath, SetupCommons.baseSourceMapping, SetupCommons.baseCpMapping)
    val localStore = FileBasedStore.binary(new File(projectRoot, "inc_data.zip"))
    val cache = CacheAwareStore(localStore, remoteCacheProvider(), projectRoot)

    val compiler = projectSetup.createCompiler()
    val result = compiler.doCompileWithStore(cache)

    assert(!result.hasModified)
  }

  private def namedTempDir[T](name: String)(op: File => T): T = {
    IO.withTemporaryDirectory { file =>
      val dir = new File(file, name)
      dir.mkdir()
      op(dir)
    }
  }
}
