import Util._
import Dependencies._
import localzinc.Scripted, Scripted._
import com.typesafe.tools.mima.core._, ProblemFilters._

def internalPath = file("internal")

def mimaSettings: Seq[Setting[_]] = Seq(
  mimaPreviousArtifacts := Set(
    "1.0.0", "1.0.1", "1.0.2", "1.0.3", "1.0.4", "1.0.5",
    "1.1.0", "1.1.1", "1.1.2", "1.1.3",
    "1.2.0",
  ) map (version =>
    organization.value %% moduleName.value % version
      cross (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
  ),
)

def buildLevelSettings: Seq[Setting[_]] = Seq(
  git.baseVersion := "1.2.1",
  // https://github.com/sbt/sbt-git/issues/109
  // Workaround from https://github.com/sbt/sbt-git/issues/92#issuecomment-161853239
  git.gitUncommittedChanges := {
    val statusCommands = Seq(
      Seq("diff-index", "--cached", "HEAD"),
      Seq("diff-index", "HEAD"),
      Seq("diff-files"),
      Seq("ls-files", "--exclude-standard", "--others")
    )
    // can't use git.runner.value because it's a task
    val runner = com.typesafe.sbt.git.ConsoleGitRunner
    val dir = baseDirectory.value
    // sbt/zinc#334 Seemingly "git status" resets some stale metadata.
    runner("status")(dir, com.typesafe.sbt.git.NullLogger)
    val uncommittedChanges = statusCommands flatMap { c =>
      val res = runner(c: _*)(dir, com.typesafe.sbt.git.NullLogger)
      if (res.isEmpty) Nil else Seq(c -> res)
    }

    val un = uncommittedChanges.nonEmpty
    if (un) {
      uncommittedChanges foreach {
        case (cmd, res) =>
          sLog.value debug s"""Uncommitted changes found via "${cmd mkString " "}":\n${res}"""
      }
    }
    un
  },
  version := {
    val v = version.value
    if (v contains "SNAPSHOT") git.baseVersion.value + "-SNAPSHOT"
    else v
  },
  bintrayPackage := "zinc",
  scmInfo := Some(ScmInfo(url("https://github.com/sbt/zinc"), "git@github.com:sbt/zinc.git")),
  description := "Incremental compiler of Scala",
  homepage := Some(url("https://github.com/sbt/zinc")),
  developers +=
    Developer("jvican", "Jorge Vicente Cantero", "@jvican", url("https://github.com/jvican")),
  scalafmtOnCompile in Sbt := false,
)

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := scala212,
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += "bintray-sbt-maven-releases" at "https://dl.bintray.com/sbt/maven-releases/",
  resolvers += Resolver.url(
    "bintray-sbt-ivy-snapshots",
    new URL("https://dl.bintray.com/sbt/ivy-snapshots/"))(Resolver.ivyStylePatterns),
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  crossScalaVersions := Seq(scala212),
  publishArtifact in Test := false,
  commands ++= Seq(publishBridges, crossTestBridges),
  scalacOptions ++= Seq(
    "-YdisableFlatCpCaching",
    "-target:jvm-1.8",
  )
)

def compilerVersionDependentScalacOptions: Seq[Setting[_]] = Seq(
  scalacOptions := {
    val old = scalacOptions.value
    scalaBinaryVersion.value match {
      case "2.12" => old ++ List("-opt-inline-from:<sources>", "-opt:l:inline", "-Yopt-inline-heuristics:at-inline-annotated")
      case _ =>
        old filterNot Set(
          "-Xfatal-warnings",
          "-deprecation",
          "-Ywarn-unused",
          "-Ywarn-unused-import",
          "-YdisableFlatCpCaching",
        )
    }
  }
)

def minimalSettings: Seq[Setting[_]] = commonSettings

// def minimalSettings: Seq[Setting[_]] =
//   commonSettings ++ customCommands ++
//   publishPomSettings ++ Release.javaVersionCheckSettings

def baseSettings: Seq[Setting[_]] =
  minimalSettings
