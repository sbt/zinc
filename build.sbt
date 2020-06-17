import Util._
import Dependencies._
import localzinc.Scripted, Scripted._
import com.typesafe.tools.mima.core._, ProblemFilters._

def zincRootPath: File = {
  sys.props.get("sbtzinc.path") match {
    case Some(x) => file(x).getCanonicalFile
    case _       => file(".").getCanonicalFile
  }
}

def internalPath = zincRootPath / "internal"

def mimaSettings: Seq[Setting[_]] = Seq(
  mimaPreviousArtifacts := {
    val pre140 = Set(
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
    )
    val post140: Set[String] = Set()
    val versions =
      if (scalaVersion.value.startsWith("2.12.")) pre140 ++ post140
      else post140
    versions map { version =>
      (organization.value %% moduleName.value % version
        cross (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled))
    }
  },
)

ThisBuild / version := {
  val old = (ThisBuild / version).value
  nightlyVersion match {
    case Some(v) => v
    case _ =>
      if ((ThisBuild / isSnapshot).value) "1.4.0-SNAPSHOT"
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
  testFrameworks += new TestFramework("verify.runner.Framework"),
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  publishArtifact in Test := false,
  scalacOptions ++= Seq(
    "-YdisableFlatCpCaching",
    "-target:jvm-1.8",
  )
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

def baseSettings: Seq[Setting[_]] = minimalSettings

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
    compilerInterface.projectRefs ++
      compilerBridge.projectRefs ++
      zincApiInfo.projectRefs ++
      zincBenchmarks.projectRefs ++
      zincClasspath.projectRefs ++
      zincClassfile.projectRefs ++
      zincCompileCore.projectRefs ++
      zincCompile.projectRefs ++
      zincCore.projectRefs ++
      zincPersist.projectRefs ++
      zincTesting.projectRefs ++
      zinc.projectRefs: _*
  )
  .settings(
    minimalSettings,
    otherRootSettings,
    noPublish,
    name := "zinc Root",
    customCommands,
    crossScalaVersions := Nil,
  )

lazy val zinc = (projectMatrix in (zincRootPath / "zinc"))
  .dependsOn(
    zincCore,
    zincPersist,
    zincCompileCore,
    zincClassfile,
    zincTesting % Test
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(sbtbuildinfo.BuildInfoPlugin.buildInfoScopedSettings(Test))
  .settings(
    name := "zinc",
    Test / resourceGenerators ++= Seq(
      (jar1 / genTestResTask).taskValue,
      (jar2 / genTestResTask).taskValue,
      (classesDep1 / genTestResTask).taskValue
    ),
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
      exclude[DirectMissingMethodProblem]("sbt.internal.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.*"),
      exclude[ReversedMissingMethodProblem]("sbt.internal.*"),
      exclude[MissingClassProblem]("sbt.internal.*"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.*"),
    )
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps)

def resGenFile = (zincRootPath / "zinc" / "resGenerator").getAbsoluteFile

lazy val jar1 = (project in resGenFile / "jar1")
  .settings(sampleProjectSettings("jar"))
lazy val jar2 = (project in resGenFile / "jar2")
  .settings(sampleProjectSettings("jar"))
lazy val classesDep1 = (project in resGenFile / "classesDep1")
  .settings(sampleProjectSettings("zip"))

lazy val zinc212 = zinc.jvm(scala212)

lazy val zincTesting = (projectMatrix in internalPath / "zinc-testing")
  .dependsOn(compilerInterface)
  .settings(
    name := "zinc Testing",
    minimalSettings,
    noPublish,
    libraryDependencies ++= Seq(scalaCheck, scalatest, junit, sjsonnewScalaJson.value)
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addSbtIO, addSbtUtilLogging)

lazy val zincCompile = (projectMatrix in zincRootPath / "zinc-compile")
  .dependsOn(zincCompileCore, zincCompileCore % "test->test")
  .settings(
    name := "zinc Compile",
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
        exclude[IncompatibleSignatureProblem]("sbt.inc.Doc*"),
        exclude[IncompatibleResultTypeProblem]("sbt.inc.Doc*"),
        exclude[IncompatibleMethTypeProblem]("sbt.inc.Doc*"),
        exclude[DirectMissingMethodProblem]("sbt.inc.Doc*"),
        exclude[ReversedMissingMethodProblem]("sbt.inc.Doc*"),
      )
    },
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps, addSbtUtilTracking)

