import sbt.{ AutoPlugin, Compile, Def, Keys, Project, Resolver, Test, TestFrameworks, Tests, URL }
import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtGit.{ git => GitKeys }
import bintray.BintrayPlugin.{ autoImport => BintrayKeys }
import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin
import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin.{ autoImport => ReleaseEarlyKeys }
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.{ autoImport => ScalafmtKeys }
import com.lucidchart.sbt.scalafmt.ScalafmtSbtPlugin.autoImport.Sbt
import com.typesafe.tools.mima.plugin.MimaKeys
import com.typesafe.tools.mima.plugin.MimaPlugin
import sbt.librarymanagement.ivy.{ InlineIvyConfiguration, IvyDependencyResolution }

object BuildPlugin extends AutoPlugin {
  override def requires =
    sbt.plugins.JvmPlugin && ScalafmtCorePlugin && GitPlugin && ReleaseEarlyPlugin && MimaPlugin
  override def trigger = allRequirements
  val autoImport = BuildAutoImported

  override def projectSettings: Seq[Def.Setting[_]] = BuildImplementation.projectSettings
  override def buildSettings: Seq[Def.Setting[_]] = BuildImplementation.buildSettings
}

trait BuildKeys {
  import sbt.{ TaskKey, taskKey }

  val tearDownBenchmarkResources: TaskKey[Unit] = taskKey[Unit]("Remove benchmark resources.")
  val scriptedPublish = taskKey[Unit]("Publishes all the Zinc artifacts for scripted")
  val cachedPublishLocal = taskKey[Unit]("Publishes a project if it hasn't been published before.")
}

object BuildAutoImported extends BuildKeys {
  import sbt.{ file, File, Developer, url, State }
  import BuildImplementation.{ BuildDefaults, BuildResolvers }

  val baseVersion: String = "1.1.0-SNAPSHOT"
  val internalPath: File = file("internal")
  val bridgeScalaVersions: List[String] =
    List(Dependencies.scala212, Dependencies.scala211, Dependencies.scala210)

  val ZincGitHomepage: URL = url("https://github.com/sbt/zinc")
  val ScalaCenterMaintainer: Developer =
    Developer("jvican", "Jorge Vicente Cantero", "@jvican", url("https://github.com/jvican"))

  // Defines several settings that are exposed to the projects definition in build.sbt
  private[this] val noPublishSettings: Seq[Def.Setting[_]] = BuildDefaults.noPublishSettings
  def noPublish(p: Project): Project = p.settings(noPublishSettings)

  // Sets up mima settings for modules that have to be binary compatible with Zinc 1.0.0
  val mimaSettings: Seq[Def.Setting[_]] =
    List(MimaKeys.mimaPreviousArtifacts := BuildDefaults.zincPreviousArtifacts.value)
  val adaptOptionsForOldScalaVersions: Seq[Def.Setting[_]] =
    List(Keys.scalacOptions := BuildDefaults.zincScalacOptionsRedefinition.value)

  val benchmarksTestDir = sbt.IO.createTemporaryDirectory
  def inCompileAndTest(ss: Def.Setting[_]*): Seq[Def.Setting[_]] =
    List(Compile, Test).flatMap(sbt.inConfig(_)(ss))
}

object BuildImplementation {
  import sbt.{ fileToRichFile, file, File, ThisBuild, Tags }