//   minimalSettings ++ baseScalacOptions ++ Licensed.settings ++ Formatting.settings

def addBaseSettingsAndTestDeps(p: Project): Project =
  p.settings(baseSettings).configure(addTestDependencies)

val noPublish: Seq[Setting[_]] = List(
  publish := {},
  publishLocal := {},
  publishArtifact in Compile := false,
  publishArtifact in Test := false,
  publishArtifact := false,
  skip in publish := true,
)

// TODO: Test Scala 2.13.0 when we upgrade to M5 (this means we need to publish sbt dependencies for this milestone too)

// zincRoot is now only 2.12 (2.11.x is not supported anymore)
lazy val zincRoot: Project = (project in file("."))
  .aggregate(
    zinc,
    zincTesting,
    zincPersist,
    zincCore,
    zincIvyIntegration,
    zincCompile,
    zincCompileCore,
    compilerInterface212,
    compilerBridge210,
    compilerBridge211,
    compilerBridge212,
    compilerBridge213,
    zincBenchmarks,
    zincApiInfo212,
    zincClasspath212,
    zincClassfile212,
    zincScripted
  )
  .settings(
    inThisBuild(buildLevelSettings),
    minimalSettings,
    otherRootSettings,
    noPublish,
    name := "zinc Root",
    customCommands
  )

lazy val zinc = (project in file("zinc"))
  .dependsOn(
    zincCore,
    zincPersist,
    zincCompileCore,
    zincClassfile212,
    zincIvyIntegration % "compile->compile;test->test",
    zincTesting % Test
  )
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc",
    mimaSettings,
  )

lazy val zincTesting = (project in internalPath / "zinc-testing")
  .settings(
    minimalSettings,
    noPublish,
    name := "zinc Testing",
    libraryDependencies ++= Seq(scalaCheck, scalatest, junit, sjsonnewScalaJson.value)
  )
  .configure(addSbtLmCore, addSbtLmIvy)

lazy val zincCompile = (project in file("zinc-compile"))
  .dependsOn(zincCompileCore, zincCompileCore % "test->test")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Compile",
    mimaSettings,
  )
  .configure(addSbtUtilTracking)

// Persists the incremental data structures using Protobuf
lazy val zincPersist = (project in internalPath / "zinc-persist")
  .dependsOn(zincCore, zincCore % "test->test")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Persist",
    libraryDependencies += sbinary,
    compileOrder := sbt.CompileOrder.Mixed,
    PB.targets in Compile := List(scalapb.gen() -> (sourceManaged in Compile).value),
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.BinaryAnalysisFormat.writeAPIs"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.BinaryAnalysisFormat.readAPIs"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.converters.ProtobufWriters.toApisFile"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.converters.ProtobufWriters.toApis"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.converters.ProtobufWriters.toAnalyzedClass"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.converters.ProtobufReaders.fromApis"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.converters.ProtobufReaders.fromApisFile"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.converters.ProtobufReaders.fromAnalyzedClass"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.AnalyzedClass.apply"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.AnalyzedClass.copy"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.AnalyzedClass.this"),
        exclude[ReversedMissingMethodProblem]("sbt.internal.inc.schema.Version.isV11"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.binary.converters.ProtobufReaders.this")
      )
    }
  )

// Implements the core functionality of detecting and propagating changes incrementally.
//   Defines the data structures for representing file fingerprints and relationships and the overall source analysis
lazy val zincCore = (project in internalPath / "zinc-core")
  .dependsOn(
    zincApiInfo212,
    zincClasspath212,
    compilerInterface212,
    compilerBridge212 % Test,
    zincTesting % Test
  )
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    fork in Test := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    name := "zinc Core",
    compileOrder := sbt.CompileOrder.Mixed,
    mimaSettings,
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtUtilRelation)