// Persists the incremental data structures using Protobuf
lazy val zincPersist = (projectMatrix in internalPath / "zinc-persist")
  .dependsOn(zincCore, zincCompileCore, zincCore % "test->test")
  .settings(
    name := "zinc Persist",
    libraryDependencies += sbinary,
    libraryDependencies ++= (scalaVersion.value match {
      case v if v.startsWith("2.12.") => List(compilerPlugin(silencerPlugin))
      case _                          => List()
    }),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, major)) if major >= 13 =>
          List("org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0")
        case _ =>
          List()
      }
    },
    compileOrder := sbt.CompileOrder.Mixed,
    Compile / scalacOptions ++= (scalaVersion.value match {
      case VersionNumber(Seq(2, 12, _*), _, _) =>
        List("-Ywarn-unused:-imports,-locals,-implicits,-explicits,-privates")
      case _ => Nil
    }),
    PB.targets in Compile := List(scalapb.gen() -> (sourceManaged in Compile).value),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    mimaSettings,
    mimaBinaryIssueFilters ++= Util.excludeInternalProblems,
    mimaBinaryIssueFilters ++= Seq(
      exclude[IncompatibleMethTypeProblem]("xsbti.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.*"),
      exclude[IncompatibleTemplateDefProblem]("sbt.internal.inc.schema.*"),
    ),
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps)

// Implements the core functionality of detecting and propagating changes incrementally.
//   Defines the data structures for representing file fingerprints and relationships and the overall source analysis
lazy val zincCore = (projectMatrix in internalPath / "zinc-core")
  .dependsOn(
    zincCompileCore,
    zincApiInfo,
    zincClasspath,
    compilerInterface,
    compilerBridge % Test,
    zincTesting % Test
  )
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
    mimaBinaryIssueFilters ++= Util.excludeInternalProblems,
    mimaBinaryIssueFilters ++= Seq(
      exclude[IncompatibleMethTypeProblem]("xsbti.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.*"),
    ),
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps, addSbtIO, addSbtUtilLogging, addSbtUtilRelation)

lazy val zincBenchmarks = (projectMatrix in internalPath / "zinc-benchmarks")
  .dependsOn(compilerInterface % "compile->compile;compile->test")
  .dependsOn(compilerBridge, zinc % "compile->test", zincTesting % "compile->test")
  .enablePlugins(JmhPlugin)
  .settings(
    noPublish,
    name := "Benchmarks of Zinc and the compiler bridge",
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit" % "5.4.2.201908231537-r",
      "net.openhft" % "affinity" % "3.1.11"
    ),
    javaOptions in Test ++= List("-Xmx600M", "-Xms600M"),
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))

