package sbt.inc.cached

import java.io.File
import java.nio.file.{ Files, Path, Paths }

import org.scalatest.BeforeAndAfterAll
import sbt.inc.BaseCompilerSpec
import sbt.internal.inc.cached.{ CacheAwareStore, CacheProvider }
import sbt.internal.inc.{ Analysis, AnalysisStore, FileBasedStore }
import sbt.io.IO

abstract class CommonCachedCompilation(name: String) extends BaseCompilerSpec with BeforeAndAfterAll {

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

  override protected def beforeAll(): Unit = {
    val basePath = IO.createTemporaryDirectory.toPath.resolve("remote")
    Files.createDirectory(basePath)

    remoteProject = ProjectSetup(basePath, SetupCommons.baseSourceMapping, SetupCommons.baseCpMapping)
    remoteCompilerSetup = remoteProject.createCompiler()
    remoteAnalysisStore = FileBasedStore.apply(remoteProject.defaultStoreLocation)

    val result = remoteCompilerSetup.doCompileWithStore(remoteAnalysisStore)
    assert(result.hasModified)
  }

  override protected def afterAll(): Unit =
    IO.delete(remoteProject.baseLocation.toFile.getParentFile)

  it should "provide correct analysis for empty project" in IO.withTemporaryDirectory {
    tempDir =>
      val cache = remoteCacheProvider().findCache(None)
      assert(cache.nonEmpty)

      val result = cache.get.loadCache(tempDir)

      assert(result.nonEmpty)

      val analysis = result.get._1.asInstanceOf[Analysis]

      val prefix = tempDir.toPath.toString

      val allFilesToMigrate = analysis.stamps.sources.keySet ++
        analysis.stamps.products.keySet ++ analysis.stamps.binaries.keySet

      val globalTmpPrefix = tempDir.getParentFile.toPath.toString
      def isGlobal(f: File): Boolean =
        !f.toPath.toString.startsWith(globalTmpPrefix)

      allFilesToMigrate.filterNot(isGlobal).foreach {
        source => source.toString should startWith(prefix)
      }
  }

  it should "not run compilation in local project" in namedTempDir("localProject") {
    tempDir =>
      val projectSetup = ProjectSetup(tempDir.toPath, SetupCommons.baseSourceMapping, SetupCommons.baseCpMapping)
      val localStore = FileBasedStore(new File(tempDir, "inc_data.zip"))
      val cache = CacheAwareStore(localStore, remoteCacheProvider(), tempDir)

      val compiler = projectSetup.createCompiler()
      val result = compiler.doCompileWithStore(cache)

      assert(!result.hasModified)
  }

  private def namedTempDir[T](name: String)(op: File => T): T = {
    IO.withTemporaryDirectory {
      file =>
        val dir = new File(file, name)
        dir.mkdir()
        op(dir)
    }
  }
}
