import sbt._, Keys._
import com.typesafe.sbt.GitPlugin, GitPlugin.autoImport._
import bintray.BintrayPlugin, BintrayPlugin.autoImport._
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin, ScalafmtCorePlugin.autoImport._
import com.lucidchart.sbt.scalafmt.ScalafmtSbtPlugin, ScalafmtSbtPlugin.autoImport._
import com.typesafe.tools.mima.plugin.MimaPlugin, MimaPlugin.autoImport._
import de.heikoseeberger.sbtheader.HeaderPlugin, HeaderPlugin.autoImport._
import Dependencies._
import Scripted._

object BuildPlugin extends AutoPlugin {
  override def requires =
    sbt.plugins.JvmPlugin && GitPlugin && BintrayPlugin &&
      ScalafmtCorePlugin && ScalafmtSbtPlugin && MimaPlugin && HeaderPlugin

  override def trigger = allRequirements

  val autoImport = BuildAutoImported

  override def buildSettings: Seq[Setting[_]] = BuildImplementation.buildSettings
  override def projectSettings: Seq[Setting[_]] = BuildImplementation.projectSettings
}

trait BuildKeys {
  val tearDownBenchmarkResources: TaskKey[Unit] = taskKey("Remove benchmark resources.")
  val scriptedPublishAll: TaskKey[Unit] = taskKey("Publishes all the Zinc artifacts for scripted")
  val cleanSbtBridge: TaskKey[Unit] = taskKey("Cleans the sbt bridge")
  val zincPublishLocal: TaskKey[Unit] =
    taskKey("Publishes Zinc artifacts to an alternative local cache")
}

object BuildAutoImported extends BuildKeys {
  import BuildImplementation.{ BuildDefaults, BuildResolvers }

  val baseVersion = "1.1.0-SNAPSHOT"
  val internalPath = file("internal")
  val bridgeScalaVersions: List[String] = List(scala212, scala213, scala211, scala210)
  // TODO: Add 2.13 (at the time some dependencies are not available for Scala 2.13.0-M2)
  val bridgeTestScalaVersions: List[String] = List(scala212, scala211, scala210)

  val ZincGitHomepage: URL = url("https://github.com/sbt/zinc")

  val ScalaCenterMaintainer: Developer =
    Developer("jvican", "Jorge Vicente Cantero", "@jvican", url("https://github.com/jvican"))

  // Defines several settings that are exposed to the projects definition in build.sbt
  val noPublish: Seq[Setting[_]] = BuildDefaults.noPublishSettings

  // Sets up mima settings for modules that have to be binary compatible with Zinc 1.0.0
  val mimaSettings: Seq[Setting[_]] =
    List(mimaPreviousArtifacts := BuildDefaults.zincPreviousArtifacts.value)

  val adaptOptionsForOldScalaVersions: Seq[Setting[_]] =
    List(scalacOptions := BuildDefaults.zincScalacOptionsRedefinition.value)

  val zincPublishLocalSettings: Seq[Setting[_]] = List(
    resolvers += BuildResolvers.AlternativeLocalResolver,
    zincPublishLocal := BuildDefaults.zincPublishLocalImpl.value,
  )

  val benchmarksTestDir: File = IO.createTemporaryDirectory

  def inCompileAndTest(ss: SettingsDefinition*): Seq[Setting[_]] =
    Seq(Compile, Test) flatMap (inConfig(_)(Def.settings(ss: _*)))
}

import BuildPlugin.autoImport._

object BuildImplementation {
  val buildSettings: Seq[Setting[_]] = List(
    git.baseVersion := baseVersion,
    git.gitUncommittedChanges := BuildDefaults.gitUncommittedChanges.value,
    version := {
      val previous = version.value
      if (previous.contains("-SNAPSHOT")) git.baseVersion.value else previous
    },
    bintrayPackage := "zinc",
    // TODO(jvican): Remove `scmInfo` and `homepage` when we have support for sbt-release-early
    scmInfo := Some(ScmInfo(ZincGitHomepage, "git@github.com:sbt/zinc.git")),
    description := "Incremental compiler of Scala",
    homepage := Some(ZincGitHomepage),
    // The rest of the zinc developers come from sbt-houserules
    developers += ScalaCenterMaintainer,
    scalafmtOnCompile := true,
    scalafmtVersion := "1.2.0",
    scalafmtOnCompile in Sbt := false,
  )

