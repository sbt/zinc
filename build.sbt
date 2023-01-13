import Util._
import Dependencies._
import localzinc.Scripted, Scripted._
import com.typesafe.tools.mima.core._, ProblemFilters._
import com.github.os72.protocjar.Protoc

def zincRootPath: File = file(sys.props.getOrElse("sbtzinc.path", ".")).getCanonicalFile
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
    val post140: Set[String] = Set(
      "1.4.0",
      "1.5.0",
      "1.6.0",
      "1.7.0",
    )
    val versions =
      if (scalaVersion.value.startsWith("2.12.")) pre140 ++ post140
      else post140
    val cross = if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled
    versions.map(version => organization.value %% moduleName.value % version cross cross)
  },
)

ThisBuild / version := {
  val old = (ThisBuild / version).value
  nightlyVersion match {
    case Some(v) => v
    case _ =>
      if ((ThisBuild / isSnapshot).value) "1.7.0-SNAPSHOT"
      else old
  }
}
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "org.scala-sbt"
ThisBuild / licenses := List(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")))
ThisBuild / scalafmtOnCompile := !(Global / insideCI).value
ThisBuild / Test / scalafmtOnCompile := !(Global / insideCI).value
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
ThisBuild / pomIncludeRepository := (_ => false) // drop repos other than Maven Central from POM
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / mimaPreviousArtifacts := Set.empty
// limit the number of concurrent test so testQuick works
Global / concurrentRestrictions += Tags.limit(Tags.Test, 4)
// Global / semanticdbVersion := "4.5.9"
ThisBuild / Test / fork := true
Global / excludeLintKeys += ideSkipProject

def baseSettings: Seq[Setting[_]] = Seq(
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1", "-verbosity", "2"),
  testFrameworks += new TestFramework("verify.runner.Framework"),
  compile / javacOptions ++= Seq("-Xlint", "-Xlint:-serial"),
  Test / publishArtifact := false,
  scalacOptions ++= Seq("-YdisableFlatCpCaching", "-target:jvm-1.8"),
  semanticdbCompilerPlugin := {
    ("org.scalameta" % "semanticdb-scalac" % semanticdbVersion.value)
      .cross(CrossVersion.full)
  },
  ideSkipProject := scalaVersion.value != defaultScalaVersion,
)

def compilerVersionDependentScalacOptions: Seq[Setting[_]] = Seq(
  scalacOptions := {
    scalaBinaryVersion.value match {
      case "2.12" =>
        scalacOptions.value ++ List(
          "-opt-inline-from:<sources>",
          "-opt:l:inline",
          "-Yopt-inline-heuristics:at-inline-annotated"
        )
      case _ =>
        scalacOptions.value.filterNot(
          Set(
            "-Xfatal-warnings",
            "-deprecation",
            "-YdisableFlatCpCaching",
          )
        )
    }
  }
)

def addBaseSettingsAndTestDeps(p: Project): Project =
  p.settings(baseSettings).configure(addTestDependencies)

// zincRoot is now only 2.12 (2.11.x is not supported anymore)
lazy val aggregated: Seq[ProjectReference] = compilerInterface.projectRefs ++
  compilerBridge.projectRefs ++
  zincApiInfo.projectRefs ++
  zincBenchmarks.projectRefs ++
  zincClasspath.projectRefs ++
  zincClassfile.projectRefs ++
  zincCompileCore.projectRefs ++
  zincCompile.projectRefs ++
  zincCore.projectRefs ++
  zincPersist.projectRefs ++
  Seq(zincPersistCore: ProjectReference) ++
  zincPersistCoreAssembly.projectRefs ++
  zincTesting.projectRefs ++
  zinc.projectRefs

lazy val zincRoot: Project = (project in file("."))
  .aggregate(aggregated: _*)
  .settings(
    baseSettings,
    name := "zinc Root",
    mimaPreviousArtifacts := Set.empty,
    scriptedBufferLog := true,
    scripted := scriptedTask.evaluated,
    scripted / watchTriggers += baseDirectory.value.toGlob / "zinc" / "src" / "sbt-test" / **,
    Scripted.scriptedSource := (zinc212 / sourceDirectory).value / "sbt-test",
    Scripted.scriptedCompileToJar := false,
    publish / skip := true,
    commands += Command.command("release") { state =>
      "clean" :: "+compile" :: "+publishSigned" :: "reload" :: state
    }, // clean is required b/c the version is generated in properties file
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
  .settings(
    name := "zinc",
    Test / resourceGenerators ++= Seq(
      (jar1 / genTestResTask).taskValue,
      (jar2 / genTestResTask).taskValue,
      (classesDep1 / genTestResTask).taskValue
    ),
    Compile / buildInfo := Nil, // Only generate build info for tests
    BuildInfoPlugin.buildInfoScopedSettings(Test),
    Test / buildInfoPackage := "sbt.internal.inc",
    Test / buildInfoObject := "ZincBuildInfo",
    Test / buildInfoKeys := List[BuildInfoKey](
      BuildInfoKey.map(compilerBridge210 / scalaVersion)("scalaVersion210" -> _._2),
      BuildInfoKey.map(compilerBridge210 / scalaInstance)("scalaJars210" -> _._2.allJars.toList),
      BuildInfoKey.map(compilerBridge210 / Compile / classDirectory)("classDirectory210" -> _._2),
      BuildInfoKey.map(compilerBridge211 / scalaVersion)("scalaVersion211" -> _._2),
      BuildInfoKey.map(compilerBridge211 / scalaInstance)("scalaJars211" -> _._2.allJars.toList),
      BuildInfoKey.map(compilerBridge211 / Compile / classDirectory)("classDirectory211" -> _._2),
      BuildInfoKey.map(compilerBridge212 / scalaVersion)("scalaVersion212" -> _._2),
      BuildInfoKey.map(compilerBridge212 / scalaInstance)("scalaJars212" -> _._2.allJars.toList),
      BuildInfoKey.map(compilerBridge212 / Compile / classDirectory)("classDirectory212" -> _._2),
      BuildInfoKey.map(compilerBridge213 / scalaVersion)("scalaVersion213" -> _._2),
      BuildInfoKey.map(compilerBridge213 / scalaInstance)("scalaJars213" -> _._2.allJars.toList),
      BuildInfoKey.map(compilerBridge213 / Compile / classDirectory)("classDirectory213" -> _._2),
    ),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    // so we have full access to com.sun.tools.javac on JDK 17
    Test / javaOptions ++= (
      if (System.getProperty("java.version").startsWith("1.8"))
        Seq()
      else
        Seq(
          "--add-opens",
          "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
          "--add-opens",
          "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        )
    ),
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
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = scala212_213)
  .configure(addBaseSettingsAndTestDeps)

def resGenFile = (zincRootPath / "zinc" / "resGenerator").getAbsoluteFile

lazy val jar1 = (project in resGenFile / "jar1").settings(sampleProjectSettings("jar"))
lazy val jar2 = (project in resGenFile / "jar2").settings(sampleProjectSettings("jar"))
lazy val classesDep1 =
  (project in resGenFile / "classesDep1").settings(sampleProjectSettings("zip"))

lazy val zinc212 = zinc.jvm(scala212)

lazy val zincTesting = (projectMatrix in internalPath / "zinc-testing")
  .dependsOn(compilerInterface)
  .settings(
    name := "zinc Testing",
    baseSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(scalaCheck, scalatest, verify, sjsonnewScalaJson.value),
    // scala-compiler depends on 1.x, but 2.x works too
    dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addSbtIO, addSbtUtilLogging)

lazy val zincCompile = (projectMatrix in zincRootPath / "zinc-compile")
  .dependsOn(zincCompileCore, zincCompileCore % "test->test")
  .settings(
    name := "zinc Compile",
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[IncompatibleSignatureProblem]("sbt.inc.Doc*"),
      exclude[IncompatibleResultTypeProblem]("sbt.inc.Doc*"),
      exclude[IncompatibleMethTypeProblem]("sbt.inc.Doc*"),
      exclude[DirectMissingMethodProblem]("sbt.inc.Doc*"),
      exclude[ReversedMissingMethodProblem]("sbt.inc.Doc*"),
    ),
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = scala212_213)
  .configure(addBaseSettingsAndTestDeps, addSbtUtilTracking)

// Persists the incremental data structures using Protobuf
lazy val zincPersist = (projectMatrix in internalPath / "zinc-persist")
  .dependsOn(zincCore, zincCompileCore, zincPersistCoreAssembly, zincCore % "test->test")
  .settings(
    name := "zinc Persist",
    libraryDependencies ++= Seq(
      sbinary.exclude("org.scala-lang.modules", "scala-xml_" + scalaBinaryVersion.value),
      scalaXml
    ),
    compileOrder := sbt.CompileOrder.Mixed,
    Compile / scalacOptions ++= (scalaVersion.value match {
      case VersionNumber(Seq(2, 12, _*), _, _) =>
        List("-Ywarn-unused:-imports,-locals,-implicits,-explicits,-privates")
      case _ => Nil
    }),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    mimaSettings,
    mimaBinaryIssueFilters ++= Util.excludeInternalProblems,
    mimaBinaryIssueFilters ++= Seq(
      exclude[IncompatibleMethTypeProblem]("xsbti.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.*"),
      exclude[IncompatibleTemplateDefProblem]("sbt.internal.inc.schema.*"),
      exclude[MissingClassProblem]("xsbti.api.InternalApiProxy$"),
      exclude[MissingClassProblem]("xsbti.api.InternalApiProxy$Modifiers$"),
      exclude[MissingClassProblem]("xsbti.api.InternalApiProxy")
    ),
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = scala212_213)
  .configure(addBaseSettingsAndTestDeps)

lazy val zincPersistCoreAssembly = (projectMatrix in internalPath / "zinc-persist-core-assembly")
  .jvmPlatform(autoScalaLibrary = false)
  .settings(
    name := "zinc-persist-core-assembly",
    crossPaths := false,
    autoScalaLibrary := false,
    exportJars := true,
    Compile / packageBin := (zincPersistCore / Compile / assembly).value,
    mimaPreviousArtifacts := Set.empty,
  )

lazy val zincPersistCore = (project in internalPath / "zinc-persist-core")
  .enablePlugins(ProtobufPlugin)
  .settings(
    name := "zinc-persist-core",
    crossPaths := false,
    autoScalaLibrary := false,
    exportJars := true,
    ProtobufConfig / version := "3.11.4", // sync version w/ plugins.sbt
    ProtobufConfig / protobufRunProtoc := { args =>
      // as per https://github.com/os72/protoc-jar/issues/93 , this is needed
      // to work around the lack of support for Apple M1 architecture.
      // (there is an upstream fix, but as of February 2022 there is no
      // protoc-jar release containing the fix)
      val workaround =
        System.getProperty("os.name") == "Mac OS X" &&
          System.getProperty("os.arch") == "aarch64"
      try {
        if (workaround)
          System.setProperty("os.arch", "x86_64")
        Protoc.runProtoc(s"-v${(ProtobufConfig / version).value}" +: args.toArray)
      } finally if (workaround)
          System.setProperty("os.arch", "aarch64")
    },
    publish / skip := true,
    assembly / assemblyShadeRules := Seq(
      ShadeRule
        .rename("com.google.protobuf.**" -> "sbt.internal.shaded.com.google.protobuf.@1")
        .inAll
    ),
    assembly / assemblyMergeStrategy := { // remove *.proto files
      case PathList(ps @ _*) if ps.last.endsWith(".proto") => MergeStrategy.discard
      case x => (assembly / assemblyMergeStrategy).value(x)
    },
  )

// Implements the core functionality of detecting and propagating changes incrementally.
//   Defines the data structures for representing file fingerprints and relationships and the overall source analysis
lazy val zincCore = (projectMatrix in internalPath / "zinc-core")
  .dependsOn(
    zincCompileCore,
    zincApiInfo,
    zincClasspath,
    compilerInterface,
    zincPersistCoreAssembly,
    compilerBridge % Test,
    zincTesting % Test
  )
  .settings(
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    Test / fork := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    Test / javaOptions += "-Xmx1G",
    name := "zinc Core",
    compileOrder := sbt.CompileOrder.Mixed,
    mimaSettings,
    mimaBinaryIssueFilters ++= Util.excludeInternalProblems,
    mimaBinaryIssueFilters ++= Seq(
      exclude[IncompatibleMethTypeProblem]("xsbti.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.*"),
      exclude[MissingClassProblem]("xsbti.*"),
    ),
    libraryDependencies ++= {
      scalaPartialVersion.value match {
        case Some((2, major)) if major >= 13 =>
          // should match version in sbt's util-collection module
          List("org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0")
        case _ =>
          List()
      }
    },
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = scala212_213)
  .configure(addBaseSettingsAndTestDeps, addSbtIO, addSbtUtilLogging, addSbtUtilRelation)

lazy val zincBenchmarks = (projectMatrix in internalPath / "zinc-benchmarks")
  .dependsOn(zinc % "test->test")
  .enablePlugins(JmhPlugin)
  .settings(
    publish / skip := true,
    ideSkipProject := true, // otherwise IntelliJ complains
    name := "Benchmarks of Zinc and the compiler bridge",
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit" % "6.4.0.202211300538-r",
      "net.openhft" % "affinity" % "3.23.3",
    ),
    Test / javaOptions ++= List("-Xmx600M", "-Xms600M"),
    inConfig(Jmh)(
      List(
        // Rewire as described at the bottom of https://github.com/ktoso/sbt-jmh#adding-to-your-project
        sourceDirectory := (Test / sourceDirectory).value,
        classDirectory := (Test / classDirectory).value,
        dependencyClasspath := (Test / dependencyClasspath).value,
        // rewire tasks, so that 'jmh:run' automatically invokes 'jmh:compile' (otherwise a clean 'jmh:run' would fail)
        compile := compile.dependsOn(Test / compile).value,
        run := run.dependsOn(Test / compile).evaluated,
      )
    ),
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = scala212_213)

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
    Test / unmanagedJars := Seq((compilerBridge212 / Compile / packageSrc).value).classpath,
    Compile / managedSourceDirectories += (Compile / generateContrabands / sourceManaged).value,
    Compile / generateContrabands / sourceManaged := (internalPath / "zinc-compile-core" / "src" / "main" / "contraband-java").getAbsoluteFile,
    mimaSettings,
    mimaBinaryIssueFilters ++= Util.excludeInternalProblems,
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = List(scala212, scala213))
  .configure(addBaseSettingsAndTestDeps, addSbtUtilLogging, addSbtIO, addSbtUtilControl)

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the sbt-contraband plugin.
lazy val compilerInterface = (projectMatrix in internalPath / "compiler-interface")
  .enablePlugins(ContrabandPlugin)
  .settings(
    baseSettings,
    name := "Compiler Interface",
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212),
    compilerVersionDependentScalacOptions,
    libraryDependencies ++= Seq(scalaLibrary.value % Test),
    libraryDependencies ++= Seq(scalatest % Test),
    exportJars := true,
    Compile / resourceGenerators += Def.task {
      val a = (Compile / compile).value
      generateVersionFile(version.value, resourceManaged.value, streams.value, a)
    }.taskValue,
    Compile / generateContrabands / sourceManaged :=
      (internalPath / "compiler-interface" / "src" / "main" / "contraband-java").getAbsoluteFile,
    Compile / managedSourceDirectories += (Compile / generateContrabands / sourceManaged).value,
    crossPaths := false,
    autoScalaLibrary := false,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      // 1.4.0 changed to VirtualFile. These should be internal to Zinc.
      exclude[ReversedMissingMethodProblem]("xsbti.AnalysisCallback.*"),
      exclude[Problem]("xsbti.compile.PerClasspathEntryLookup.*"),
      exclude[Problem]("xsbti.compile.ExternalHooks*"),
      exclude[Problem]("xsbti.compile.FileHash.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.Output.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.OutputGroup.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.SingleOutput.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.MultipleOutput.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.CachedCompiler.*"),
      exclude[Problem]("xsbti.compile.ClassFileManager.*"),
      exclude[Problem]("xsbti.compile.WrappedClassFileManager.*"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.DependencyChanges.*"),
      exclude[Problem]("xsbti.compile.ScalaCompiler.*"),
      exclude[Problem]("xsbti.compile.JavaTool.*"),
      exclude[Problem]("xsbti.compile.JavaTool.*"),
      exclude[Problem]("xsbti.compile.analysis.ReadSourceInfos.*"),
      exclude[Problem]("xsbti.compile.analysis.ReadStamps.*"),
      // This is a breaking change
      exclude[Problem]("xsbti.compile.CompileOptions.*"),
      exclude[Problem]("xsbti.compile.CompileProgress.*"),
      exclude[Problem]("xsbti.InteractiveConsoleFactory.createConsole"),
      // new API points
      exclude[DirectMissingMethodProblem]("xsbti.compile.IncrementalCompiler.compile"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.IncrementalCompiler.compile"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.ScalaInstance.loaderLibraryOnly"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.ScalaInstance.libraryJars"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.IncrementalCompiler.compileAllJava"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.ScalaInstance.loaderCompilerOnly"),
      exclude[ReversedMissingMethodProblem]("xsbti.compile.ScalaInstance.compilerJars"),
      exclude[InheritedNewAbstractMethodProblem]("xsbti.InteractiveConsoleInterface.close"),
    ),
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(autoScalaLibrary = false)
  .configure(addSbtUtilInterface(_))

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
    name := "Compiler Bridge",
    autoScalaLibrary := false,
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
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions = allScalaVersions)

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
  .dependsOn(zinc.jvm(scala212) % "compile->compile;test->test", compilerInterface.jvm(false))
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
    Test / javaOptions += "-Xmx1G",
    Test / javaOptions += s"-Dzinc.build.compilerbridge.scalaVersion=${scalaVersion.value}",
    publish / skip := true,
  )

