import Util._
import Dependencies._
import localzinc.Scripted, Scripted._
import com.typesafe.tools.mima.core._, ProblemFilters._

def internalPath = file("internal")

def mimaSettings: Seq[Setting[_]] = Seq(
  mimaPreviousArtifacts := Set(
    "1.0.0",
    "1.0.1",
    "1.0.2",
    "1.0.3",
    "1.0.4",
    "1.0.5",
    "1.1.0",
    "1.1.1",
    "1.1.2",
    "1.1.3",
    "1.2.0",
    "1.2.1",
    "1.2.2",
    "1.3.0",
  ) map (
      version =>
        organization.value %% moduleName.value % version
          cross (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
    ),
)

ThisBuild / version := {
  val old = (ThisBuild / version).value
  nightlyVersion match {
    case Some(v) => v
    case _ =>
      if ((ThisBuild / isSnapshot).value) "1.3.0-SNAPSHOT"
      else old
  }
}
ThisBuild / organization := "org.scala-sbt"
ThisBuild / licenses := List(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")))
ThisBuild / scalafmtOnCompile := !(Global / insideCI).value
ThisBuild / Test / scalafmtOnCompile := !(Global / insideCI).value
ThisBuild / bintrayPackage := "zinc"
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/sbt/zinc"), "git@github.com:sbt/zinc.git")
)
ThisBuild / description := "Incremental compiler of Scala"
ThisBuild / homepage := Some(url("https://github.com/sbt/zinc"))
ThisBuild / developers := List(
  Developer("harrah", "Mark Harrah", "@harrah", url("https://github.com/harrah")),
  Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("http://eed3si9n.com/")),
  Developer("dwijnand", "Dale Wijnand", "@dwijnand", url("https://github.com/dwijnand")),
  Developer(
    "gkossakowski",
    "Grzegorz Kossakowski",
    "@gkossakowski",
    url("https://github.com/gkossakowski")
  ),
  Developer("jvican", "Jorge Vicente Cantero", "@jvican", url("https://github.com/jvican")),
  Developer("Duhemm", "Martin Duhem", "@Duhemm", url("https://github.com/Duhemm")),
)

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ =>
  false
}
ThisBuild / mimaPreviousArtifacts := Set.empty

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := scala212,
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += "bintray-sbt-maven-releases" at "https://dl.bintray.com/sbt/maven-releases/",
  resolvers += Resolver.url(
    "bintray-sbt-ivy-snapshots",
    new URL("https://dl.bintray.com/sbt/ivy-snapshots/")
  )(Resolver.ivyStylePatterns),
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1", "-verbosity", "2"),
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  crossScalaVersions := Seq(scala212),
  publishArtifact in Test := false,
  commands ++= Seq(crossTestBridges),
  scalacOptions ++= Seq(
    "-YdisableFlatCpCaching",
    "-target:jvm-1.8",
  ),
  // Override the version that scalapb depends on. This adds an explicit dependency on
  // protobuf-java. This will cause sbt to evict the older version that is used by
  // scalapb-runtime.
  libraryDependencies += "com.google.protobuf" % "protobuf-java" % "3.7.0"
)

