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
  CompileOutput,
  Analysis,
  MixedAnalyzingCompiler,
  JarUtils,
  PlainVirtualFile
}
import sbt.io.IO

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
      val projectSetup = TestProjectSetup(tempDir.toPath(), sources0, Nil)
      val compiler = projectSetup.createCompiler()

      import compiler.in.{ setup, options, compilers, previousResult }
      import sbt.internal.inc.JavaInterfaceUtil._
      import sbt.io.syntax.{ file, fileToRichFile, singleFileFinder }

      val javac = compilers.javaTools.javac
      val scalac = compilers.scalac
      val giganticClasspath =
        file(sys.props("user.home"))
          ./(".ivy2")
          .**("*.jar")
          .get
          .take(500)
          .map(x => PlainVirtualFile(x.toPath))
      val output = CompileOutput(options.classesDirectory)
      def genConfig = MixedAnalyzingCompiler.makeConfig(
        scalac,
        javac,
        options.sources,
        options.converter.toOption.get,
        giganticClasspath,
        output,
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
        JarUtils.createOutputJarContent(output),
        options.stamper.toOption.get,
        setup.extra.toList.map(_.toScalaTuple)
      )

      val hashingTime = timeMs(genConfig)
      val cachedHashingTime = timeMs(genConfig)
      if (isWindows) assert(true)
      else
        assert(
          cachedHashingTime < (hashingTime * 0.50),
          s"Cache jar didn't work: $cachedHashingTime is >= than 50% of $hashingTime."
        )
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
