package sbt.inc.cached

import java.nio.file.Paths

import sbt.inc.{ BaseCompilerSpec, SourceFiles }
import sbt.internal.inc.{ Analysis, CompileOutput, MixedAnalyzingCompiler }
import sbt.io.IO

class CachedHashingSpec extends BaseCompilerSpec {
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
      val projectSetup = ProjectSetup(tempDir.toPath(), sources0, Nil)
      val compiler = projectSetup.createCompiler()

      import compiler.in.{ setup, options, compilers, previousResult }
      import sbt.internal.inc.JavaInterfaceUtil._
      import sbt.io.syntax.{ file, fileToRichFile, singleFileFinder }

      val javac = compilers.javaTools.javac
      val scalac = compilers.scalac
      val giganticClasspath = file(sys.props("user.home"))./(".ivy2").**("*.jar").get.take(500)

      def genConfig = MixedAnalyzingCompiler.makeConfig(
        scalac,
        javac,
        options.sources,
        giganticClasspath,
        CompileOutput(options.classesDirectory),
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
        setup.extra.toList.map(_.toScalaTuple)
      )

      val hashingTime = timeMs(genConfig)
      val cachedHashingTime = timeMs(genConfig)
      assert(cachedHashingTime < (hashingTime * 0.20),
             s"Cache jar didn't work: $cachedHashingTime is >= than 20% of $hashingTime.")
    }
  }
}
