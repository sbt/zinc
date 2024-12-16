/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbt

import java.io.File
import java.nio.file.{ Files, Path, Paths }
import org.eclipse.jgit.api.{ CloneCommand, Git }
import sbt.io.{ IO, RichFile }
import sbt.inc.{ ProjectSetup, VirtualSubproject }
import xsbt.ZincBenchmark.CompilationInfo

import scala.util.Try

/** Consist of the setups for every subproject of a `ProjectBenchmark`. */
case class ZincSetup(result: ZincBenchmark.Result[List[ProjectSetup]]) {
  private def crash(throwable: Throwable) = {
    val message =
      s"""Unexpected error when setting up Zinc benchmarks:
        |$throwable
      """.stripMargin
    sys.error(message)
  }

  /** Crash at this point because JMH wants the list of setup runs. */
  def getOrCrash: List[ProjectSetup] =
    result.fold(crash, identity)
}

/* Classes are defined `private[xsbt]` to avoid scoping issues w/ `CachedCompiler0`. */

/** Instantiate a `ZincBenchmark` from a given project. */
private[xsbt] class ZincBenchmark(toCompile: BenchmarkProject, zincEnabled: Boolean = true) {
  import ZincBenchmark.WriteBuildInfo

  def writeSetup(globalDir: File): WriteBuildInfo = {
    // Destructive action, remove previous state and cloned projects
    if (globalDir.exists()) IO.delete(globalDir)
    toCompile.cloneRepo(globalDir).flatMap { projectDir =>
      toCompile.writeBuildInfo(projectDir, globalDir)
    }
  }

  private val UseJavaCpArg = Array("-usejavacp")
  private val nowarn = Seq("-nowarn")
  def readSetup(compilationDir: File): ZincSetup = {
    def createSetup(subproject: String, compilationInfo: CompilationInfo) = {

      val buildInfo = {
        if (!toCompile.useJavaCp) compilationInfo
        else {
          val currentOpts = compilationInfo.scalacOptions
          compilationInfo.copy(scalacOptions = currentOpts ++ UseJavaCpArg)
        }
      }
      val output = (new RichFile(compilationDir) / "benchmark-target").toPath
      val base = compilationDir.toPath
      // the expected sources are relative
      val sources = buildInfo.sources
        .map(x => base.relativize(x))
      val cp = buildInfo.classpath
      cp.foreach(x => assert(Files.exists(x), s"$x does not exist"))
      ProjectSetup(
        VirtualSubproject(compilationDir.toPath),
        Map(output -> sources),
        cp,
        Map.empty,
        outputToJar = false,
        subproject,
        // ignore `buildInfo.scalacOptions` that was recovered from the build
        // [info] [error] ## Exception when compiling 564 sources to /private/var/folders/hg/2602nfrs2958vnshglyl3srw0000gn/T/sbt_ed541eaf/scala/scala/classes
        // [info] [error] scala.reflect.internal.Symbols$CyclicReference: illegal cyclic reference involving object Predef
        scalacOptions = nowarn,
      )
    }

    val targetProjects = toCompile.subprojects.map(
      CompilationInfo.createIdentifierFor(_, toCompile)
    )

    import CompilationInfo.{ readBuildInfos, createStateFile }
    val stateFile = createStateFile(compilationDir)
    val targetSetup = readBuildInfos(stateFile).flatMap { builds =>
      val collected = builds.collect {
        case r @ Right(read) if r.exists(t => targetProjects.contains(t._1)) =>
          val (subproject, compilationInfo) = read
          createSetup(subproject, compilationInfo)
      }

      if (collected.nonEmpty) Right(collected)
      else
        Left(new Exception(s"No build in $targetProjects found in $builds."))
    }

    ZincSetup(targetSetup)
  }
}