val scalaPartialVersion = Def.setting(CrossVersion.partialVersion(scalaVersion.value))

def inBoth(ss: Setting[_]*): Seq[Setting[_]] = Seq(Compile, Test).flatMap(inConfig(_)(ss))

// defines operations on the API of a source, including determining whether it has changed and converting it to a string
//   and discovery of classes and annotations
lazy val zincApiInfo = (projectMatrix in internalPath / "zinc-apiinfo")
  .dependsOn(compilerInterface, compilerBridge, zincClassfile % "compile;test->test")
  .settings(
    name := "zinc ApiInfo",
    compilerVersionDependentScalacOptions,
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
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
    ),
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = scala212_213)
  .configure(addBaseSettingsAndTestDeps(_))

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
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = scala212_213)
  .configure(addBaseSettingsAndTestDeps, addSbtIO)

// class file reader and analyzer
lazy val zincClassfile = (projectMatrix in internalPath / "zinc-classfile")
  .dependsOn(compilerInterface, zincTesting % Test)
  .settings(
    name := "zinc Classfile",
    compilerVersionDependentScalacOptions,
    Compile / headerSources ~= { xs =>
      val excluded = Set("ZipCentralDir.java", "ZipConstants.java", "ZipUtils.java")
      xs filter { x =>
        !excluded(x.getName)
      }
    },
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
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = scala212_213)
  .configure(addBaseSettingsAndTestDeps, addSbtIO, addSbtUtilLogging)