// sbt-side interface to compiler.  Calls compiler-side interface reflectively
lazy val zincCompileCore = (projectMatrix in internalPath / "zinc-compile-core")
  .enablePlugins(ContrabandPlugin)
  .dependsOn(
    compilerInterface,
    zincClasspath,
    zincApiInfo,
    zincClassfile,
    zincTesting % Test
  )
  .settings(
    name := "zinc Compile Core",
    libraryDependencies ++= Seq(
      scalaCompiler.value % Test,
      launcherInterface,
      parserCombinator,
      zeroAllocationHashing
    ),
    unmanagedJars in Test := Seq(packageSrc in compilerBridge212 in Compile value).classpath,
    managedSourceDirectories in Compile += (Compile / generateContrabands / sourceManaged).value,
    Compile / generateContrabands / sourceManaged := (internalPath / "zinc-compile-core" / "src" / "main" / "contraband-java").getAbsoluteFile,
    mimaSettings,
    mimaBinaryIssueFilters ++= Util.excludeInternalProblems,
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps, addSbtUtilLogging, addSbtIO, addSbtUtilControl)

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the sbt-contraband plugin.
lazy val compilerInterface = (projectMatrix in internalPath / "compiler-interface")
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
    Compile / generateContrabands / sourceManaged :=
      (internalPath / "compiler-interface" / "src" / "main" / "contraband-java").getAbsoluteFile,
    managedSourceDirectories in Compile += (Compile / generateContrabands / sourceManaged).value,
    crossPaths := false,
    autoScalaLibrary := false,
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
        // 1.4.0 changed to VirtualFile. These should be internal to Zinc.
        exclude[Problem]("xsbti.AnalysisCallback.*"),
        exclude[Problem]("xsbti.compile.PerClasspathEntryLookup.*"),
        exclude[Problem]("xsbti.compile.ExternalHooks*"),
        exclude[Problem]("xsbti.compile.FileHash.*"),
        exclude[Problem]("xsbti.compile.Output.*"),
        exclude[Problem]("xsbti.compile.OutputGroup.*"),
        exclude[Problem]("xsbti.compile.SingleOutput.*"),
        exclude[Problem]("xsbti.compile.MultipleOutput.*"),
        exclude[Problem]("xsbti.compile.CachedCompiler.*"),
        exclude[Problem]("xsbti.compile.ClassFileManager.*"),
        exclude[Problem]("xsbti.compile.WrappedClassFileManager.*"),
        exclude[Problem]("xsbti.compile.DependencyChanges.*"),
        exclude[Problem]("xsbti.compile.ScalaCompiler.*"),
        exclude[Problem]("xsbti.compile.JavaTool.*"),
        exclude[Problem]("xsbti.compile.JavaTool.*"),
        exclude[Problem]("xsbti.compile.analysis.ReadSourceInfos.*"),
        exclude[Problem]("xsbti.compile.analysis.ReadStamps.*"),
        // This is a breaking change
        exclude[Problem]("xsbti.compile.CompileOptions.*"),
        // new API points
        exclude[DirectMissingMethodProblem]("xsbti.compile.IncrementalCompiler.compile"),
        exclude[ReversedMissingMethodProblem]("xsbti.compile.IncrementalCompiler.compile"),
        exclude[ReversedMissingMethodProblem]("xsbti.compile.ScalaInstance.loaderLibraryOnly"),
        exclude[ReversedMissingMethodProblem]("xsbti.compile.ScalaInstance.libraryJars"),
      )
    },
  )
  .jvmPlatform(autoScalaLibrary = false)
  .configure(addSbtUtilInterface)

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
lazy val compilerBridge = (projectMatrix in internalPath / "compiler-bridge")
  .dependsOn(compilerInterface)
  .settings(
    autoScalaLibrary := false,
    // precompiledSettings,
    name := "Compiler Bridge",
    baseSettings,
    compilerVersionDependentScalacOptions,
    // We need this for import Compat._
    Compile / scalacOptions --= Seq("-Ywarn-unused-import", "-Xfatal-warnings"),
    Compile / scalacOptions ++= (scalaVersion.value match {
      case VersionNumber(Seq(2, 12, _*), _, _) =>
        List("-Ywarn-unused:-imports,-locals,-implicits,-explicits,-privates")
      case _ => Nil
    }),
    libraryDependencies += scalaCompiler.value % "provided",
    exportJars := true,
    inBoth(unmanagedSourceDirectories ++= scalaPartialVersion.value.collect {
      case (2, y) if y == 10            => new File(scalaSource.value.getPath + "_2.10")
      case (2, y) if y == 11 || y == 12 => new File(scalaSource.value.getPath + "_2.11-12")
      case (2, y) if y >= 13            => new File(scalaSource.value.getPath + "_2.13")
    }.toList),
  )
  .jvmPlatform(scalaVersions = Seq(scala210, scala211, scala212, scala213))

