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

import java.nio.file.Paths
import sbt.internal.inc.{
  Analysis,
  CompileOutput,
  JarUtils,
  MixedAnalyzingCompiler,
  PlainVirtualFile
}
import sbt.io.IO

import scala.jdk.CollectionConverters._

class CachedHashingSpec extends BaseCompilerSpec {
  lazy val isWindows: Boolean =
    sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")

  def timeMs[R](block: => R): Long = {
    val t0 = System.nanoTime()
    block // call-by-name
    val t1 = System.nanoTime()
    (t1 - t0) / 1000000
  }

  "zinc" should "cache jar generation" in {
    IO.withTemporaryDirectory { tempDir =>
      val classes = Seq(SourceFiles.Good)
      val sources0 = Map(Paths.get("src") -> classes.map(path => Paths.get(path)))
      val projectSetup = ProjectSetup(VirtualSubproject(tempDir.toPath()), sources0, Nil)
      val compiler = projectSetup.createCompiler()
      try {
        import compiler.in.{ setup, options, compilers, previousResult }
        import sbt.internal.inc.JavaInterfaceUtil._

        val javac = compilers.javaTools.javac
        val scalac = compilers.scalac

        import java.nio.file._
        val giganticClasspath = Files
          .walk(Paths.get(sys.props("user.home"), ".ivy2"))
          .iterator()
          .asScala
          .filter(_.getFileName.toString.endsWith(".jar"))
          .take(500)
          .map(x => PlainVirtualFile(x))
          .toVector
        val output = CompileOutput(options.classesDirectory)

        def genConfig = MixedAnalyzingCompiler.makeConfig(
          scalac,
          javac,
          options.sources,
          options.converter.toOption.get,
          giganticClasspath,
          setup.cache,
          setup.progress.toOption,
          options.scalacOptions,
          options.javacOptions,
          Analysis.empty,
          previousResult.setup.toOption,
          setup.perClasspathEntryLookup,
          setup.reporter,
          options.order,
          setup.skip,
          setup.incrementalCompilerOptions,
          output,
          JarUtils.createOutputJarContent(output),
          options.earlyOutput.toOption,
          setup.earlyAnalysisStore.toOption,
          options.stamper.toOption.get,
          setup.extra.toList.map(_.toScalaTuple)
        )

        val hashingTime = timeMs(genConfig)
        // we've had problems with this failing intermittently in CI (issue #1064), so let's
        // run it three times and accept any passing result; hopefully that will help?
        val cachedHashingTime =
          Iterator.continually(timeMs(genConfig))
            .take(3).min
        if (isWindows) assert(true)
        else
          assert(
            cachedHashingTime < (hashingTime * 0.50),
            s"Cache jar didn't work: $cachedHashingTime is >= than 50% of $hashingTime."
          )
      } finally {
        compiler.close()
      }
    }
  }

  // it should "fall back when the JAR metadata is changed" in {
  //   IO.withTemporaryDirectory { tempDir =>
  //     import java.nio.file.{ Files, Path, Paths, StandardCopyOption }
  //     val classes = Seq(SourceFiles.Good)
  //     val sources0 = Map(Paths.get("src") -> classes.map(path => Paths.get(path)))
  //     val projectSetup = ProjectSetup(tempDir.toPath(), sources0, Nil)
  //     val compiler = projectSetup.createCompiler()

  //     import compiler.in.{ setup, options, compilers, previousResult }
  //     import sbt.internal.inc.JavaInterfaceUtil._

  //     val javac = compilers.javaTools.javac
  //     val scalac = compilers.scalac
  //     val fakeLibraryJar = tempDir.toPath / "lib" / "foo.jar"
  //     val output = CompileOutput(options.classesDirectory)
  //     def genConfig = MixedAnalyzingCompiler.makeConfig(
  //       scalac,
  //       javac,
  //       options.sources,
  //       options.converter.toOption.get,
  //       List(fakeLibraryJar),
  //       output,
  //       setup.cache,
  //       setup.progress.toOption,
  //       options.scalacOptions,
  //       options.javacOptions,
  //       Analysis.empty,
  //       previousResult.setup.toOption,
  //       setup.perClasspathEntryLookup,
  //       setup.reporter,
  //       options.order,
  //       setup.skip,
  //       setup.incrementalCompilerOptions,
  //       JarUtils.createOutputJarContent(output),
  //       setup.extra.toList.map(_.toScalaTuple)
  //     )

  //     Files.copy(
  //       fromResource(Paths.get("jar1.jar")),
  //       fakeLibraryJar,
  //       StandardCopyOption.REPLACE_EXISTING
  //     )
  //     genConfig

  //     // This mimics changing dependency like -SNAPSHOT
  //     Files.copy(
  //       fromResource(Paths.get("classesDep1.zip")),
  //       fakeLibraryJar,
  //       StandardCopyOption.REPLACE_EXISTING
  //     )
  //     genConfig
  //   }
  // }

  // private def fromResource(path: Path): Path = {
  //   val prefix = Paths.get("bin")
  //   val fullPath = prefix.resolve(path).toString()
  //   Option(getClass.getClassLoader.getResource(fullPath))
  //     .map(url => Paths.get(url.toURI))
  //     .getOrElse(throw new NoSuchElementException(s"Missing resource $fullPath"))
  // }
}