def compilerVersionDependentScalacOptions: Seq[Setting[_]] = Seq(
  scalacOptions := {
    val old = scalacOptions.value
    scalaBinaryVersion.value match {
      case "2.12" =>
        old ++ List(
          "-opt-inline-from:<sources>",
          "-opt:l:inline",
          "-Yopt-inline-heuristics:at-inline-annotated"
        )
      case _ =>
        old filterNot Set(
          "-Xfatal-warnings",
          "-deprecation",
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
    zincCompile,
    zincCompileCore,
    compilerInterface212,
    compilerBridge210,
    compilerBridge211,
    compilerBridge212,
    compilerBridge213,
    zincApiInfo212,
    zincClasspath212,
    zincClassfile212,
  )
  .settings(
    minimalSettings,
    otherRootSettings,
    noPublish,
    name := "zinc Root",
    customCommands,
    crossScalaVersions := Nil,
  )

lazy val zinc = (project in file("zinc"))
  .dependsOn(
    zincCore,
    zincPersist,
    zincCompileCore,
    zincClassfile212,
    zincTesting % Test
  )
  .configure(addBaseSettingsAndTestDeps)
  .enablePlugins(BuildInfoPlugin)
  .settings(sbtbuildinfo.BuildInfoPlugin.buildInfoScopedSettings(Test))
  .settings(
    name := "zinc",
    buildInfo in Compile := Nil, // Only generate build info for tests
    buildInfoPackage in Test := "sbt.internal.inc",
    buildInfoObject in Test := "ZincBuildInfo",
    buildInfoKeys in Test := {
      val bridgeKeys = List[BuildInfoKey](
        BuildInfoKey.map(scalaVersion in compilerBridge210) {
          case (_, v) => "scalaVersion210" -> v
        },
        BuildInfoKey.map(scalaInstance in compilerBridge210) {
          case (k, v) => "scalaJars210" -> v.allJars.toList
        },
        BuildInfoKey.map(classDirectory in Compile in compilerBridge210) {
          case (k, v) => "classDirectory210" -> v
        },
        BuildInfoKey.map(scalaVersion in compilerBridge211) {
          case (_, v) => "scalaVersion211" -> v
        },
        BuildInfoKey.map(scalaInstance in compilerBridge211) {
          case (k, v) => "scalaJars211" -> v.allJars.toList
        },
        BuildInfoKey.map(classDirectory in Compile in compilerBridge211) {
          case (k, v) => "classDirectory211" -> v
        },
        BuildInfoKey.map(scalaVersion in compilerBridge212) {
          case (_, v) => "scalaVersion212" -> v
        },
        BuildInfoKey.map(scalaInstance in compilerBridge212) {
          case (k, v) => "scalaJars212" -> v.allJars.toList
        },
        BuildInfoKey.map(classDirectory in Compile in compilerBridge212) {
          case (k, v) => "classDirectory212" -> v
        },
        BuildInfoKey.map(scalaVersion in compilerBridge213) {
          case (_, v) => "scalaVersion213" -> v
        },
        BuildInfoKey.map(scalaInstance in compilerBridge213) {
          case (k, v) => "scalaJars213" -> v.allJars.toList
        },
        BuildInfoKey.map(classDirectory in Compile in compilerBridge213) {
          case (k, v) => "classDirectory213" -> v
        },
      )
      bridgeKeys
    },
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem](
        "sbt.internal.inc.IncrementalCompilerImpl.compileIncrementally"
      ),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCompilerImpl.inputs"),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCompilerImpl.compile"),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.MixedAnalyzingCompiler.config"),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.MixedAnalyzingCompiler.makeConfig"),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.MixedAnalyzingCompiler.this"),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.CompileConfiguration.this"),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.ZincUtil.getDefaultBridgeModule"),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.ZincUtil.scalaCompiler"),
    )
  )