lazy val zincBenchmarks = (project in internalPath / "zinc-benchmarks")
  .dependsOn(compilerInterface212 % "compile->compile;compile->test")
  .dependsOn(compilerBridge212, zincCore, zincTesting % Test)
  .enablePlugins(JmhPlugin)
  .settings(
    noPublish,
    name := "Benchmarks of Zinc and the compiler bridge",
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.0.201612231935-r",
      "net.openhft" % "affinity" % "3.0.6"
    ),
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    javaOptions in Test ++= List("-Xmx600M", "-Xms600M"),
  )

lazy val zincIvyIntegration = (project in internalPath / "zinc-ivy-integration")
  .dependsOn(zincCompileCore, zincTesting % Test)
  .settings(
    baseSettings,
    name := "zinc Ivy Integration",
    compileOrder := sbt.CompileOrder.ScalaThenJava,
    mimaSettings,
    test in Test := {
      // Test in ivy integration needs the published compiler bridges to work
      (test in Test).value
    }
  )
  .configure(addSbtLmCore, addSbtLmIvyTest)

// sbt-side interface to compiler.  Calls compiler-side interface reflectively
lazy val zincCompileCore = (project in internalPath / "zinc-compile-core")
  .enablePlugins(ContrabandPlugin)
  .dependsOn(
    compilerInterface212 % "compile;test->test",
    zincClasspath212,
    zincApiInfo212,
    zincClassfile212,
    zincTesting % Test
  )
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Compile Core",
    libraryDependencies ++= Seq(scalaCompiler.value % Test, launcherInterface, parserCombinator),
    unmanagedJars in Test := Seq(packageSrc in compilerBridge212 in Compile value).classpath,
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-java",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-java",
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
        // PositionImpl is a private class only invoked in the same source.
        exclude[FinalClassProblem]("sbt.internal.inc.javac.DiagnosticsReporter$PositionImpl"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.javac.DiagnosticsReporter#PositionImpl.this"),
      )
    },
  )
  .configure(addSbtUtilLogging, addSbtIO, addSbtUtilControl)

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the sbt-contraband plugin.
lazy val compilerInterface212 = (project in internalPath / "compiler-interface")
  .enablePlugins(ContrabandPlugin)
  .settings(
    minimalSettings,
    // javaOnlySettings,
    name := "Compiler Interface",
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    compilerVersionDependentScalacOptions,
    libraryDependencies ++= Seq(scalaLibrary.value % Test),
    exportJars := true,
    resourceGenerators in Compile += Def
      .task(
        generateVersionFile(
          version.value,
          resourceManaged.value,
          streams.value,
          compile in Compile value
        )
      )
      .taskValue,
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-java",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-java",
    crossPaths := false,
    autoScalaLibrary := false,
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
        exclude[ReversedMissingMethodProblem]("xsbti.compile.ExternalHooks#Lookup.hashClasspath"),
        exclude[ReversedMissingMethodProblem]("xsbti.compile.ScalaInstance.loaderLibraryOnly"),
        exclude[DirectMissingMethodProblem]("xsbti.api.AnalyzedClass.of"),
        exclude[DirectMissingMethodProblem]("xsbti.api.AnalyzedClass.create")
      )
    },
  )
  .configure(addSbtUtilInterface)

/* Create a duplicated compiler-interface project that uses Scala 2.10 to parse Java files.
 * Scala 2.10's parser uses Java 6 semantics, so this way we ensure that the interface can
 * be compiled with Java 6 too. `compiler-interface` checks compilation for Java 8. */
val compilerInterface210 = compilerInterface212
  .withId("compilerInterface210")
  .settings(
    scalaVersion := scala210,
    crossScalaVersions := Seq(scala210),
    target := (target in compilerInterface212).value.getParentFile / "target-2.10",
    skip in publish := true
  )

val compilerInterface211 = compilerInterface212
  .withId("compilerInterface211")
  .settings(
    scalaVersion := scala211,
    crossScalaVersions := Seq(scala211),
    target := (target in compilerInterface212).value.getParentFile / "target-2.11",
    skip in publish := true
  )

val compilerInterface213 = compilerInterface212
  .withId("compilerInterface213")
  .settings(
    scalaVersion := scala213,
    crossScalaVersions := Seq(scala213),
    target := (target in compilerInterface212).value.getParentFile / "target-2.13",
    skip in publish := true
  )

