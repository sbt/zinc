import sbt.{ AutoPlugin, Compile, Def, Keys, Resolver, Test, TestFrameworks, Tests, URL, Project }
import com.typesafe.sbt.SbtGit.{ git => GitKeys }
import bintray.BintrayPlugin.{ autoImport => BintrayKeys }
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.{ autoImport => ScalafmtKeys }
import com.lucidchart.sbt.scalafmt.ScalafmtSbtPlugin.autoImport.Sbt
import com.typesafe.tools.mima.plugin.MimaKeys

object BuildPlugin extends AutoPlugin {
  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements
  val autoImport = BuildKeys

  override def projectSettings: Seq[Def.Setting[_]] = BuildImplementation.projectSettings
  override def buildSettings: Seq[Def.Setting[_]] = BuildImplementation.buildSettings
  override def globalSettings: Seq[Def.Setting[_]] = BuildImplementation.globalSettings
}

object BuildKeys {
  import sbt.{ file, File, Developer, url }
  import BuildImplementation.{ BuildDefaults, BuildResolvers }

  val baseVersion: String = "1.1.0-SNAPSHOT"
  val internalPath: File = file("internal")
  val bridgeScalaVersions: List[String] =
    List(Dependencies.scala212, Dependencies.scala211, Dependencies.scala210)

  val ZincGitHomepage: URL = url("https://github.com/sbt/zinc")
  val ScalaCenterMaintainer: Developer =
    Developer("jvican", "Jorge Vicente Cantero", "@jvican", url("https://github.com/jvican"))

  // Ids that we will use to name our projects in build.sbt
  val CompilerInterfaceId = "compiler-interface"
  val CompilerBridgeId = "compiler-bridge"
  val ZincApiInfoId = "zinc-apiinfo"

  // Defines the constants for the alternative publishing
  val ZincAlternativeCacheName = "alternative-local"
  val ZincAlternativeCacheDir: File = file(sys.props("user.home") + "/.ivy2/zinc-alternative")

  // Defines several settings that are exposed to the projects definition in build.sbt
  private[this] val noPublishSettings: Seq[Def.Setting[_]] = BuildDefaults.noPublishSettings
  def noPublish(p: Project): Project = p.settings(noPublishSettings)

  val adaptOptionsForOldScalaVersions: Seq[Def.Setting[_]] =
    List(Keys.scalacOptions := BuildDefaults.zincScalacOptionsRedefinition.value)
  // Sets up mima settings for modules that have to be binary compatible with Zinc 1.0.0
  val mimaSettings: Seq[Def.Setting[_]] =
    List(MimaKeys.mimaPreviousArtifacts := BuildDefaults.zincPreviousArtifacts.value)

  import sbt.{ TaskKey, taskKey }
  val scriptedPublishAll = taskKey[Unit]("Publishes all the Zinc artifacts for scripted")
  val cleanSbtBridge: TaskKey[Unit] = taskKey[Unit]("Cleans the sbt bridge.")
  val zincPublishLocal: TaskKey[Unit] =
    taskKey[Unit]("Publishes Zinc artifacts to a alternative local cache.")
  val zincPublishLocalSettings: Seq[Def.Setting[_]] = List(
    Keys.resolvers += BuildResolvers.AlternativeLocalResolver,
    zincPublishLocal := BuildDefaults.zincPublishLocal.value,
  )

  val tearDownBenchmarkResources: TaskKey[Unit] = taskKey[Unit]("Remove benchmark resources.")
  val benchmarksTestDir = sbt.IO.createTemporaryDirectory

  val sourcesForAllScalaVersionsSetting: Seq[Def.Setting[_]] =
    List(Keys.unmanagedSourceDirectories ++= BuildDefaults.handleScalaSpecificSources.value)
  def inCompileAndTest(ss: Def.Setting[_]): Seq[Def.Setting[_]] =
    List(Compile, Test).flatMap(sbt.inConfig(_)(ss))
}