lazy val compilerBridge210 = compilerBridge.jvm(scala210)
lazy val compilerBridge211 = compilerBridge.jvm(scala211)
lazy val compilerBridge212 = compilerBridge.jvm(scala212)
lazy val compilerBridge213 = compilerBridge.jvm(scala213)

/**
 * Tests for the compiler bridge.
 * This is split into a separate subproject because testing introduces more dependencies
 * (Zinc API Info, which transitively depends on IO).
 */
lazy val compilerBridgeTest = (project in internalPath / "compiler-bridge-test")
  .dependsOn(
    zinc.jvm(scala212) % "compile->compile;test->test",
    compilerInterface.jvm(false)
  )
  .settings(
    name := "Compiler Bridge Test",
    baseSettings,
    scalaVersion := scala212,
    compilerVersionDependentScalacOptions,
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    Test / fork := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    Test / javaOptions += {
      s"-Dzinc.build.compilerbridge.scalaVersion=${scalaVersion.value}"
    },
    publish / skip := true,
  )

// lazy val compilerBridgeTest210 = compilerBridgeTest.jvm(scala210)
// lazy val compilerBridgeTest211 = compilerBridgeTest.jvm(scala211)
// lazy val compilerBridgeTest212 = compilerBridgeTest.jvm(scala212)
// lazy val compilerBridgeTest213 = compilerBridgeTest.jvm(scala213)

val scalaPartialVersion = Def setting (CrossVersion partialVersion scalaVersion.value)

def inBoth(ss: Setting[_]*): Seq[Setting[_]] = Seq(Compile, Test) flatMap (inConfig(_)(ss))

// defines operations on the API of a source, including determining whether it has changed and converting it to a string
//   and discovery of Projclasses and annotations
lazy val zincApiInfo = (projectMatrix in internalPath / "zinc-apiinfo")
  .dependsOn(compilerInterface, compilerBridge, zincClassfile % "compile;test->test")
  .settings(
    name := "zinc ApiInfo",
    compilerVersionDependentScalacOptions,
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
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps)

// Utilities related to reflection, managing Scala versions, and custom class loaders
lazy val zincClasspath = (projectMatrix in internalPath / "zinc-classpath")
  .dependsOn(compilerInterface)
  .settings(
    name := "zinc Classpath",
    compilerVersionDependentScalacOptions,
    libraryDependencies ++= Seq(scalaCompiler.value, launcherInterface),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // private[sbt]
      exclude[DirectMissingMethodProblem](
        "sbt.internal.inc.classpath.ClasspathUtilities.printSource"
      ),
      exclude[DirectMissingMethodProblem](
        "sbt.internal.inc.classpath.ClasspathUtilities.compilerPlugins"
      ),
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.classpath.ClasspathUtilities.asFile"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.inc.classpath.ClasspathFilter.this"),
      exclude[IncompatibleResultTypeProblem](
        "sbt.internal.inc.classpath.NativeCopyConfig.*"
      ),
      exclude[IncompatibleSignatureProblem](
        "sbt.internal.inc.classpath.NativeCopyConfig.*"
      ),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.inc.classpath.NativeCopyConfig.*"),
    ),
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps, addSbtIO)

// class file reader and analyzer
lazy val zincClassfile = (projectMatrix in internalPath / "zinc-classfile")
  .dependsOn(compilerInterface, zincTesting % Test)
  .settings(
    name := "zinc Classfile",
    compilerVersionDependentScalacOptions,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem]("sbt.internal.inc.classfile.Analyze.apply"),
      // changes stemming from Scala 2.13 Seq changes
      exclude[IncompatibleResultTypeProblem]("sbt.internal.inc.IndexBasedZipFsOps.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.inc.IndexBasedZipFsOps.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.inc.CreateZip.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.inc.IndexBasedZipOps.*"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.inc.IndexBasedZipOps.*"),
      exclude[ReversedMissingMethodProblem]("sbt.internal.inc.IndexBasedZipOps.*"),
    ),
    mimaBinaryIssueFilters ++= Util.excludeInternalProblems,
  )
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps, addSbtIO, addSbtUtilLogging)

