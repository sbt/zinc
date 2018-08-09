package sbt.inc

import java.io.File
import java.net.URLClassLoader
import java.util.Optional

import sbt.internal.inc.{ ScalaInstance => _, FileAnalysisStore => _, AnalysisStore => _, _ }
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.Logger
import JavaInterfaceUtil.{ EnrichOption, EnrichOptional }
import TestResource._
import sbt.internal.inc.classpath.ClassLoaderCache
import xsbti.compile._

class MultiProjectIncrementalSpec extends BridgeProviderSpecification {
  val scalaVersion = "2.11.8"
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val maxErrors = 100

  "incremental compiler" should "detect shadowing" in {
    IO.withTemporaryDirectory { tempDir =>
      // Second subproject
      val sub2Directory = tempDir / "sub2"
      IO.createDirectory(sub2Directory)
      val targetDir2 = sub2Directory / "target"
      val cacheFile2 = targetDir2 / "inc_compile.zip"
      val fileStore2 = AnalysisStore.getCachedStore(FileAnalysisStore.getDefault(cacheFile2))

      // Prepare the initial compilation
      val sub1Directory = tempDir / "sub1"
      IO.createDirectory(sub1Directory)
      val targetDir = sub1Directory / "target"
      val cacheFile = targetDir / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.getDefault(cacheFile))
      val dependerFile = sub1Directory / "src" / "Depender.scala"
      IO.copyFile(dependerFile0, dependerFile, false)
      val depender2File = sub1Directory / "src" / "Depender2.scala"
      IO.copyFile(depender2File0, depender2File, false)
      val binarySampleFile = sub1Directory / "lib" / "sample-binary_2.11-0.1.jar"
      IO.copyFile(binarySampleFile0, binarySampleFile)
      val sources = Array(dependerFile)
      // uncomment this to see the debug log
      // log.setLevel(Level.Debug)
      val noLogger = Logger.Null
      val compilerBridge = getCompilerBridge(sub1Directory, noLogger, scalaVersion)
      val si = scalaInstance(scalaVersion, sub1Directory, noLogger)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val prev0 = compiler.emptyPreviousResult
      val cp = si.allJars ++ Array(targetDir, targetDir2, binarySampleFile)
      val lookup = new PerClasspathEntryLookupImpl(
        {
          case x if x.getAbsoluteFile == targetDir.getAbsoluteFile => prev0.analysis.toOption
          case _                                                   => None
        },
        Locate.definesClass
      )
      val incOptions = IncOptions
        .of()
        .withApiDebug(true)

      val reporter = new ManagedLoggedReporter(maxErrors, log)
      val setup = compiler.setup(lookup,
                                 skip = false,
                                 cacheFile,
                                 CompilerCache.fresh,
                                 incOptions,
                                 reporter,
                                 None,
                                 Array())
      val in = compiler.inputs(cp,
                               sources,
                               targetDir,
                               Array(),
                               Array(),
                               maxErrors,
                               Array(),
                               CompileOrder.Mixed,
                               cs,
                               setup,
                               prev0)
      // This registers `test.pkg.Ext1` as the class name on the binary stamp
      val result0 = compiler.compile(in, log)
      val contents = AnalysisContents.create(result0.analysis(), result0.setup())
      fileStore.set(contents)
      val prev1 = fileStore.get.toOption match {
        case Some(contents) =>
          PreviousResult.of(Optional.of(contents.getAnalysis), Optional.of(contents.getMiniSetup))
        case _ => sys.error("previous is not found")
      }
      val sources1 = Array(dependerFile, depender2File)
      val in1 = compiler.inputs(cp,
                                sources1,
                                targetDir,
                                Array(),
                                Array(),
                                maxErrors,
                                Array(),
                                CompileOrder.Mixed,
                                cs,
                                setup,
                                prev1)
      // This registers `test.pkg.Ext2` as the class name on the binary stamp,
      // which means `test.pkg.Ext1` is no longer in the stamp.
      val result1 = compiler.compile(in1, log)
      fileStore.set(AnalysisContents.create(result1.analysis(), result1.setup()))

      // Second subproject
      val ext1File = sub2Directory / "src" / "Ext1.scala"
      IO.copyFile(ext1File0, ext1File, false)
      val sources2 = Array(ext1File)
      val emptyPrev = compiler.emptyPreviousResult
      val cp2 = si.allJars ++ Array(targetDir2)
      val lookup2 = new PerClasspathEntryLookupImpl(
        {
          case x if x.getAbsoluteFile == targetDir2.getAbsoluteFile => emptyPrev.analysis.toOption
          case _                                                    => None
        },
        Locate.definesClass
      )
      val setup2 = compiler.setup(lookup2,
                                  skip = false,
                                  cacheFile2,
                                  CompilerCache.fresh,
                                  incOptions,
                                  reporter,
                                  None,
                                  Array())
      val in2 = compiler.inputs(cp2,
                                sources2,
                                targetDir2,
                                Array(),
                                Array(),
                                maxErrors,
                                Array(),
                                CompileOrder.Mixed,
                                cs,
                                setup2,
                                emptyPrev)
      val result2 = compiler.compile(in2, log)
      fileStore2.set(AnalysisContents.create(result2.analysis(), result2.setup()))

      // Actual test
      val knownSampleGoodFile = sub1Directory / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val sources3 = Array(knownSampleGoodFile, dependerFile, depender2File)
      val prev = fileStore.get.toOption match {
        case Some(contents) =>
          PreviousResult.of(Optional.of(contents.getAnalysis), Optional.of(contents.getMiniSetup))
        case _ => sys.error("previous is not found")
      }
      val lookup3 = new PerClasspathEntryLookupImpl(
        {
          case x if x.getAbsoluteFile == targetDir.getAbsoluteFile  => prev.analysis.toOption
          case x if x.getAbsoluteFile == targetDir2.getAbsoluteFile => Some(result2.analysis)
          case _                                                    => None
        },
        Locate.definesClass
      )
      val setup3 = compiler.setup(lookup3,
                                  skip = false,
                                  cacheFile,
                                  CompilerCache.fresh,
                                  incOptions,
                                  reporter,
                                  None,
                                  Array())
      val in3 = compiler.inputs(cp,
                                sources3,
                                targetDir,
                                Array(),
                                Array(),
                                maxErrors,
                                Array(),
                                CompileOrder.Mixed,
                                cs,
                                setup3,
                                prev)
      val result3 = compiler.compile(in3, log)
      val a3 = result3.analysis match { case a: Analysis => a }
      fileStore.set(AnalysisContents.create(a3, result3.setup))

      val allCompilations = a3.compilations.allCompilations
      val recompiledClasses: Seq[Set[String]] = allCompilations map { c =>
        val recompiledClasses = a3.apis.internal.collect {
          case (className, api) if api.compilationTimestamp() == c.getStartTime => className
        }
        recompiledClasses.toSet
      }
      val lastClasses = recompiledClasses.last

      // Depender.scala should be invalidated since it depends on test.pkg.Ext1 from the JAR file,
      // but the class is now shadowed by sub2/target.
      assert(lastClasses contains "test.pkg.Depender")
    }
  }

  it should "recompile the dependent project when the independent project is recompiled" in {
    IO.withTemporaryDirectory { tempDir =>
      // Independent subproject
      val independentDirectory = tempDir / "independent"
      IO.createDirectory(independentDirectory)
      val independentTargetDir = independentDirectory / "target"
      val independentCacheFile = independentTargetDir / "inc_compile.zip"
      val extFile = independentDirectory / "src" / "Ext1.scala"

      // Dependent subproject
      val dependentDirectory = tempDir / "sub1"
      IO.createDirectory(dependentDirectory)
      val dependentTargetDir = dependentDirectory / "target"
      val dependentCacheFile = dependentTargetDir / "inc_compile.zip"
      val dependentFile = dependentDirectory / "src" / "Depender.scala"

      // Shared setup
      val incOptions = IncOptions
        .of()
        .withApiDebug(true)
      val noLogger = Logger.Null
      val compilerBridge = getCompilerBridge(dependentDirectory, noLogger, scalaVersion)
      val si = scalaInstance(scalaVersion, dependentDirectory, noLogger)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val reporter = new ManagedLoggedReporter(maxErrors, log)
      val cache = CompilerCache.createCacheFor(4)

      IO.copyFile(dependerFile0, dependentFile, false)
      val dependentSources = Array(dependentFile)
      val emptyPrev = compiler.emptyPreviousResult
      val dependentClassPath = si.allJars ++ Array(independentTargetDir)
      var prevAnalysis = emptyPrev.analysis.toOption
      val dependentLookup = new PerClasspathEntryLookupImpl(
        {
          case x if x.getAbsoluteFile == independentTargetDir.getAbsoluteFile =>
            prevAnalysis
          case _ => None
        },
        Locate.definesClass
      )

      // Build without the required dependency so that we're left with a cached compiler that
      // will not see binary changes in the independent project
      val dependentSetup = compiler.setup(dependentLookup,
                                          skip = false,
                                          dependentCacheFile,
                                          cache,
                                          incOptions,
                                          reporter,
                                          None,
                                          Array())
      val dependentInput = compiler.inputs(dependentClassPath,
                                           dependentSources,
                                           dependentTargetDir,
                                           Array(),
                                           Array(),
                                           maxErrors,
                                           Array(),
                                           CompileOrder.Mixed,
                                           cs,
                                           dependentSetup,
                                           emptyPrev)
      assertThrows[CompileFailed](compiler.compile(dependentInput, log))

      reporter.reset()
      IO.copyFile(ext1File0, extFile, false)
      val independentSources = Array(extFile)
      val independentClassPath = si.allJars
      val independentSetup = compiler.setup(
        new PerClasspathEntryLookupImpl(_ => None, Locate.definesClass),
        skip = false,
        independentCacheFile,
        cache,
        incOptions,
        reporter,
        None,
        Array()
      )
      val independentInput = compiler.inputs(independentClassPath,
                                             independentSources,
                                             independentTargetDir,
                                             Array(),
                                             Array(),
                                             maxErrors,
                                             Array(),
                                             CompileOrder.Mixed,
                                             cs,
                                             independentSetup,
                                             emptyPrev)

      val result = compiler.compile(independentInput, log)
      assert(result.hasModified)

      // This lets the incremental compiler know that the products have changed.
      prevAnalysis = Some(AnalysisContents.create(result.analysis(), result.setup()).getAnalysis)
      val dependentResult = compiler.compile(dependentInput, log)
      assert(dependentResult.hasModified)
    }
  }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(instance, bridgeJar)
    val classpath = ClasspathOptionsUtil.boot
    val cache = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(instance, bridgeProvider, classpath, _ => (), cache)
  }
}

class PerClasspathEntryLookupImpl(
    am: File => Option[CompileAnalysis],
    definesClassLookup: File => DefinesClass
) extends PerClasspathEntryLookup {
  override def analysis(classpathEntry: File): Optional[CompileAnalysis] =
    am(classpathEntry).toOptional
  override def definesClass(classpathEntry: File): DefinesClass =
    definesClassLookup(classpathEntry)
}

/* Make a jar with the following:

package test.pkg

object Ext1 {
  val x = 1
}
 */
