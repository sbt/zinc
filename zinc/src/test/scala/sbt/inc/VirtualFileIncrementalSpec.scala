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
import java.io.{ InputStream, ByteArrayInputStream }

import sbt.internal.inc.{ ScalaInstance => _, FileAnalysisStore => _, AnalysisStore => _, _ }
import sbt.io.IO
import sbt.util.{ Level, Logger }
import JavaInterfaceUtil.EnrichOptional
import TestResource._
import sbt.internal.inc.classpath.ClassLoaderCache
import xsbti.{ BasicVirtualFileRef, VirtualFile }
import xsbti.compile._

class VirtualFileIncrementalSpec extends BridgeProviderSpecification {
  import VirtualFileIncrementalSpec._
  override val logLevel = Level.Debug
  val scalaVersion = "2.12.11"
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val maxErrors = 100

  "incremental compiler" should "allow virtual file" in {
    IO.withTemporaryDirectory { tempDir =>
      // Prepare the initial compilation
      val sub1Directory = tempDir.toPath / "sub1"
      Files.createDirectories(sub1Directory)
      Files.createDirectories(sub1Directory / "lib")
      val targetDir = sub1Directory / "target"
      val cacheFile = targetDir / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.getDefault(cacheFile.toFile))
      val dependerFile: VirtualFile =
        StringVirtualFile("src/Depender.scala", """package test.pkg

object Depender {
  val x = test.pkg.Ext1.x
}
""")
      val depender2File = StringVirtualFile("src/Depender2.scala", """package test.pkg

object Depender2 {
  val x = test.pkg.Ext2.x
}
""")
      val binarySampleFile = sub1Directory / "lib" / "sample-binary_2.12-0.1.jar"
      Files.copy(binarySampleFile0, binarySampleFile, StandardCopyOption.REPLACE_EXISTING)
      val noLogger = Logger.Null
      val compilerBridge = getCompilerBridge(sub1Directory, noLogger, scalaVersion)
      val si = scalaInstance(scalaVersion, sub1Directory, noLogger)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val prev0 = compiler.emptyPreviousResult
      val incOptions = IncOptions
        .of()
        .withApiDebug(true)

      val localBoot = Paths.get(sys.props("user.home")).resolve(".sbt").resolve("boot")
      val javaHome = Paths.get(sys.props("java.home"))
      val rootPaths = Array(tempDir.toPath, localBoot, javaHome)
      val converter = new MappedFileConverter(rootPaths, true)
      val mapper = VirtualFileUtil.sourcePositionMapper(converter)
      val stamper = Stamps.timeWrapLibraryStamps(converter)
      val cp: Vector[VirtualFile] =
        (si.allJars.toVector.map(_.toPath) ++ Vector(targetDir, binarySampleFile))
          .map(converter.toVirtualFile)
      val lookup = new PerClasspathEntryLookupImpl(
        {
          case x if converter.toPath(x).toAbsolutePath == targetDir.toAbsolutePath =>
            prev0.analysis.toOption
          case _ => None
        },
        Locate.definesClass
      )
      val sources = Array(dependerFile)
      val reporter = new ManagedLoggedReporter(maxErrors, log, mapper)
      val setup = compiler.setup(
        lookup,
        skip = false,
        cacheFile,
        CompilerCache.fresh,
        incOptions,
        reporter,
        None,
        Array()
      )
      val in = compiler.inputs(
        cp.toArray,
        sources,
        classesDirectory = targetDir,
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
      import sbt.io.syntax._
      println((targetDir.toFile ** "*").get.toList.toString)
      val expectedOut = targetDir.resolve("test").resolve("pkg").resolve("Depender$.class")
      assert(Files.exists(expectedOut), s"$expectedOut does not exist")

      val prev1 = fileStore.get.toOption match {
        case Some(contents) =>
          PreviousResult.of(Optional.of(contents.getAnalysis), Optional.of(contents.getMiniSetup))
        case _ => sys.error("previous is not found")
      }
      val sources1 = Array(dependerFile, depender2File)
      val in1 = compiler.inputs(
        cp.toArray,
        sources1,
        targetDir,
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
      val expectedOut1 = targetDir.resolve("test").resolve("pkg").resolve("Depender2$.class")
      assert(Files.exists(expectedOut), s"$expectedOut1 does not exist")
    }
  }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: Path): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(instance, bridgeJar)
    val classpath = ClasspathOptionsUtil.boot
    val cache = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(instance, bridgeProvider, classpath, _ => (), cache)
  }
}

object VirtualFileIncrementalSpec {
  case class StringVirtualFile(path: String, content: String)
      extends BasicVirtualFileRef(path)
      with VirtualFile {
    override def contentHash(): Long = HashUtil.farmHash(content.getBytes("UTF-8"))
    override def id(): String = path
    override def name(): String = path.split("/").last
    override def names(): Array[String] = path.split("/")
    override def input(): InputStream = new ByteArrayInputStream(content.getBytes("UTF-8"))
    override def toString(): String = s"StringVirtualFile($path, <content>)"
  }
}