val cleanSbtBridge = taskKey[Unit]("Cleans the sbt bridge.")

def wrapIn(color: String, content: String): String = {
  import sbt.internal.util.ConsoleAppender
  if (!ConsoleAppender.formatEnabledInEnv) content
  else color + content + scala.Console.RESET
}

def noSourcesForTemplate: Seq[Setting[_]] = inBoth(
  sources := {
    val oldSources = sources.value
    if (Keys.thisProject.value.id.contains("Template")) Seq.empty[File] else oldSources
  },
)

/**
 * Compiler-side interface to compiler that is compiled against the compiler being used either in advance or on the fly.
 * Includes API and Analyzer phases that extract source API and relationships.
 * As this is essentially implementations of the compiler-interface (per Scala compiler),
 * the code here should not be consumed without going through the classloader trick and the interface.
 * Due to the hermetic nature of the bridge, there's no necessity to keep binary compatibility across Zinc versions,
 * and therefore there's no `mimaSettings` added.
 * For the case of Scala 2.13 bridge, we didn't even have the bridge to compare against when Zinc 1.0.0 came out.
 */
lazy val compilerBridgeTemplate: Project = (project in internalPath / "compiler-bridge")
  .settings(
    baseSettings,
    noSourcesForTemplate,
    compilerVersionDependentScalacOptions,
    libraryDependencies += scalaCompiler.value % "provided",
    autoScalaLibrary := false,
    // precompiledSettings,
    name := "Compiler Bridge",
    exportJars := true,
    inBoth(unmanagedSourceDirectories ++= scalaPartialVersion.value.collect {
      case (2, y) if y == 10            => new File(scalaSource.value.getPath + "_2.10")
      case (2, y) if y == 11 || y == 12 => new File(scalaSource.value.getPath + "_2.11-12")
      case (2, y) if y >= 13            => new File(scalaSource.value.getPath + "_2.13")
    }.toList),
    // Use a bootstrap compiler bridge to compile the compiler bridge.
    scalaCompilerBridgeSource := {
      val old = scalaCompilerBridgeSource.value
      scalaVersion.value match {
        case x if x startsWith "2.13." => ("org.scala-sbt" % "compiler-bridge_2.13.0-M2" % "1.1.0-M1-bootstrap2" % Compile).sources()
        case _ => old
      }
    },
    cleanSbtBridge := {
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
      val artifactName =
        s"$org-$artifact-$bridgeVersion-bin_${scalaV}__$classVersion"

      val targetsToDelete = List(
        // We cannot use the target key, it's not scoped in `ThisBuild` nor `Global`.
        (baseDirectory in ThisBuild).value / "target" / "zinc-components",
        file(home) / ".ivy2/cache" / sbtOrg / artifactName,
        file(home) / ".sbt/boot" / s"scala-$sbtScalaVersion" / sbtOrg / "sbt" / sbtV / artifactName
      )
      val logger = streams.value.log
      logger.info(wrapIn(scala.Console.BOLD, "Cleaning stale compiler bridges:"))
      targetsToDelete.foreach { target =>
        IO.delete(target)
        logger.info(s"${wrapIn(scala.Console.GREEN, "  ✓ ")}${target.getAbsolutePath}")
      }
    },
    publishLocal := publishLocal.dependsOn(cleanSbtBridge).value,
  )

lazy val compilerBridge210 = compilerBridgeTemplate
  .withId("compilerBridge210")
  .dependsOn(compilerInterface210)
  .settings(
    scalaVersion := scala210,
    crossScalaVersions := Seq(scala210),
    target := (target in compilerBridgeTemplate).value.getParentFile / "target-2.10"
  )

lazy val compilerBridge211 = compilerBridgeTemplate
  .withId("compilerBridge211")
  .dependsOn(compilerInterface211)
  .settings(
    scalaVersion := scala211,
    crossScalaVersions := Seq(scala211),
    target := (target in compilerBridgeTemplate).value.getParentFile / "target-2.11"
  )

