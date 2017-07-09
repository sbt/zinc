package sbt.inc

import java.io.File
import java.net.URLClassLoader
import java.util.Optional

import sbt.internal.inc.{ ScalaInstance => _, _ }
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.{ InterfaceUtil, Logger }
import JavaInterfaceUtil.{ EnrichOption, EnrichOptional }
import TestResource._
import sbt.internal.inc.classpath.ClassLoaderCache
import xsbti.compile.{ ClassFileManager, CompilerBridgeProvider, _ }

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
      val fileStore2 = AnalysisStore.cached(FileBasedStore.binary(cacheFile2))

      // Prepare the initial compilation
      val sub1Directory = tempDir / "sub1"
      IO.createDirectory(sub1Directory)
      val targetDir = sub1Directory / "target"
      val cacheFile = targetDir / "inc_compile.zip"
      val fileStore = AnalysisStore.cached(FileBasedStore.binary(cacheFile))
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
      val skipBinaryChangeDetection = false
      val emptyLookup = new ExternalLookup {
        override def changedSources(previous: CompileAnalysis): Option[Changes[File]] = None
        override def changedBinaries(previous: CompileAnalysis): Option[Set[File]] =
          if (skipBinaryChangeDetection) Some(Set.empty) else None
        override def removedProducts(previous: CompileAnalysis): Option[Set[File]] = None
        override def shouldDoIncrementalCompilation(changedClasses: Set[String],
                                                    analysis: CompileAnalysis): Boolean = true
      }
      val incOptions = IncOptionsUtil
        .defaultIncOptions()
        .withApiDebug(true)
        .withExternalHooks(new ExternalHooks() {
          def externalLookup: Optional[ExternalHooks.Lookup] = Optional.of(emptyLookup)
          def externalClassFileManager: Optional[ClassFileManager] = Optional.empty()
        })

      val reporter = new LoggerReporter(maxErrors, log, identity)
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
      fileStore.set(result0.analysis match { case a: Analysis => a }, result0.setup)
      val prev1 = fileStore.get match {
        case Some((a, s)) => new PreviousResult(Optional.of(a), Optional.of(s))
        case _            => sys.error("previous is not found")
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
      fileStore.set(result1.analysis match { case a: Analysis => a }, result1.setup)

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
      fileStore2.set(result2.analysis match { case a: Analysis => a }, result2.setup)

      // Actual test
      val knownSampleGoodFile = sub1Directory / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val sources3 = Array(knownSampleGoodFile, dependerFile, depender2File)
      val prev = fileStore.get match {
        case Some((a, s)) => new PreviousResult(Optional.of(a), Optional.of(s))
        case _            => sys.error("previous is not found")
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
      fileStore.set(a3, result3.setup)

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