private[xsbt] object ZincBenchmark {
  // This is the Scala version used to compile the benchmark project
  // do not use `scala.util.Properties.versionNumberString`.
  val scalaVersion = "2.13.15"

  /* ************************************************************* */
  /* Utils to programmatically instantiate Compiler from sbt setup  */
  /* ************************************************************* */

  /**
   * Represent the build results for reading and writing build infos.
   *
   * In the future, `Throwable` can be lifted to another error repr.
   */
  type Result[T] = Either[Throwable, T]
  type ReadBuildInfo = Result[(String, CompilationInfo)]
  type WriteBuildInfo = Result[Unit]

  object Git {

    /** Clone a git repository using JGit. */
    def clone(repo: String, at: File): Result[Git] = {
      val cloneCommand =
        new CloneCommand().setURI(s"https://github.com/$repo").setDirectory(at)
      Try(cloneCommand.call()).toEither
    }

    /** Checkout a hash in a concrete repository and throw away Ref. */
    def checkout(git: Git, hash: String): Result[Git] =
      Try(git.checkout().setName(hash).call()).toEither.map(_ => git)
  }

  /** Sbt classpath, scalac options and sources for a given subproject. */
  case class CompilationInfo(
      classpath: List[Path],
      sources: List[Path],
      scalacOptions: List[String]
  )

  /** Helper to get the build info of a given sbt subproject. */
  object CompilationInfo {

    /** Generate class from output generated by `generateImpl`. */
    def apply(
        classpath: String,
        sources: String,
        options: String
    ): CompilationInfo = {
      val classpathL = classpath
        .split(File.pathSeparator)
        .toList
        .map(Paths.get(_))
      val sourcesL = sources
        .split(" ")
        .toList
        .map(Paths.get(_))
      val optionsL = options.split(" ").toList
      CompilationInfo(classpathL, sourcesL, optionsL)
    }

    private val TaskNamePrefix = "getAllSourcesAndClasspath"
    private val ExpectedFileType = "out"

    private def generateTaskName(sbtProject: String) =
      s"$TaskNamePrefix$sbtProject"

    def generateOutputFile(sbtProject: String) =
      s"${generateTaskName(sbtProject)}.$ExpectedFileType"

    /** Generate an implementation for the task targeted at `sbtProject`. */
    def generateImpl(sbtProject: String, outputFile: File): String = {
      val taskName = generateTaskName(sbtProject)
      s"""
         |// This task is instrumented by the benchmarks in the Zinc compiler
         |lazy val `$taskName` =
         |  taskKey[Unit]("Get source files and classpath of subprojects")
         |`$taskName` in ThisBuild := `$taskName-impl`.value
         |lazy val `$taskName-impl` = Def.taskDyn {
         |  // Resolve project dynamically to avoid name clashes/overloading
         |  val project = LocalProject("$sbtProject")
         |  Def.task {
         |    val file = new File("${outputFile.getAbsolutePath.replace("\\", "/")}")
         |    val rawSources = (sources in Compile in project).value
         |    val sourcesLine = rawSources.map(_.getCanonicalPath).mkString(" ")
         |    val rawClasspath = (dependencyClasspath in Compile in project).value
         |    val classpathLine = rawClasspath.map(_.data.getCanonicalPath).mkString(java.io.File.pathSeparator)
         |    val optionsLine = (scalacOptions in Compile in project).value.mkString(" ")
         |    IO.writeLines(file, Seq(sourcesLine, classpathLine, optionsLine))
         |  }
         |}
      """.stripMargin
    }

    /**
     * Create identifier for subproject.
     *
     * Use of '#' as a delimiter because it is prohibited in GitHub repos.
     */
    def createIdentifierFor(subproject: String, project: BenchmarkProject) =
      s"${project.repo}#$subproject"

    /** Read all the compilation infos for all the benchmarks to be run. */
    def readBuildInfos(stateFile: File): Result[List[ReadBuildInfo]] = {
      def readCompilationFile(outputFile: File) = {
        val contents = IO.read(outputFile)
        val lines = contents.split("\n")
        lines match {
          case Array(sourcesL, classpathL, optsL) =>
            Right(CompilationInfo(classpathL.trim, sourcesL.trim, optsL.trim))
          case _ =>
            Left(new Exception(s"Error when reading sbt output: $lines."))
        }
      }

      def parseStateLine(line: String): ReadBuildInfo = {
        line.split(UniqueDelimiter) match {
          case Array(sbtProject, buildOutputFilepath) =>
            val buildOutputFile = new File(buildOutputFilepath)
            if (buildOutputFile.exists())
              readCompilationFile(buildOutputFile).map(sbtProject -> _)
            else Left(new Exception(s"$buildOutputFile doesn't exist."))
          case _ =>
            Left(new Exception(s"Unexpected format of line: $line."))
        }
      }

      val readState = Try(IO.read(stateFile).linesIterator.toList).toEither
      readState.flatMap { stateLines =>
        val init: Result[List[ReadBuildInfo]] = Right(Nil)
        stateLines.foldLeft(init) { (acc, line) =>
          acc.map(rs => parseStateLine(line) :: rs)
        }
      }
    }

    private val BenchmarkStateFilename = "benchmarks-info.out"

    /**
     * Create the file where the benchmark state is saved.
     *
     * State file holds the pair of projects to filepaths where the build
     * information is found. This information has to be written into a
     * file so that the reader and writer (that run in independent JVMs)
     * can communicate between each other. The writer JVM is the one that
     * sets up the benchmarks, the reader is the JMH-based benchmarks.
     */
    def createStateFile(atDir: File): File = {
      new File(s"${atDir.getAbsolutePath}/$BenchmarkStateFilename")
    }

    private val UniqueDelimiter = "@@@"

    /** Run sbt task command for a given project. */
    def executeSbtTask(
        sbtProject: String,
        project: BenchmarkProject,
        atDir: File,
        buildOutputFile: File,
        stateFile: File
    ): Result[Unit] = {
      import scala.sys.process._
      val taskName = generateTaskName(sbtProject)
      val sbtExecutable = if (scala.util.Properties.isWin) "cmd /c sbt.bat" else "sbt"
      val sbt = Try(Process(s"$sbtExecutable ++$scalaVersion! $taskName", atDir).!).toEither
      sbt.flatMap { _ =>
        val buildOutputFilepath = buildOutputFile.getAbsolutePath
        Try {
          val subprojectId = createIdentifierFor(sbtProject, project)
          assert(!subprojectId.contains(UniqueDelimiter))
          assert(!buildOutputFilepath.contains(UniqueDelimiter))
          val projectLine =
            s"$subprojectId$UniqueDelimiter$buildOutputFilepath\n"
          IO.append(stateFile, projectLine)
        }.toEither
      }
    }
  }
}