// re-implementation of scripted engine
lazy val zincScripted = (projectMatrix in internalPath / "zinc-scripted")
  .dependsOn(zinc % "compile;test->test")
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
    buildInfoKeys in Test := Seq[BuildInfoKey](zinc212 / sourceDirectory),
    conflictWarning := ConflictWarning.disable,
  )
  .jvmPlatform(scalaVersions = List(scala212))
  .configure({ p =>
    p.dependsOn(compilerBridge210, compilerBridge211, compilerBridge212, compilerBridge213)
  }, addSbtUtilScripted)

lazy val zincScripted212 = zincScripted.jvm(scala212)

def isJava8: Boolean = sys.props("java.specification.version") == "1.8"
def bridges =
  if (isJava8) List(compilerBridge210 / publishLocal, compilerBridge211 / publishLocal)
  else
    List(
      compilerBridge210 / publishLocal,
      compilerBridge211 / publishLocal,
      compilerBridge212 / publishLocal,
      compilerBridge213 / publishLocal,
    )

val publishBridges = taskKey[Unit]("")
val crossTestBridges = taskKey[Unit]("")

publishBridges := Def.task(()).dependsOn(bridges: _*).value
crossTestBridges := (compilerBridgeTest / Test / test).dependsOn(publishBridges).value

val dir = IO.createTemporaryDirectory
val dirPath = dir.getAbsolutePath
lazy val tearDownBenchmarkResources = taskKey[Unit]("Remove benchmark resources.")
tearDownBenchmarkResources in ThisBuild := { IO.delete(dir) }

addCommandAlias(
  "runBenchmarks",
  s""";${compilerBridge213.id}/packageBin;${compilerBridge212.id}/packageBin;zincBenchmarksJVM2_12/run $dirPath;zincBenchmarksJVM2_12/jmh:run -p _tempDir=$dirPath -prof gc -foe true;tearDownBenchmarkResources""".stripMargin
)

lazy val otherRootSettings = Seq(
  mimaPreviousArtifacts := Set.empty,
  scriptedBufferLog := true,
  scripted := scriptedTask.evaluated,
  Scripted.scriptedSource := (zinc212 / sourceDirectory).value / "sbt-test",
  Scripted.scriptedCompileToJar := false,
)

def scriptedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
  doScripted(
    (zincScripted212 / Test / fullClasspath).value,
    (zincScripted212 / scalaInstance).value,
    scriptedSource.value,
    result,
    scriptedBufferLog.value,
    scriptedCompileToJar.value,
  )
}

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("release") { state =>
    "clean" :: // This is required since version number is generated in properties file.
      "+compile" ::
      "+publishSigned" ::
      "reload" ::
      state
  }
)
ThisBuild / publishTo := {
  val old = (ThisBuild / publishTo).value
  sys.props.get("sbt.build.localmaven") match {
    case Some(path) => Some(MavenCache("local-maven", file(path)))
    case _          => old
  }
}

ThisBuild / whitesourceProduct := "Lightbend Reactive Platform"
ThisBuild / whitesourceAggregateProjectName := "sbt-zinc-master"
ThisBuild / whitesourceAggregateProjectToken := "4b57f35176864c6397b872277d51bc27b89503de0f1742b8bc4dfa2e33b95c5c"
ThisBuild / whitesourceIgnoredScopes += "scalafmt"
ThisBuild / whitesourceFailOnError := sys.env.contains("WHITESOURCE_PASSWORD") // fail if pwd is present
ThisBuild / whitesourceForceCheckAllDependencies := true
