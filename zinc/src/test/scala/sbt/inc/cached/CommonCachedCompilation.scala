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

package sbt.inc
package cached

import java.io.File
import java.nio.file.{ Files, Path, Paths }

import org.scalatest.BeforeAndAfterAll
import sbt.internal.inc.cached.{ CacheAwareStore, CacheProvider }
import sbt.internal.inc.{ Analysis, FileAnalysisStore }
import sbt.io.IO
import xsbti.VirtualFileRef
import xsbti.compile.AnalysisStore

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

  var remoteProject: TestProjectSetup = _
  var remoteCompilerSetup: TestProjectSetup.CompilerSetup = _
  var remoteAnalysisStore: AnalysisStore = _

  def remoteCacheProvider(): CacheProvider

  override protected def beforeAll(): Unit = {
    //don't use temp dir because it might end up on different drive (on windows) and this later fails in sbt.internal.inc.binary.converters.ProtobufWriters due to inability to relativize paths
    val basePath =
      IO.createUniqueDirectory(new java.io.File("target").getAbsoluteFile).toPath.resolve("remote")
    Files.createDirectory(basePath)

    remoteProject =
      TestProjectSetup(basePath, SetupCommons.baseSourceMapping, SetupCommons.baseCpMapping)
    remoteCompilerSetup = remoteProject.createCompiler()
    remoteAnalysisStore = FileAnalysisStore.binary(remoteProject.defaultStoreLocation.toFile)

    val result = remoteCompilerSetup.doCompileWithStore(remoteAnalysisStore)
    assert(result.hasModified)
    ()
  }

  override protected def afterAll(): Unit = {
    IO.delete(remoteProject.baseLocation.toFile.getParentFile)
    if (remoteCompilerSetup != null) remoteCompilerSetup.close()
  }

  it should "provide correct analysis for empty project" in IO.withTemporaryDirectory { tempDir =>
    val cache = remoteCacheProvider().findCache(None)
    assert(cache.nonEmpty)
    val result = cache.get.loadCache(tempDir)
    assert(result.nonEmpty)

    val analysis: Analysis = result.get._1.asInstanceOf[Analysis]
    val prefix = tempDir.toPath.toString

    val stamps = analysis.stamps
    // val productStamps = stamps.products.keySet
    val fileStamps = stamps.libraries.keySet
    // val vfileStamps = stamps.sources.keySet
    // val outputs = analysis.compilations.allCompilations.map(_.getOutput.getSingleOutput.get)
    val allFilesToMigrate = fileStamps // ++ outputs

    val globalTmpPrefix = tempDir.getParentFile.toPath.toString
    def isGlobal(f: VirtualFileRef): Boolean =
      !f.id.toString.startsWith(globalTmpPrefix)

    allFilesToMigrate.filterNot(isGlobal).foreach { source =>
      source.toString should startWith(prefix)
    }
  }

  it should "not run compilation in local project" in namedTempDir("localProject") { projectRoot =>
    val projectSetup =
      TestProjectSetup(
        projectRoot.toPath,
        SetupCommons.baseSourceMapping,
        SetupCommons.baseCpMapping
      )
    val localStore = FileAnalysisStore.binary(new File(projectRoot, "inc_data.zip"))
    val cache = CacheAwareStore(localStore, remoteCacheProvider(), projectRoot)

    val compiler = projectSetup.createCompiler()
    try {
      val result = compiler.doCompileWithStore(cache)

      assert(!result.hasModified)
    } finally {
      compiler.close()
    }
  }

  private def namedTempDir[T](name: String)(op: File => T): T = {
    IO.withTemporaryDirectory { file =>
      val dir = new File(file, name)
      dir.mkdir()
      op(dir)
    }
  }
}