// re-implementation of scripted engine
lazy val zincScripted = (projectMatrix in internalPath / "zinc-scripted")
  .dependsOn(zinc % "compile;test->test")
  .enablePlugins(BuildInfoPlugin)
  .settings(
    baseSettings,
    ideSkipProject := true, // otherwise IntelliJ complains
    publish / skip := true,
    name := "zinc Scripted",
    Compile / buildInfo := Nil, // Only generate build info for tests
    BuildInfoPlugin.buildInfoScopedSettings(Test),
    Test / buildInfoPackage := "sbt.internal.inc",
    Test / buildInfoKeys := Seq[BuildInfoKey](zinc212 / sourceDirectory),
    conflictWarning := ConflictWarning.disable,
  )
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala212))
  .jvmPlatform(scalaVersions = List(scala212))
  .configure(
    _.dependsOn(compilerBridge210, compilerBridge211, compilerBridge212, compilerBridge213)
  )
  .configure(addSbtUtilScripted)

lazy val zincScripted212 = zincScripted.jvm(scala212)

def bridges = {
  if (sys.props("java.specification.version") == "1.8") {
    List(compilerBridge210 / publishLocal, compilerBridge211 / publishLocal)
  } else {
    List(
      compilerBridge210 / publishLocal,
      compilerBridge211 / publishLocal,
      compilerBridge212 / publishLocal,
      compilerBridge213 / publishLocal,
    )
  }
}

val publishBridges = taskKey[Unit]("")
val crossTestBridges = taskKey[Unit]("")

publishBridges := Def.task(()).dependsOn(bridges: _*).value
crossTestBridges := (compilerBridgeTest / Test / test).dependsOn(publishBridges).value

addCommandAlias(
  "runBenchmarks", {
    val dir = IO.createTemporaryDirectory.getAbsolutePath
    Seq(
      s"${compilerBridge213.id}/packageBin",
      s"${compilerBridge212.id}/packageBin",
      s"${zincBenchmarks.jvm(scala212).id}/Test/run $dir",
      s"${zincBenchmarks.jvm(scala212).id}/jmh:run -p _tempDir=$dir -prof gc -foe true",
      s"""eval IO.delete(file("$dir"))""",
    ).mkString(";", ";", "")
  }
)

def scriptedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (_: State) => scriptedParser(dir)).parsed
  doScripted(
    (zincScripted212 / Test / fullClasspath).value,
    (zincScripted212 / scalaInstance).value,
    scriptedSource.value,
    result,
    scriptedBufferLog.value,
    scriptedCompileToJar.value,
  )
}