/** Represent a project on which to run benchmarks. */
case class BenchmarkProject(
    repo: String,
    hash: String,
    subprojects: Seq[String],
    useJavaCp: Boolean = true
) {
  assert(hash.nonEmpty)
  assert(subprojects.nonEmpty)
  assert(repo.contains("/"), "Repo has to follow the 'owner/repo' format")

  import ZincBenchmark.{ Result, Git, CompilationInfo, WriteBuildInfo }

  private[xsbt] def cloneRepo(at: File): Result[File] = {
    val tempDir = new File(s"${at.getAbsolutePath}/$hash")
    val gitClient = Git.clone(repo, tempDir)
    gitClient.flatMap(Git.checkout(_, hash)).map(_ => tempDir)
  }

  // Left for compatibility
  def writeBuildInfo(projectDir: File, sharedDir: File): WriteBuildInfo = {
    def persistBuildInfo(subproject: String, stateFile: File): Result[Unit] = {
      val filename = CompilationInfo.generateOutputFile(subproject)
      val clonedProjectDir = new RichFile(projectDir)
      val subprojectOutput = clonedProjectDir / filename
      val taskImpl = CompilationInfo.generateImpl(subproject, subprojectOutput)
      val buildFile = clonedProjectDir / "build.sbt"
      val appendFile = Try(IO.append(buildFile, taskImpl)).toEither

      appendFile.flatMap { _ =>
        CompilationInfo.executeSbtTask(
          subproject,
          this,
          projectDir,
          subprojectOutput,
          stateFile
        )
      }
    }

    // Empty state file if exists, otherwise create it
    val stateFile = CompilationInfo.createStateFile(sharedDir)
    IO.write(stateFile, "")

    val init: WriteBuildInfo = Right(())
    subprojects.foldLeft(init) { (result, subproject) =>
      result.flatMap { _ =>
        persistBuildInfo(subproject, stateFile)
      }
    }
  }
}