lazy val zincTesting = (project in internalPath / "zinc-testing")
  .dependsOn(compilerInterface212)
  .settings(
    minimalSettings,
    noPublish,
    name := "zinc Testing",
    libraryDependencies ++= Seq(scalaCheck, scalatest, junit, sjsonnewScalaJson.value)
  )
  .configure(addSbtIO, addSbtUtilLogging)

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
    libraryDependencies ++= (scalaVersion.value match {
      case v if v.startsWith("2.12.") => List(compilerPlugin(silencerPlugin))
      case _                          => List()
    }),
    compileOrder := sbt.CompileOrder.Mixed,
    Compile / scalacOptions ++= (scalaVersion.value match {
      case VersionNumber(Seq(2, 12, _*), _, _) =>
        List("-Ywarn-unused:-imports,-locals,-implicits,-explicits,-privates")
      case _ => Nil
    }),
    PB.targets in Compile := List(scalapb.gen() -> (sourceManaged in Compile).value),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.BinaryAnalysisFormat.writeAPIs"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.BinaryAnalysisFormat.readAPIs"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.converters.ProtobufWriters.toApisFile"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.converters.ProtobufWriters.toApis"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.converters.ProtobufWriters.toAnalyzedClass"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.converters.ProtobufReaders.fromApis"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.converters.ProtobufReaders.fromApisFile"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.converters.ProtobufReaders.fromAnalyzedClass"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.AnalyzedClass.apply"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.AnalyzedClass.copy"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.AnalyzedClass.this"),
        exclude[ReversedMissingMethodProblem]("sbt.internal.inc.schema.Version.isV11"),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.binary.converters.ProtobufReaders.this"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.Problem.*"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.Problem#ProblemLens.rendered"),
        exclude[MissingClassProblem]("sbt.internal.inc.text.Java678Encoder"),
        // Added {start,end}{Offset,Line,Column}
        // Added Position#{start,end}{Offset,Line,Column}
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.Position.apply"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.Position.copy"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.Position.this"),
        // Added Problem#reported
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.Problem.apply"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.Problem.copy"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.schema.Problem.this"),
        exclude[IncompatibleSignatureProblem]("sbt.internal.inc.schema.Problem.unapply"),
        exclude[IncompatibleSignatureProblem]("sbt.internal.inc.schema.Position.unapply"),
        exclude[IncompatibleSignatureProblem]("sbt.internal.inc.schema.AnalyzedClass.unapply"),
        exclude[IncompatibleSignatureProblem]("sbt.internal.inc.schema.AnalyzedClass.unapply"),
        exclude[IncompatibleSignatureProblem]("sbt.internal.inc.schema.Version.values"),
        exclude[IncompatibleSignatureProblem]("sbt.internal.inc.schema.Position.unapply"),
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
    PB.targets in Compile := List(scalapb.gen() -> (sourceManaged in Compile).value),
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      List(
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalNameHashing.allDeps"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalNameHashing.sameAPI"),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalNameHashing.invalidateClass"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalNameHashing.invalidateByExternal"
        ),
        exclude[DirectAbstractMethodProblem](
          "sbt.internal.inc.IncrementalCommon.invalidatedPackageObjects"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalNameHashing.this"),
        exclude[MissingClassProblem]("sbt.internal.inc.ClassToSourceMapper"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.Incremental.compile"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.Incremental.prune"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.changes"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.sameClass"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.allDeps"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.sameAPI"),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.invalidateIntermediate"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.invalidateByAllExternal"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.invalidateDuplicates"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.transitiveDeps"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.invalidateClass"),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.externalBinaryModified"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.invalidateIncremental"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.changedInitial"),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.transitiveDeps$default$2"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.orTrue"),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.invalidateByExternal"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.wrappedLog"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.shortcutSameClass"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.orEmpty"),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.changedIncremental"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.currentExternalAPI"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.this"),
        exclude[ReversedMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.findClassDependencies"
        ),
        exclude[ReversedMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.invalidateClassesInternally"
        ),
        exclude[ReversedMissingMethodProblem](
          "sbt.internal.inc.IncrementalCommon.invalidateClassesExternally"
        ),
        exclude[ReversedMissingMethodProblem]("sbt.internal.inc.IncrementalCommon.findAPIChange"),
        exclude[IncompatibleMethTypeProblem]("sbt.internal.inc.Incremental.prune"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.IncrementalCompile.apply"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.AnalysisCallback#Builder.this"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.AnalysisCallback.this"),
        exclude[IncompatibleSignatureProblem]("sbt.internal.inc.MiniSetupUtil.equivCompileSetup"),
      )
    }
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
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.javac.DiagnosticsReporter#PositionImpl.this"
        ),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.javac.JavaProblem.rendered"),
        // Renamed vals in a private[sbt] class
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.javac.DiagnosticsReporter#PositionImpl.endPosition"
        ),
        exclude[DirectMissingMethodProblem](
          "sbt.internal.inc.javac.DiagnosticsReporter#PositionImpl.startPosition"
        ),
        exclude[IncompatibleMethTypeProblem](
          "sbt.internal.inc.javac.DiagnosticsReporter#PositionImpl.this"
        ),
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
        exclude[ReversedMissingMethodProblem]("xsbti.compile.ScalaInstance.libraryJars"), // Added in 1.3.0
        exclude[DirectMissingMethodProblem]("xsbti.api.AnalyzedClass.of"),
        exclude[DirectMissingMethodProblem]("xsbti.api.AnalyzedClass.create"),
        exclude[ReversedMissingMethodProblem]("xsbti.AnalysisCallback.classesInOutputJar"),
        exclude[ReversedMissingMethodProblem]("xsbti.compile.IncrementalCompiler.compile"),
        exclude[DirectMissingMethodProblem]("xsbti.compile.IncrementalCompiler.compile")
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
    // We need this for import Compat._
    Compile / scalacOptions --= Seq("-Ywarn-unused-import", "-Xfatal-warnings"),
    Compile / scalacOptions ++= (scalaVersion.value match {
      case VersionNumber(Seq(2, 12, _*), _, _) =>
        List("-Ywarn-unused:-imports,-locals,-implicits,-explicits,-privates")
      case _ => Nil
    }),
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
        (baseDirectory in ThisBuild).value / "internal" / "compiler-bridge-test" / "target" / "zinc-components",
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
  .dependsOn(zinc % "compile->compile;test->test", compilerInterface212 % "test->test")
  .settings(
    name := "Compiler Bridge Test",
    baseSettings,
    compilerVersionDependentScalacOptions,
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    Test / fork := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    skip in publish := true,
  )

lazy val compilerBridgeTest210 = compilerBridgeTestTemplate
  .withId("compilerBridgeTest210")
  .settings(
    Test / javaOptions += s"-Dzinc.build.compilerbridge.scalaVersion=${scala210}",
    Test / parallelExecution := false,
    target := (target in compilerBridgeTestTemplate).value.getParentFile / "target-2.10"
  )