  def projectSettings: Seq[Setting[_]] = Def settings (
    scalaVersion := scala212,
    // publishArtifact in packageDoc := false,
    resolvers ++= BuildResolvers.all,
    resolvers ~= BuildResolvers.removeRepeatedResolvers,
    // concurrentRestrictions in Global += Util.testExclusiveRestriction,
    testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
    javacOptions in Compile ++= Seq("-Xlint", "-Xlint:-serial"),
    crossScalaVersions := Seq(scala211, scala212),
    publishArtifact in Test := false,
    scalacOptions += "-YdisableFlatCpCaching",
    inCompileAndTest(Seq(headerCreate, headerCheck) flatMap (inTask(_)(
      unmanagedResources ~= (_ filterNot { f =>
        // exclude test resource source files
        (f.getName endsWith ".java") || (f.getName endsWith ".scala")
      })
    ))),
  )

  // Defines the constants for the alternative publishing
  val ZincAlternativeRepoName: String = "alternative-local"
  val ZincAlternativeRepoPath: String = sys.props("user.home") + "/.ivy2/zinc-alternative"
  val ZincAlternativeRepoDir: File = file(ZincAlternativeRepoPath)

  object BuildResolvers {
    val TypesafeReleases: Resolver = Resolver.typesafeIvyRepo("releases")
    val SonatypeSnapshots: Resolver = Resolver.sonatypeRepo("snapshots")
    val BintrayMavenReleases: Resolver =
      MavenRepository("bintray-sbt-maven-releases", "https://dl.bintray.com/sbt/maven-releases/")
    val BintraySbtIvySnapshots: Resolver =
      Resolver.url("bintray-sbt-ivy-snapshots",
                   new URL("https://dl.bintray.com/sbt/ivy-snapshots/"))(Resolver.ivyStylePatterns)
    val all: List[Resolver] =
      List(TypesafeReleases, SonatypeSnapshots, BintrayMavenReleases, BintraySbtIvySnapshots)

    val AlternativeLocalResolver: Resolver =
      Resolver.file(ZincAlternativeRepoName, ZincAlternativeRepoDir)(Resolver.ivyStylePatterns)

    // Naive way of implementing a filter to remove repeated resolvers.
    def removeRepeatedResolvers(rs: Seq[Resolver]): Seq[Resolver] = rs.distinct
  }

  object BuildCommands {
    def crossTestBridges(bridgeTest: Project): Command = {
      Command.command("crossTestBridges") { (state: State) =>
        val bridgeCommands = bridgeTestScalaVersions.flatMap { (bridgeVersion: String) =>
          // Note the ! here. You need this so compilerInterface gets forced to the scalaVersion
          s"++ $bridgeVersion!" :: s"${bridgeTest.id}/test" :: Nil
        }
        bridgeCommands ::: s"++ $scala212!" :: state
      }
    }

    def publishBridgesAndSet(bridge: Project, interface: Project): Command = {
      Command.args("publishBridgesAndSet", "<version>") { (state, args) =>
        require(args.nonEmpty, "Missing Scala version argument.")
        val version = args.mkString("")
        val bridgeCommands = bridgeScalaVersions.flatMap { (v: String) =>
          // Note the ! here - it forces the scalaVersion
          s"++ $v!" :: s"${bridge.id}/publishLocal" :: Nil
        }
        s"${interface.id}/publishLocal" :: bridgeCommands ::: s"++ $version!" :: state
      }
    }