lazy val compilerBridge212 = compilerBridgeTemplate
  .withId("compilerBridge212")
  .dependsOn(compilerInterface212)
  .settings(
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    target := (target in compilerBridgeTemplate).value.getParentFile / "target-2.12"
  )

lazy val compilerBridge213 = compilerBridgeTemplate
  .withId("compilerBridge213")
  .dependsOn(compilerInterface213)
  .settings(
    scalaVersion := scala213,
    crossScalaVersions := Seq(scala213),
    target := (target in compilerBridgeTemplate).value.getParentFile / "target-2.13"
  )

/**
 * Tests for the compiler bridge.
 * This is split into a separate subproject because testing introduces more dependencies
 * (Zinc API Info, which transitively depends on IO).
 */
lazy val compilerBridgeTestTemplate = (project in internalPath / "compiler-bridge-test")
  .settings(
    name := "Compiler Bridge Test",
    baseSettings,
    compilerVersionDependentScalacOptions,
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    fork in Test := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    libraryDependencies += scalaCompiler.value,
    skip in publish := true,
    autoScalaLibrary := false,
  )

lazy val compilerBridgeTest210 = compilerBridgeTestTemplate
  .withId("compilerBridgeTest210")
  .dependsOn(compilerInterface210 % "test->test")
  .dependsOn(compilerBridge210, zincApiInfo210 % "test->test")
  .settings(
    scalaVersion := scala210,
    crossScalaVersions := Seq(scala210),
    target := (target in compilerBridgeTestTemplate).value.getParentFile / "target-2.10",
    skip in publish := true
  )

lazy val compilerBridgeTest211 = compilerBridgeTestTemplate
  .withId("compilerBridgeTest211")
  .dependsOn(compilerInterface211 % "test->test")
  .dependsOn(compilerBridge211, zincApiInfo211 % "test->test")
  .settings(
    scalaVersion := scala211,
    crossScalaVersions := Seq(scala211),
    target := (target in compilerBridgeTestTemplate).value.getParentFile / "target-2.11",
    skip in publish := true
  )

lazy val compilerBridgeTest212 = compilerBridgeTestTemplate
  .withId("compilerBridgeTest212")
  .dependsOn(compilerInterface212 % "test->test")
  .dependsOn(compilerBridge212, zincApiInfo212 % "test->test")
  .settings(
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    target := (target in compilerBridgeTestTemplate).value.getParentFile / "target-2.12",
    skip in publish := true
  )

val scalaPartialVersion = Def setting (CrossVersion partialVersion scalaVersion.value)

def inBoth(ss: Setting[_]*): Seq[Setting[_]] = Seq(Compile, Test) flatMap (inConfig(_)(ss))

// defines operations on the API of a source, including determining whether it has changed and converting it to a string
//   and discovery of Projclasses and annotations
lazy val zincApiInfoTemplate = (project in internalPath / "zinc-apiinfo")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc ApiInfo",
    compilerVersionDependentScalacOptions,
    noSourcesForTemplate,
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
         exclude[IncompatibleMethTypeProblem]("xsbt.api.HashAPI.hashTypeParameters"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.HashAPI.hashAnnotations"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.HashAPI.hashParameters"),
         exclude[DirectMissingMethodProblem]("xsbt.api.HashAPI.hashDefinitionsWithExtraHashes"),
         exclude[DirectMissingMethodProblem]("xsbt.api.HashAPI.hashSeq"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.HashAPI.hashValueParameters"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.HashAPI.hashAnnotationArguments"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.HashAPI.hashTypes"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.Visit.visitTypeParameters"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.Visit.visitDefinitions"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.Visit.visitAnnotationArguments"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.Visit.visitAnnotations"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.Visit.visitValueParameters"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.Visit.visitParameters"),
         exclude[IncompatibleMethTypeProblem]("xsbt.api.Visit.visitTypes"),
         exclude[DirectMissingMethodProblem]("xsbt.api.HashAPI.apply"),
         exclude[DirectMissingMethodProblem]("xsbt.api.HashAPI.hashStructure0"),
         exclude[DirectMissingMethodProblem]("xsbt.api.HashAPI.hashStructure"),
         exclude[DirectMissingMethodProblem]("xsbt.api.HashAPI.hashDefinitions"),
         exclude[DirectMissingMethodProblem]("xsbt.api.HashAPI.this")
      )
    }
  )