object BuildImplementation {
  import sbt.{ ScmInfo }
  val buildSettings: Seq[Def.Setting[_]] = List(
    Scripted.scriptedBufferLog := true,
    GitKeys.baseVersion := BuildKeys.baseVersion,
    GitKeys.gitUncommittedChanges := BuildDefaults.gitUncommitedChanges.value,
    BintrayKeys.bintrayPackage := "zinc",
    ScalafmtKeys.scalafmtOnCompile := true,
    ScalafmtKeys.scalafmtVersion := "1.2.0",
    ScalafmtKeys.scalafmtOnCompile in Sbt := false,
    Keys.description := "Incremental compiler of Scala",
    // The rest of the sbt developers come from the Sbt Houserules plugin
    Keys.developers += BuildKeys.ScalaCenterMaintainer,
    // TODO(jvican): Remove `scmInfo` and `homepage` when we have support for sbt-release-early
    Keys.homepage := Some(BuildKeys.ZincGitHomepage),
    Keys.scmInfo := Some(ScmInfo(BuildKeys.ZincGitHomepage, "git@github.com:sbt/zinc.git")),
    Keys.version := {
      val previous = Keys.version.value
      if (previous.contains("-SNAPSHOT")) GitKeys.baseVersion.value else previous
    },
  )

  val globalSettings: Seq[Def.Setting[_]] = List(
    Keys.commands ++= BuildCommands.all
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
    Keys.scalacOptions += "-YdisableFlatCpCaching"
  )

  object BuildResolvers {
    import sbt.{ MavenRepository, file }
    val TypesafeReleases: Resolver = Resolver.typesafeIvyRepo("releases")
    val SonatypeSnapshots: Resolver = Resolver.sonatypeRepo("snapshots")
    val BintrayMavenReleases: Resolver =
      MavenRepository("bintray-sbt-maven-releases", "https://dl.bintray.com/sbt/maven-releases/")
    val BintraySbtIvySnapshots: Resolver =
      Resolver.url("bintray-sbt-ivy-snapshots",
                   new URL("https://dl.bintray.com/sbt/ivy-snapshots/"))(Resolver.ivyStylePatterns)
    val all: List[Resolver] =
      List(TypesafeReleases, SonatypeSnapshots, BintrayMavenReleases, BintraySbtIvySnapshots)

    import BuildKeys.{ ZincAlternativeCacheName, ZincAlternativeCacheDir }
    val AlternativeLocalResolver: Resolver =
      Resolver.file(ZincAlternativeCacheName, ZincAlternativeCacheDir)(Resolver.ivyStylePatterns)

    // Naive way of implementing a filter to remove repeated resolvers.
    def removeRepeatedResolvers(rs: Seq[Resolver]): Seq[Resolver] = rs.toSet.toVector
  }

  object BuildCommands {
    import sbt.{ Command, State }
    import BuildKeys.{ CompilerBridgeId, ZincApiInfoId, CompilerInterfaceId, bridgeScalaVersions }
    val crossTestBridges: Command = {
      Command.command("crossTestBridges") { (state: State) =>
        (bridgeScalaVersions.flatMap { (bridgeVersion: String) =>
          // Note the ! here. You need this so compilerInterface gets forced to the scalaVersion
          s"++ $bridgeVersion!" :: s"$CompilerBridgeId/test" :: Nil
        }) ::: (s"++ ${Dependencies.scala212}!" :: state)
      }
    }

    val publishBridgesAndSet: Command = {
      Command.args("publishBridgesAndSet", "<version>") { (state, args) =>
        require(args.nonEmpty, "Missing Scala version argument.")
        val userScalaVersion = args.mkString("")
        s"$CompilerInterfaceId/publishLocal" :: bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v!" :: s"$ZincApiInfoId/publishLocal" :: s"$CompilerBridgeId/publishLocal" :: Nil
        } ::: s"++ $userScalaVersion!" :: state
      }
    }

