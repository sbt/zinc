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

import java.net.URLClassLoader
import java.util.Optional
import java.nio.file.{ Files, Path, Paths, StandardCopyOption }

import sbt.internal.inc.{ ScalaInstance => _, FileAnalysisStore => _, _ }
import sbt.io.IO
import sbt.util.Logger
import JavaInterfaceUtil.{ EnrichOption, EnrichOptional }
import TestResource._
import sbt.internal.inc.classpath.ClassLoaderCache
import xsbti.compile._
import xsbti.VirtualFile

class MultiProjectIncrementalSpec extends BridgeProviderSpecification {
  val scalaVersion = "2.12.11"
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val maxErrors = 100

  "incremental compiler" should "detect shadowing" in {
    IO.withTemporaryDirectory { tempDir =>
      val localBoot = Paths.get(sys.props("user.home")).resolve(".sbt").resolve("boot")
      val javaHome = Paths.get(sys.props("java.home"))
      val rootPaths =
        Map("BASE" -> tempDir.toPath, "SBT_BOOT" -> localBoot, "JAVA_HOME" -> javaHome)
      val converter = new MappedFileConverter(rootPaths, true)

      // Second subproject
      val sub2Directory = tempDir.toPath / "sub2"
      Files.createDirectories(sub2Directory)
      Files.createDirectories(sub2Directory / "src")
      val targetDir2 = sub2Directory / "target"
      val earlyOutput2 = targetDir2 / "early-output.jar"
      val cacheFile2 = targetDir2 / "inc_compile.zip"
      val fileStore2 = AnalysisStore.getCachedStore(FileAnalysisStore.getDefault(cacheFile2.toFile))
      val earlyCacheFile2 = targetDir2 / "early" / "inc_compile.zip"
      val earlyAnalysisStore2 =
        AnalysisStore.getCachedStore(FileAnalysisStore.getDefault(earlyCacheFile2.toFile))

      // Prepare the initial compilation
      val sub1Directory = tempDir.toPath / "sub1"
      Files.createDirectories(sub1Directory)
      Files.createDirectories(sub1Directory / "src")
      Files.createDirectories(sub1Directory / "lib")
      val targetDir = sub1Directory / "target"
      val earlyOutput = targetDir / "early-output.jar"
      val cacheFile = targetDir / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.getDefault(cacheFile.toFile))
      val earlyCacheFile = targetDir / "early" / "inc_compile.zip"
      val earlyAnalysisStore =
        AnalysisStore.getCachedStore(FileAnalysisStore.getDefault(earlyCacheFile.toFile))
      val dependerFile = sub1Directory / "src" / "Depender.scala"
      Files.copy(dependerFile0, dependerFile, StandardCopyOption.REPLACE_EXISTING)
      val depender2File = sub1Directory / "src" / "Depender2.scala"
      Files.copy(depender2File0, depender2File, StandardCopyOption.REPLACE_EXISTING)
      val binarySampleFile = sub1Directory / "lib" / "sample-binary_2.12-0.1.jar"
      Files.copy(binarySampleFile0, binarySampleFile, StandardCopyOption.REPLACE_EXISTING)
      val sources = Array(dependerFile)
      // uncomment this to see the debug log
      // log.setLevel(Level.Debug)
      val noLogger = Logger.Null
      val compilerBridge = getCompilerBridge(sub1Directory, noLogger, scalaVersion)
      val si = scalaInstance(scalaVersion, sub1Directory, noLogger)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val prev0 = compiler.emptyPreviousResult
      val cp: Vector[VirtualFile] =
        (si.allJars.toVector.map(_.toPath) ++ Vector(earlyOutput, targetDir2, binarySampleFile))
          .map(PlainVirtualFile(_))
      val emptyPrev = compiler.emptyPreviousResult
      val lookup = new PerClasspathEntryLookupImpl(
        {
          case x
              if converter.toPath(x).toAbsolutePath == targetDir.toAbsolutePath ||
                converter.toPath(x).toAbsolutePath == earlyOutput.toAbsolutePath =>
            prev0.analysis.toOption
          case x if converter.toPath(x).toAbsolutePath == targetDir2.toAbsolutePath =>
            emptyPrev.analysis.toOption
          case _ => None
        },
        Locate.definesClass
      )
      val incOptions = IncOptions
        .of()
        .withApiDebug(true)
      val stamper = Stamps.timeWrapLibraryStamps(converter)
      val vs = sources map converter.toVirtualFile
      val reporter = new ManagedLoggedReporter(maxErrors, log)
      val setup = compiler.setup(
        lookup,
        skip = false,
        cacheFile,
        CompilerCache.fresh,
        incOptions,
        reporter,
        progress = None,
        earlyAnalysisStore = Some(earlyAnalysisStore),
        Array()
      )
      val in = compiler.inputs(
        cp.toArray,
        vs,
        targetDir,
        Some(earlyOutput),
        Array(),
        Array(),
        maxErrors,
        Array(),
        CompileOrder.Mixed,
        cs,
        setup,
        prev0,
        Optional.empty(),
        converter,
        stamper
      )
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
      val vs1 = sources1 map converter.toVirtualFile
      val in1 = compiler.inputs(
        cp.toArray,
        vs1,
        targetDir,
        Some(earlyOutput),
        Array(),
        Array(),
        maxErrors,
        Array(),
        CompileOrder.Mixed,
        cs,
        setup,
        prev1,
        Optional.empty(),
        converter,
        stamper
      )
      // This registers `test.pkg.Ext2` as the class name on the binary stamp,
      // which means `test.pkg.Ext1` is no longer in the stamp.
      val result1 = compiler.compile(in1, log)
      fileStore.set(AnalysisContents.create(result1.analysis(), result1.setup()))

      // Second subproject
      val ext1File = sub2Directory / "src" / "Ext1.scala"
      Files.copy(ext1File0, ext1File, StandardCopyOption.REPLACE_EXISTING)
      val sources2 = Array(ext1File)
      val vs2 = sources2 map converter.toVirtualFile

      val cp2: Vector[VirtualFile] = (si.allJars.toVector.map(_.toPath) ++ Vector(targetDir2))
        .map(PlainVirtualFile(_))
      val setup2 = compiler.setup(
        lookup,
        skip = false,
        cacheFile2,
        CompilerCache.fresh,
        incOptions,
        reporter,
        progress = None,
        earlyAnalysisStore = Some(earlyAnalysisStore2),
        Array()
      )
      val in2 = compiler.inputs(
        cp2.toArray,
        vs2,
        targetDir2,
        Some(earlyOutput2),
        Array(),
        Array(),
        maxErrors,
        Array(),
        CompileOrder.Mixed,
        cs,
        setup2,
        emptyPrev,
        Optional.empty(),
        converter,
        stamper
      )
      val result2 = compiler.compile(in2, log)
      fileStore2.set(AnalysisContents.create(result2.analysis(), result2.setup()))

      // Actual test
      val knownSampleGoodFile = sub1Directory / "src" / "Good.scala"
      Files.copy(knownSampleGoodFile0, knownSampleGoodFile, StandardCopyOption.REPLACE_EXISTING)
      val sources3 = Array(knownSampleGoodFile, dependerFile, depender2File)
      val vs3 = sources3 map converter.toVirtualFile
      val prev = fileStore.get.toOption match {
        case Some(contents) =>
          PreviousResult.of(Optional.of(contents.getAnalysis), Optional.of(contents.getMiniSetup))
        case _ => sys.error("previous is not found")
      }
      val lookup3 = new PerClasspathEntryLookupImpl(
        {
          case x
              if converter.toPath(x).toAbsolutePath == targetDir.toAbsolutePath ||
                converter.toPath(x).toAbsolutePath == earlyOutput.toAbsolutePath =>
            prev.analysis.toOption
          case x
              if converter.toPath(x).toAbsolutePath == targetDir2.toAbsolutePath ||
                converter.toPath(x).toAbsolutePath == earlyOutput2.toAbsolutePath =>
            Some(result2.analysis)
          case _ => None
        },
        Locate.definesClass
      )
      val setup3 = compiler.setup(
        lookup3,
        skip = false,
        cacheFile,
        CompilerCache.fresh,
        incOptions,
        reporter,
        progress = None,
        earlyAnalysisStore = Some(earlyAnalysisStore),
        Array()
      )
      val in3 = compiler.inputs(
        cp.toArray,
        vs3,
        targetDir,
        Some(earlyOutput),
        Array(),
        Array(),
        maxErrors,
        Array(),
        CompileOrder.Mixed,
        cs,
        setup3,
        prev,
        Optional.empty(),
        converter,
        stamper
      )
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

  def scalaCompiler(instance: ScalaInstance, bridgeJar: Path): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(instance, bridgeJar)
    val classpath = ClasspathOptionsUtil.boot
    val cache = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(instance, bridgeProvider, classpath, _ => (), cache)
  }
}

class PerClasspathEntryLookupImpl(
    am: VirtualFile => Option[CompileAnalysis],
    definesClassLookup: VirtualFile => DefinesClass
) extends PerClasspathEntryLookup {
  override def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] =
    am(classpathEntry).toOptional
  override def definesClass(classpathEntry: VirtualFile): DefinesClass =
    definesClassLookup(classpathEntry)
}

/* Make a jar with the following:

package test.pkg

object Ext1 {
  val x = 1
}

object Ext2 {
  val x = 2
}

object Ext3 {
  val x = 3
}

object Ext4 {
  val x = 4
}

object Ext5 {
  val x = 5
}

object Ext6 {
  val x = 6
}

object Ext7 {
  val x = 7
}

object Ext8 {
  val x = 8
}

object Ext9 {
  val x = 9
}

object Ext10 {
  val x = 10
}

 */