  val buildSettings: Seq[Def.Setting[_]] = List(
    Scripted.scriptedBufferLog := true,
    GitKeys.baseVersion := BuildAutoImported.baseVersion,
    GitKeys.gitUncommittedChanges := BuildDefaults.gitUncommitedChanges.value,
    BintrayKeys.bintrayPackage := "zinc",
    ScalafmtKeys.scalafmtOnCompile := true,
    ScalafmtKeys.scalafmtVersion := "1.2.0",
    ScalafmtKeys.scalafmtOnCompile in Sbt := false,
    ReleaseEarlyKeys.releaseEarlyWith := ReleaseEarlyKeys.BintrayPublisher,
    Keys.description := "Incremental compiler of Scala",
    // The rest of the sbt developers come from the Sbt Houserules plugin
    Keys.developers += BuildAutoImported.ScalaCenterMaintainer,
    Keys.homepage := Some(BuildAutoImported.ZincGitHomepage),
    Keys.publishArtifact in (Compile, Keys.packageDoc) :=
      BuildDefaults.publishDocAndSourceArtifact.value,
    Keys.publishArtifact in (Compile, Keys.packageSrc) :=
      BuildDefaults.publishDocAndSourceArtifact.value,
    Keys.version := {
      val previous = Keys.version.value
      if (previous.contains("-SNAPSHOT")) GitKeys.baseVersion.value else previous
    },
  )

  val projectSettings: Seq[Def.Setting[_]] = List(
    // publishArtifact in packageDoc := false,
    // concurrentRestrictions in Global += Util.testExclusiveRestriction,
    Keys.scalaVersion := Dependencies.scala212,
    Keys.resolvers ++= BuildResolvers.all,
    Keys.resolvers ~= BuildResolvers.removeRepeatedResolvers,
    Keys.testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
    Keys.javacOptions in Compile ++= Seq("-Xlint", "-Xlint:-serial"),
    Keys.crossScalaVersions := Seq(Dependencies.scala211, Dependencies.scala212),
    Keys.publishArtifact in Test := false,
    Keys.scalacOptions += "-YdisableFlatCpCaching",
    BuildAutoImported.cachedPublishLocal := BuildDefaults.cachedPublishLocal.value,
  )

  object BuildResolvers {
    import sbt.MavenRepository
    val TypesafeReleases: Resolver = Resolver.typesafeIvyRepo("releases")
    val SonatypeSnapshots: Resolver = Resolver.sonatypeRepo("snapshots")
    val BintrayMavenReleases: Resolver =
      MavenRepository("bintray-sbt-maven-releases", "https://dl.bintray.com/sbt/maven-releases/")
    val BintraySbtIvySnapshots: Resolver =
      Resolver.url("bintray-sbt-ivy-snapshots",
                   new URL("https://dl.bintray.com/sbt/ivy-snapshots/"))(Resolver.ivyStylePatterns)

    // Defines a resolver that is used to publish only for local testing via scripted
    val ScriptedResolverId = "zinc-scripted-local"
    val ScriptedResolveCacheDir: File = file(sys.props("user.dir") + s"/.ivy2/$ScriptedResolverId")
    val ScriptedResolver: Resolver =
      Resolver.file(ScriptedResolverId, ScriptedResolveCacheDir)(Resolver.ivyStylePatterns)

    // All contains all the resolvers but `alternativeLocal` which is used project per project
    val all: List[Resolver] =
      List(ScriptedResolver,
           TypesafeReleases,
           SonatypeSnapshots,
           BintrayMavenReleases,
           BintraySbtIvySnapshots)

    // Naive way of implementing a filter to remove repeated resolvers.
    def removeRepeatedResolvers(rs: Seq[Resolver]): Seq[Resolver] = rs.distinct
  }

  object BuildCommands {
    import sbt.{ Command, State }
    import BuildAutoImported.bridgeScalaVersions
    def crossTestBridges(bridge: Project): Command = {
      Command.command("crossTestBridges") { (state: State) =>
        (bridgeScalaVersions.flatMap { (bridgeVersion: String) =>
          // Note the ! here. You need this so compilerInterface gets forced to the scalaVersion
          s"++ $bridgeVersion!" :: s"${bridge.id}/test" :: Nil
        }) ::: (s"++ ${Dependencies.scala212}!" :: state)
      }
    }