lazy val zincApiInfo210 = zincApiInfoTemplate
  .withId("zincApiInfo210")
  .dependsOn(compilerInterface210, zincClassfile210 % "compile;test->test")
  .settings(
    scalaVersion := scala210,
    crossScalaVersions := Seq(scala210),
    target := (target in zincApiInfoTemplate).value.getParentFile / "target-2.10"
  )

lazy val zincApiInfo211 = zincApiInfoTemplate
  .withId("zincApiInfo211")
  .dependsOn(compilerInterface211, compilerBridge211)
  .dependsOn(zincClassfile211 % "compile;test->test")
  .settings(
    scalaVersion := scala211,
    crossScalaVersions := Seq(scala211),
    target := (target in zincApiInfoTemplate).value.getParentFile / "target-2.11"
  )

lazy val zincApiInfo212 = zincApiInfoTemplate
  .withId("zincApiInfo212")
  .dependsOn(compilerInterface212, compilerBridge212)
  .dependsOn(zincClassfile212 % "compile;test->test")
  .settings(
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    target := (target in zincApiInfoTemplate).value.getParentFile / "target-2.12"
  )

// Utilities related to reflection, managing Scala versions, and custom class loaders
lazy val zincClasspathTemplate = (project in internalPath / "zinc-classpath")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Classpath",
    compilerVersionDependentScalacOptions,
    libraryDependencies ++= Seq(scalaCompiler.value, launcherInterface),
    noSourcesForTemplate,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // Changed the signature of a private[sbt] method
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.classpath.ClasspathUtilities.compilerPlugins"),
    ),
  )
  .configure(addSbtIO)

lazy val zincClasspath210 = zincClasspathTemplate
  .withId("zincClasspath210")
  .dependsOn(compilerInterface210)
  .settings(
    scalaVersion := scala210,
    crossScalaVersions := Seq(scala210),
    target := (target in zincClasspathTemplate).value.getParentFile / "target-2.10"
  )

lazy val zincClasspath211 = zincClasspathTemplate
  .withId("zincClasspath211")
  .dependsOn(compilerInterface211)
  .settings(
    scalaVersion := scala211,
    crossScalaVersions := Seq(scala211),
    target := (target in zincClasspathTemplate).value.getParentFile / "target-2.11"
  )

lazy val zincClasspath212 = zincClasspathTemplate
  .withId("zincClasspath212")
  .dependsOn(compilerInterface212)
  .settings(
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    target := (target in zincClasspathTemplate).value.getParentFile / "target-2.12"
  )

// class file reader and analyzer
lazy val zincClassfileTemplate = (project in internalPath / "zinc-classfile")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Classfile",
    compilerVersionDependentScalacOptions,
    mimaSettings,
    noSourcesForTemplate,
  )
  .configure(addSbtIO, addSbtUtilLogging)

lazy val zincClassfile210 = zincClassfileTemplate
  .withId("zincClassfile210")
  .dependsOn(compilerInterface210 % "compile;test->test")
  .settings(
    scalaVersion := scala210,
    crossScalaVersions := Seq(scala210),
    target := (target in zincClassfileTemplate).value.getParentFile / "target-2.10"
  )

lazy val zincClassfile211 = zincClassfileTemplate
  .withId("zincClassfile211")
  .dependsOn(compilerInterface211 % "compile;test->test")
  .settings(
    scalaVersion := scala211,
    crossScalaVersions := Seq(scala211),
    target := (target in zincClassfileTemplate).value.getParentFile / "target-2.11"
  )

lazy val zincClassfile212 = zincClassfileTemplate
  .withId("zincClassfile212")
  .dependsOn(compilerInterface212 % "compile;test->test")
  .settings(
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    target := (target in zincClassfileTemplate).value.getParentFile / "target-2.12"
  )