lazy val compilerBridgeTest211 = compilerBridgeTestTemplate
  .withId("compilerBridgeTest211")
  .settings(
    Test / javaOptions += s"-Dzinc.build.compilerbridge.scalaVersion=${scala211}",
    target := (target in compilerBridgeTestTemplate).value.getParentFile / "target-2.11"
  )

lazy val compilerBridgeTest212 = compilerBridgeTestTemplate
  .withId("compilerBridgeTest212")
  .settings(
    Test / javaOptions += s"-Dzinc.build.compilerbridge.scalaVersion=${scala212}",
    target := (target in compilerBridgeTestTemplate).value.getParentFile / "target-2.12"
  )

lazy val compilerBridgeTest213 = compilerBridgeTestTemplate
  .withId("compilerBridgeTest213")
  .settings(
    Test / javaOptions += s"-Dzinc.build.compilerbridge.scalaVersion=${scala213}",
    target := (target in compilerBridgeTestTemplate).value.getParentFile / "target-2.13"
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
        exclude[DirectMissingMethodProblem]("xsbt.api.HashAPI.this"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.ClassToAPI.handleMalformedNameOf*"),
      )
    }
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
      exclude[DirectMissingMethodProblem](
        "sbt.internal.inc.classpath.ClasspathUtilities.compilerPlugins"
      ),
    ),
  )
  .configure(addSbtIO)

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

lazy val zincClassfile212 = zincClassfileTemplate
  .withId("zincClassfile212")
  .dependsOn(compilerInterface212 % "compile;test->test")
  .settings(
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    target := (target in zincClassfileTemplate).value.getParentFile / "target-2.12",
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.classfile.Analyze.apply")
    )
  )

// re-implementation of scripted engine
lazy val zincScripted = (project in internalPath / "zinc-scripted")
  .dependsOn(zinc % "compile;test->test")
  .dependsOn(compilerBridge210, compilerBridge211, compilerBridge212, compilerBridge213)
  .enablePlugins(BuildInfoPlugin)
  .settings(sbtbuildinfo.BuildInfoPlugin.buildInfoScopedSettings(Test))
  .settings(
    minimalSettings,
    noPublish,
    name := "zinc Scripted",
    libraryDependencies ++= log4jDependencies,
    // Only generate build info for tests
    buildInfo in Compile := Nil,
    buildInfoPackage in Test := "sbt.internal.inc",
    buildInfoKeys in Test := {
      Seq[BuildInfoKey](
        sourceDirectory in zinc,
        classDirectory in Test,
        BuildInfoKey.map(dependencyClasspath in Test) {
          case (_, v) => "classpath" -> v.seq.map(_.data)
        }
      )
    },
    conflictWarning := ConflictWarning.disable,
  )
  .configure(addSbtUtilScripted)

def isJava8: Boolean = sys.props("java.specification.version") == "1.8"

lazy val crossTestBridges = {
  Command.command("crossTestBridges") { state =>
    val java8Only =
      if (isJava8)
        List(
          s"${compilerBridge210.id}/publishLocal",
          s"${compilerBridgeTest210.id}/test",
          s"${compilerBridge211.id}/publishLocal",
          s"${compilerBridgeTest211.id}/test",
        )
      else Nil
    val testCommands =
      java8Only :::
        List(
          s"${compilerBridge212.id}/publishLocal",
          s"${compilerBridgeTest212.id}/test",
          s"${compilerBridge213.id}/publishLocal",
          s"${compilerBridgeTest213.id}/test",
        )

    testCommands ::: state
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
  mimaPreviousArtifacts := Set.empty,
  scriptedBufferLog := true,
  scripted := scriptedTask.evaluated,
  Scripted.scriptedPrescripted := { (_: File) =>
    ()
  },
  Scripted.scriptedUnpublished := scriptedUnpublishedTask.evaluated,
  Scripted.scriptedSource := (sourceDirectory in zinc).value / "sbt-test",
  Scripted.scriptedCompileToJar := false,
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
    scriptedCompileToJar.value,
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
    scriptedCompileToJar.value,
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

ThisBuild / whitesourceProduct := "Lightbend Reactive Platform"
ThisBuild / whitesourceAggregateProjectName := "sbt-zinc-master"
ThisBuild / whitesourceAggregateProjectToken := "4b57f35176864c6397b872277d51bc27b89503de0f1742b8bc4dfa2e33b95c5c"
ThisBuild / whitesourceIgnoredScopes += "scalafmt"
ThisBuild / whitesourceFailOnError := sys.env.contains("WHITESOURCE_PASSWORD") // fail if pwd is present
ThisBuild / whitesourceForceCheckAllDependencies := true