    def publishBridgesAndSet(bridge: Project, interface: Project, apiInfo: Project): Command = {
      Command.args("publishBridgesAndSet", "<version>") { (state, args) =>
        require(args.nonEmpty, "Missing Scala version argument.")
        val userScalaVersion = args.mkString("")
        s"${interface.id}/publishLocal" :: bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v!" :: s"${apiInfo.id}/publishLocal" :: s"${bridge.id}/publishLocal" :: Nil
        } ::: s"++ $userScalaVersion!" :: state
      }
    }

    def publishBridgesAndTest(bridge: Project, interface: Project, apiInfo: Project): Command = {
      Command.args("publishBridgesAndTest", "<version>") { (state, args) =>
        require(args.nonEmpty, "Missing arguments to publishBridgesAndTest.")
        val version = args mkString ""
        val bridgeCommands: List[String] = bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v" :: s"${apiInfo.id}/publishLocal" :: s"${bridge.id}/publishLocal" :: Nil
        }
        s"${interface.id}/publishLocal" ::
          bridgeCommands :::
          s"++ $version" ::
          s"zincRoot/scalaVersion" ::
          s"zincRoot/test" ::
          s"zincRoot/scripted" ::
          state
      }
    }

    def runBenchmarks(benchmarkProject: Project): Command = {
      val dirPath = BuildAutoImported.benchmarksTestDir.getAbsolutePath
      val projectId = benchmarkProject.id
      val runPreSetup = s"$projectId/run $dirPath"
      val runBenchmark = s"$projectId/jmh:run -p _tempDir=$dirPath -prof gc"
      val tearDownResources = s"$projectId/tearDownBenchmarkResources"
      Command.command("runBenchmarks")(st => runPreSetup :: runBenchmark :: tearDownResources :: st)
    }

    def all(bridge: Project, interface: Project, apiInfo: Project, bench: Project): Seq[Command] = {
      val crossTest = crossTestBridges(bridge)
      val publishBridges = publishBridgesAndSet(bridge, interface, apiInfo)
      val publishBridgesTest = publishBridgesAndTest(bridge, interface, apiInfo)
      val runBench = runBenchmarks(bench)
      List(crossTest, publishBridges, publishBridgesTest, runBench)
    }
  }

  object BuildDefaults {
    import sbt.{ Task, State, IO }
    private[this] val statusCommands = List(
      List("diff-index", "--cached", "HEAD"),
      List("diff-index", "HEAD"),
      List("diff-files"),
      List("ls-files", "--exclude-standard", "--others")
    )

    // https://github.com/sbt/sbt-git/issues/109
    val gitUncommitedChanges: Def.Initialize[Boolean] = Def.setting {
      // Workaround from https://github.com/sbt/sbt-git/issues/92#issuecomment-161853239
      val dir = Keys.baseDirectory.value
      // can't use git.runner.value because it's a task
      val runner = com.typesafe.sbt.git.ConsoleGitRunner
      // sbt/zinc#334 Seemingly "git status" resets some stale metadata.
      runner("status")(dir, com.typesafe.sbt.git.NullLogger)
      val uncommittedChanges = statusCommands.flatMap { c =>
        val res = runner(c: _*)(dir, com.typesafe.sbt.git.NullLogger)
        if (res.isEmpty) Nil else List(c -> res)
      }
      val logger = Keys.sLog.value
      val areUncommited = uncommittedChanges.nonEmpty
      if (areUncommited) {
        uncommittedChanges.foreach {
          case (cmd, res) =>
            logger.debug(s"""Uncommitted changes found via "${cmd.mkString(" ")}":\n${res}""")
        }
      }
      areUncommited
    }

    import sbt.{ CrossVersion, ModuleID, stringToOrganization }
    val zincPreviousArtifacts: Def.Initialize[Set[ModuleID]] = Def.setting {
      val zincModule = (Keys.organization.value % Keys.moduleName.value % "1.0.0")
        .cross(if (Keys.crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
      Set(zincModule)
    }

    private[this] val toFilterInOldScala: Set[String] = Set(
      "-Xfatal-warnings",
      "-deprecation",
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-YdisableFlatCpCaching"
    )

    val zincScalacOptionsRedefinition: Def.Initialize[Task[Seq[String]]] = Def.task {
      val old = Keys.scalacOptions.value
      Keys.scalaBinaryVersion.value match {
        case v if v == "2.12" || v == "2.13" => old
        case _                               => old.filterNot(toFilterInOldScala)
      }
    }


    /**
      * This setting figures out whether the version is a snapshot or not and configures
      * the source and doc artifacts that are published by the build.
      *
      * Snapshot is a term with no clear definition. In this code, a snapshot is a revision
      * that has either build or time metadata in its representation. In those cases, the
      * build will not publish doc and source artifacts by any of the publishing actions.
      */
    val publishDocAndSourceArtifact: Def.Initialize[Boolean] = Def.setting {
      import sbtdynver.{ GitDescribeOutput, DynVerPlugin }
      import DynVerPlugin.{ autoImport => DynVerKeys }
      def isDynVerSnapshot(gitInfo: Option[GitDescribeOutput], defaultValue: Boolean): Boolean = {
        val isStable = gitInfo.map { info =>
          info.ref.value.startsWith("v") &&
          (info.commitSuffix.distance <= 0 || info.commitSuffix.sha.isEmpty)
        }
        val isNewSnapshot =
          isStable.map(stable => !stable || defaultValue)
        // Return previous snapshot definition in case users has overridden version
        isNewSnapshot.getOrElse(defaultValue)
      }

      // We publish doc and source artifacts if the version is not a snapshot
      !isDynVerSnapshot(DynVerKeys.dynverGitDescribeOutput.value, Keys.isSnapshot.value)
    }

    private val P = "[" + wrapIn(Console.BOLD + Console.CYAN, "scripted") + "]"
    val cachedPublishLocal: Def.Initialize[Task[Unit]] = Def.taskDyn {
      import BuildResolvers.{ ScriptedResolveCacheDir, ScriptedResolver }
      if ((Keys.skip in Keys.publish).value) Def.task(())
      else
        Def.taskDyn {
          import sbt.util.Logger.{ Null => NoLogger }
          val logger = Keys.streams.value.log

          // Find out the configuration of this task to invoke source dirs in the right place
          val taskConfig = Keys.resolvedScoped.value.scope.config
          val currentConfig: sbt.ConfigKey = taskConfig.fold(identity, Compile, Compile)

          // Important to make it transitive, we just want to check if a jar exists
          val moduleID = Keys.projectID.value.intransitive()
          val scalaModule = Keys.scalaModuleInfo.value
          val ivyConfig = Keys.ivyConfiguration.value
          val options = ivyConfig.updateOptions

          // If it's another thing, just fail! We must have an inline ivy config here.
          val inlineConfig = ivyConfig.asInstanceOf[InlineIvyConfiguration]
          val fasterIvyConfig: InlineIvyConfiguration = inlineConfig
            .withResolvers(Vector(ScriptedResolver))
            .withChecksums(Vector())
            // We can do this because we resolve intransitively and nobody but this task publishes
            .withLock(None)

          val resolution = IvyDependencyResolution(fasterIvyConfig)
          val result = resolution.retrieve(moduleID, scalaModule, ScriptedResolveCacheDir, NoLogger)
          result match {
            case l: Left[_, _] => publishLocalWrapper(moduleID, fasterIvyConfig, false)
            case Right(resolved) =>
              Def.taskDyn {
                val projectName = Keys.name.value
                val baseDirectory = Keys.baseDirectory.value.toPath()
                val sourceDirs = Keys.sourceDirectories.in(currentConfig).value
                val resourceDirs = Keys.resourceDirectories.in(currentConfig).value
                val allDirs = sourceDirs ++ resourceDirs
                val files = allDirs.flatMap(sbt.Path.allSubpaths(_)).toIterator.map(_._1)

                val allJars = resolved.filter(_.getPath().endsWith(".jar"))
                val lastPublicationTime = allJars.map(_.lastModified()).max
                val invalidatedSources = files.filter(_.lastModified() >= lastPublicationTime)
                if (invalidatedSources.isEmpty) {
                  Def.task(logger.info(s"$P Skip publish for `$projectName`."))
                } else {
                  Def.task {
                    val onlySources = invalidatedSources
                      .filter(_.isFile)
                      .map(f => baseDirectory.relativize(f.toPath).toString)
                    val allChanges = onlySources.mkString("\n\t-> ", "\n\t-> ", "\n")
                    logger.warn(s"$P Changes detected in $projectName: $allChanges")
                    publishLocalWrapper(moduleID, fasterIvyConfig, true).value
                  }
                }
              }
          }
        }
    }

    def publishLocalWrapper(moduleID: ModuleID,
                            ivyConfiguration: InlineIvyConfiguration,
                            overwrite: Boolean): Def.Initialize[Task[Unit]] = {
      import BuildResolvers.ScriptedResolverId
      import sbt.internal.librarymanagement._
      import sbt.librarymanagement.{ ModuleSettings, PublishConfiguration }
      Def.task {
        val logger = Keys.streams.value.log

        def publishLocal(moduleSettings: ModuleSettings, config: PublishConfiguration): Unit = {
          val ivy = new IvySbt(ivyConfiguration)
          val module = new ivy.Module(moduleSettings)
          val correctConfig = config.withOverwrite(overwrite)
          val fastConfig: PublishConfiguration =
            correctConfig.withResolverName(ScriptedResolverId).withChecksums(Vector())
          IvyActions.publish(module, fastConfig, logger)
        }

        val name = Keys.name.value
        val version = moduleID.revision
        logger.warn(s"$P Publishing `$name`, version: '$version'.")

        val moduleSettings = Keys.moduleSettings.value
        val publishConfig = Keys.publishLocalConfiguration.value
        publishLocal(moduleSettings, publishConfig)
      }
    }

    val noPublishSettings: Seq[Def.Setting[_]] = List(
      Keys.publish := {},
      Keys.publishLocal := {},
      Keys.publishArtifact in Compile := false,
      Keys.publishArtifact in Test := false,
      Keys.publishArtifact := false,
      Keys.skip in Keys.publish := true,
    )

    private[this] def wrapIn(color: String, content: String): String = {
      import sbt.internal.util.ConsoleAppender
      if (!ConsoleAppender.formatEnabledInEnv) content
      else color + content + scala.Console.RESET
    }

    private[this] val scalaPartialVersion =
      Def.setting(CrossVersion.partialVersion(Keys.scalaVersion.value))
    val handleScalaSpecificSources: Def.Initialize[List[File]] = Def.setting {
      val source = Keys.scalaSource.value
      scalaPartialVersion.value.collect {
        case (2, y) if y == 10 => new File(source.getPath + "_2.10")
        case (2, y) if y >= 11 => new File(source.getPath + "_2.11+")
      }.toList
    }

    import sbt.{ InputTask }
    import Scripted.{ scriptedSource, scriptedParser, scriptedBufferLog, scriptedPrescripted }
    def zincScripted(bridgeRef: Project,
                     interfaceRef: Project,
                     scriptedRef: Project): Def.Initialize[InputTask[Unit]] = Def.inputTask {
      // First, publish all the zinc modules (not cross-compiled though)
      BuildAutoImported.scriptedPublish.value

      val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
      val source = scriptedSource.value
      val logged = scriptedBufferLog.value
      val hook = scriptedPrescripted.value

      val scriptedClasspath = (Keys.fullClasspath in scriptedRef in Test).value
      val instance = (Keys.scalaInstance in scriptedRef).value
      Scripted.doScripted(scriptedClasspath, instance, source, result, logged, hook)
    }

    val tearDownBenchmarkResources: Def.Initialize[Task[Unit]] =
      Def.task(IO.delete(BuildAutoImported.benchmarksTestDir))
  }
}