// re-implementation of scripted engine
lazy val zincScripted = (project in internalPath / "zinc-scripted")
  .dependsOn(zinc, zincIvyIntegration % "test->test")
  .settings(
    minimalSettings,
    noPublish,
    name := "zinc Scripted",
  )
  .configure(addSbtUtilScripted)

// re-implementation of scripted engine as a standalone main
lazy val bloopScripted = (project in internalPath / "zinc-scripted-bloop")
  .dependsOn(zinc, zincScripted % "compile->test")
  .settings(
    minimalSettings,
    noPublish,
    name := "zinc Scripted Bloop",
    scalaVersion := scala212,
    crossScalaVersions := List(scala212),
    libraryDependencies ++= List("ch.epfl.scala" %% "bloop-config" % "1.0.0")
  )

lazy val crossTestBridges = {
  Command.command("crossTestBridges") { state =>
    val publishCommands = List(
      s"${compilerBridgeTest210.id}/test",
      s"${compilerBridgeTest211.id}/test",
      s"${compilerBridgeTest212.id}/test",
    )

    publishCommands ::: state
  }
}

lazy val publishBridges = {
  Command.command("publishBridges") { state =>
    val publishCommands = List(
      s"${compilerInterface212.id}/publishLocal",
      s"${compilerBridge210.id}/publishLocal",
      s"${compilerBridge211.id}/publishLocal",
      s"${compilerBridge212.id}/publishLocal",
      s"${compilerBridge213.id}/publishLocal"
    )

    publishCommands ::: state
  }
}

val dir = IO.createTemporaryDirectory
val dirPath = dir.getAbsolutePath
lazy val tearDownBenchmarkResources = taskKey[Unit]("Remove benchmark resources.")
tearDownBenchmarkResources in ThisBuild := { IO.delete(dir) }

addCommandAlias(
  "runBenchmarks",
  s""";zincBenchmarks/run $dirPath
     |;zincBenchmarks/jmh:run -p _tempDir=$dirPath -prof gc
     |;tearDownBenchmarkResources
   """.stripMargin
)

lazy val otherRootSettings = Seq(
  scriptedBufferLog := true,
  scripted := scriptedTask.evaluated,
  Scripted.scriptedPrescripted := { (_: File) => () },
  Scripted.scriptedUnpublished := scriptedUnpublishedTask.evaluated,
  Scripted.scriptedSource := (sourceDirectory in zinc).value / "sbt-test",
  publishAll := {
    val _ = (publishLocal).all(ScopeFilter(inAnyProject)).value
  }
)

def scriptedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
  publishAll.value
  doScripted(
    (fullClasspath in zincScripted in Test).value,
    (scalaInstance in zincScripted).value,
    scriptedSource.value,
    result,
    scriptedBufferLog.value,
    scriptedPrescripted.value
  )
}

def scriptedUnpublishedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
  doScripted(
    (fullClasspath in zincScripted in Test).value,
    (scalaInstance in zincScripted).value,
    scriptedSource.value,
    result,
    scriptedBufferLog.value,
    scriptedPrescripted.value
  )
}

lazy val publishAll = TaskKey[Unit]("publish-all")
lazy val publishLauncher = TaskKey[Unit]("publish-launcher")

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("release") { state =>
    "clean" :: // This is required since version number is generated in properties file.
      "+compile" ::
      "+publishSigned" ::
      "reload" ::
      state
  }
)

inThisBuild(Seq(
  whitesourceProduct                   := "Lightbend Reactive Platform",
  whitesourceAggregateProjectName      := "sbt-zinc-master",
  whitesourceAggregateProjectToken     := "4b57f35176864c6397b872277d51bc27b89503de0f1742b8bc4dfa2e33b95c5c",
  whitesourceIgnoredScopes             += "scalafmt",
  whitesourceFailOnError               := sys.env.contains("WHITESOURCE_PASSWORD"), // fail if pwd is present
  whitesourceForceCheckAllDependencies := true,
))