    def publishBridgesAndTest(bridge: Project, interface: Project): Command = {
      Command.args("publishBridgesAndTest", "<version>") { (state, args) =>
        require(args.nonEmpty, "Missing arguments to publishBridgesAndTest.")
        val version = args mkString ""
        val bridgeCommands = bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v" :: s"${bridge.id}/publishLocal" :: Nil
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

    val release: Command =
      Command.command("release")(st =>
        "clean" :: // This is required since version number is generated in properties file.
          "+compile" ::
          "+publishSigned" ::
          "reload" ::
          st
      )

    def runBenchmarks(benchmarkProject: Project): Command = {
      val projectId = benchmarkProject.id
      val dirPath = benchmarksTestDir.getAbsolutePath
      val runPreSetup = s"$projectId/run $dirPath"
      val runBenchmark = s"$projectId/jmh:run -p _tempDir=$dirPath -prof gc"
      val tearDownResources = s"$projectId/${tearDownBenchmarkResources.key.label}"
      Command.command("runBenchmarks")(st => runPreSetup :: runBenchmark :: tearDownResources :: st)
    }

    def all(bridge: Project, bridgeTest: Project, interface: Project, bench: Project): Seq[Command] = {
      val crossTest = crossTestBridges(bridgeTest)
      val publishBridges = publishBridgesAndSet(bridge, interface)
      val publishBridgesTest = publishBridgesAndTest(bridge, interface)
      val runBench = runBenchmarks(bench)
      List(crossTest, publishBridges, publishBridgesTest, runBench, release)
    }
  }

  object BuildDefaults {
    private[this] val statusCommands = List(
      List("diff-index", "--cached", "HEAD"),
      List("diff-index", "HEAD"),
      List("diff-files"),
      List("ls-files", "--exclude-standard", "--others")
    )

    // https://github.com/sbt/sbt-git/issues/109
    // Workaround from https://github.com/sbt/sbt-git/issues/92#issuecomment-161853239
    val gitUncommittedChanges: Def.Initialize[Boolean] = Def.setting {
      val dir = baseDirectory.value
      // can't use git.runner.value because it's a task
      val runner = com.typesafe.sbt.git.ConsoleGitRunner
      // sbt/zinc#334 Seemingly "git status" resets some stale metadata.
      runner("status")(dir, com.typesafe.sbt.git.NullLogger)
      val uncommittedChanges = statusCommands.flatMap { c =>
        val res = runner(c: _*)(dir, com.typesafe.sbt.git.NullLogger)
        if (res.isEmpty) Nil else List(c -> res)
      }
      val logger = sLog.value
      val areUncommitted = uncommittedChanges.nonEmpty
      if (areUncommitted) {
        uncommittedChanges.foreach {
          case (cmd, res) =>
            logger.debug(s"""Uncommitted changes found via "${cmd.mkString(" ")}":\n$res""")
        }
      }
      areUncommitted
    }

    val zincPreviousArtifacts: Def.Initialize[Set[ModuleID]] = Def.setting {
      val zincModule = (organization.value % moduleName.value % "1.0.0")
        .cross(if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
      Set(zincModule)
    }

    val zincScalacOptionsRedefinition: Def.Initialize[Task[Seq[String]]] = {
      val exclude = Set(
        "-Xfatal-warnings",
        "-deprecation",
        "-Ywarn-unused",
        "-Ywarn-unused-import",
        "-YdisableFlatCpCaching"
      )
      Def.task {
        val old = scalacOptions.value
        scalaBinaryVersion.value match {
          case v if v == "2.12" || v == "2.13" => old
          case _                               => old.filterNot(exclude.contains)
        }
      }
    }

    val zincPublishLocalImpl: Def.Initialize[Task[Unit]] = Def.task {
      import sbt.internal.librarymanagement._
      val logger = streams.value.log
      val config = (publishLocalConfiguration).value
      val ivy = new IvySbt((ivyConfiguration.value))
      val moduleSettings = Keys.moduleSettings.value
      val module = new ivy.Module(moduleSettings)
      val newConfig = config.withResolverName(ZincAlternativeRepoName).withOverwrite(false)
      logger.info(s"Publishing $module to local repo: $ZincAlternativeRepoName")
      Set(IvyActions.publish(module, newConfig, logger))
    }

    val noPublishSettings: Seq[Setting[_]] = List(
      publish := {},
      publishLocal := {},
      publishArtifact in Compile := false,
      publishArtifact in Test := false,
      publishArtifact := false,
      skip in publish := true,
    )

    private[this] def wrapIn(color: String, content: String): String = {
      import sbt.internal.util.ConsoleAppender
      if (!ConsoleAppender.formatEnabledInEnv) content
      else color + content + scala.Console.RESET
    }

    val cleanSbtBridge: Def.Initialize[Task[Unit]] = Def.task {
      val sbtV = sbtVersion.value
      val sbtOrg = "org.scala-sbt"
      val sbtScalaVersion = "2.10.6"
      val bridgeVersion = version.value
      val scalaV = scalaVersion.value

      // Assumes that JDK version is the same than the one that publishes the bridge
      val classVersion = System.getProperty("java.class.version")

      val home = System.getProperty("user.home")
      val org = organization.value
      val artifact = moduleName.value
      val artifactName = s"$org-$artifact-$bridgeVersion-bin_${scalaV}__$classVersion"

      val targetsToDelete = List(
        // We cannot use the target key, it's not scoped in `ThisBuild` nor `Global`.
        (baseDirectory in ThisBuild).value / "target" / "zinc-components",
        file(home) / ".ivy2/cache" / sbtOrg / artifactName,
        file(home) / ".ivy2/local" / sbtOrg / artifactName,
        file(home) / ".sbt/boot" / s"scala-$sbtScalaVersion" / sbtOrg / "sbt" / sbtV / artifactName
      )

      val logger = streams.value.log
      logger.info(wrapIn(scala.Console.BOLD, "Cleaning stale compiler bridges:"))
      targetsToDelete.foreach { target =>
        IO.delete(target)
        logger.info(s"${wrapIn(scala.Console.GREEN, "  ✓ ")}${target.getAbsolutePath}")
      }
    }

    private[this] val scalaPartialVersion =
      Def.setting(CrossVersion.partialVersion(scalaVersion.value))

    val handleScalaSpecificSources: Def.Initialize[List[File]] = Def.setting {
      val source = scalaSource.value
      scalaPartialVersion.value.collect {
        case (2, y) if y == 10            => new File(source.getPath + "_2.10")
        case (2, y) if y == 11 || y == 12 => new File(source.getPath + "_2.11-12")
        case (2, y) if y >= 13            => new File(source.getPath + "_2.13")
      }.toList
    }

    val customCompilerBridge: Def.Initialize[ModuleID] = Def.setting {
      val old = scalaCompilerBridgeSource.value
      val bootstrapModule =
        "org.scala-sbt" % "compiler-bridge_2.13.0-M2" % "1.1.0-M1-bootstrap2" % Compile sources()
      scalaVersion.value match {
        case x if x startsWith "2.13." => bootstrapModule
        case _                         => old
      }
    }

    def zincScripted(
        bridgeRef: Project,
        interfaceRef: Project,
        scriptedRef: Project,
    ): Def.Initialize[InputTask[Unit]] = Def.inputTask {
      val result = scriptedSource(dir => (_: State) => scriptedParser(dir)).parsed
      scriptedPublishAll.value // We first publish all the zinc modules

      // Publish the interface and the bridge to an alternative location where
      // scripted then resolves them from
      (zincPublishLocal in interfaceRef).value
      (zincPublishLocal in bridgeRef).value

      val scriptedClasspath = (fullClasspath in scriptedRef in Test).value
      val instance = (scalaInstance in scriptedRef).value
      val source = scriptedSource.value
      val logged = scriptedBufferLog.value
      val hook = scriptedPrescripted.value
      doScripted(scriptedClasspath, instance, source, result, logged, hook)
    }

    def zincOnlyScripted(scriptedRef: Project): Def.Initialize[InputTask[Unit]] = Def.inputTask {
      val result = scriptedSource(dir => (_: State) => scriptedParser(dir)).parsed
      val scriptedClasspath = (fullClasspath in scriptedRef in Test).value
      val instance = (scalaInstance in scriptedRef).value
      val source = scriptedSource.value
      val logged = scriptedBufferLog.value
      val hook = scriptedPrescripted.value
      doScripted(scriptedClasspath, instance, source, result, logged, hook)
    }

    private[this] val ZincAlternativeResolverPlugin = s"""
       |import sbt._
       |import Keys._
       |
       |object AddResolverPlugin extends AutoPlugin {
       |  override def requires = sbt.plugins.JvmPlugin
       |  override def trigger = allRequirements
       |
       |  override lazy val projectSettings = Seq(resolvers += alternativeLocalResolver)
       |  lazy val alternativeLocalResolver = Resolver.file("$ZincAlternativeRepoName", file("$ZincAlternativeRepoPath"))(Resolver.ivyStylePatterns)
       |}
       |""".stripMargin

    def addSbtAlternateResolver(scriptedRoot: File): Unit = {
      val resolver = scriptedRoot / "project" / "AddResolverPlugin.scala"
      if (!resolver.exists) IO.write(resolver, ZincAlternativeResolverPlugin)
      else ()
    }

    val tearDownBenchmarkResources: Def.Initialize[Task[Unit]] =
      Def.task(IO.delete(benchmarksTestDir))
  }
}