    val publishBridgesAndTest: Command = Command.args("publishBridgesAndTest", "<version>") {
      (state, args) =>
        require(args.nonEmpty, "Missing arguments to publishBridgesAndTest.")
        val version = args mkString ""
        val bridgeCommands: List[String] = bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v" :: s"$ZincApiInfoId/publishLocal" :: s"$CompilerBridgeId/publishLocal" :: Nil
        }
        s"$CompilerInterfaceId/publishLocal" ::
          bridgeCommands :::
          s"++ $version" ::
          s"zincRoot/scalaVersion" ::
          s"zincRoot/test" ::
          s"zincRoot/scripted" ::
          state
    }

    val release: Command =
      Command.command("release")(st => "clean" :: "+compile" :: "+publishSigned" :: "reload" :: st)

    def runBenchmarks(benchmarkProject: Project): Command = {
      val dirPath = BuildKeys.benchmarksTestDir.getAbsolutePath
      val projectId = benchmarkProject.id
      val runPreSetup = s"$projectId/run $dirPath"
      val runBenchmark = s"$projectId/jmh:run -p _tempDir=$dirPath -prof gc"
      val tearDownResources = s"$projectId/tearDownBenchmarkResources"
      Command.command("runBenchmarks")(st => runPreSetup :: runBenchmark :: tearDownResources :: st)
    }

    val all: List[Command] = List(crossTestBridges, publishBridgesAndSet, publishBridgesAndTest)
  }

  object BuildDefaults {
    import BuildKeys.{ ZincAlternativeCacheName, ZincAlternativeCacheDir }
    import sbt.{ Task, State, fileToRichFile, file, File, IO }
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

    val zincPublishLocal: Def.Initialize[Task[Unit]] = Def.task {
      import sbt.internal.librarymanagement._
      val logger = Keys.streams.value.log
      val config = (Keys.publishLocalConfiguration).value
      val ivy = new IvySbt((Keys.ivyConfiguration.value))
      val moduleSettings = (Keys.moduleSettings).value
      val module = new ivy.Module(moduleSettings)
      val newConfig = config.withResolverName(ZincAlternativeCacheName).withOverwrite(false)
      logger.info(s"Publishing $module to local repo: $ZincAlternativeCacheName")
      Set(IvyActions.publish(module, newConfig, logger))
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

    val cleanSbtBridge: Def.Initialize[Task[Unit]] = Def.task {
      val sbtV = Keys.sbtVersion.value
      val sbtOrg = "org.scala-sbt"
      val sbtScalaVersion = "2.10.6"
      val bridgeVersion = Keys.version.value
      val scalaV = Keys.scalaVersion.value

      // Assumes that JDK version is the same than the one that publishes the bridge
      val classVersion = System.getProperty("java.class.version")

      val home = System.getProperty("user.home")
      val org = Keys.organization.value
      val artifact = Keys.moduleName.value
      val artifactName = s"$org-$artifact-$bridgeVersion-bin_${scalaV}__$classVersion"

      val targetsToDelete = List(
        // We cannot use the target key, it's not scoped in `ThisBuild` nor `Global`.
        (Keys.baseDirectory in sbt.ThisBuild).value / "target" / "zinc-components",
        file(home) / ".ivy2/cache" / sbtOrg / artifactName,
        file(home) / ".ivy2/local" / sbtOrg / artifactName,
        file(home) / ".sbt/boot" / s"scala-$sbtScalaVersion" / sbtOrg / "sbt" / sbtV / artifactName
      )

      val logger = Keys.streams.value.log
      logger.info(wrapIn(scala.Console.BOLD, "Cleaning stale compiler bridges:"))
      targetsToDelete.foreach { target =>
        IO.delete(target)
        logger.info(s"${wrapIn(scala.Console.GREEN, "  ✓ ")}${target.getAbsolutePath}")
      }
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
      val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
      // We first publish all the zinc modules
      BuildKeys.scriptedPublishAll.value

      val source = scriptedSource.value
      val logged = scriptedBufferLog.value
      val hook = scriptedPrescripted.value

      // Publish the interface and the bridge for scripted to resolve them correctly
      (BuildKeys.zincPublishLocal in interfaceRef).value
      (BuildKeys.zincPublishLocal in bridgeRef).value

      val scriptedClasspath = (Keys.fullClasspath in scriptedRef in Test).value
      val instance = (Keys.scalaInstance in scriptedRef in Test).value
      Scripted.doScripted(scriptedClasspath, instance, source, result, logged, hook)
    }

    def zincOnlyScripted(scriptedRef: Project): Def.Initialize[InputTask[Unit]] = Def.inputTask {
      val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
      val scriptedClasspath = (Keys.fullClasspath in scriptedRef in Test).value
      val instance = (Keys.scalaInstance in scriptedRef in Test).value
      val source = scriptedSource.value
      val logged = scriptedBufferLog.value
      val hook = scriptedPrescripted.value
      Scripted.doScripted(scriptedClasspath, instance, source, result, logged, hook)
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
       |  lazy val alternativeLocalResolver = Resolver.file("$ZincAlternativeCacheName", file("${ZincAlternativeCacheDir.getAbsolutePath}"))(Resolver.ivyStylePatterns)
       |}
       |""".stripMargin

    def addSbtAlternateResolver(scriptedRoot: File): Unit = {
      val resolver = scriptedRoot / "project" / "AddResolverPlugin.scala"
      if (!resolver.exists) IO.write(resolver, ZincAlternativeResolverPlugin)
      else ()
    }

    val tearDownBenchmarkResources: Def.Initialize[Task[Unit]] =
      Def.task(IO.delete(BuildKeys.benchmarksTestDir))
  }
}
