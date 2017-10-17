import BuildImplementation.{BuildDefaults, BuildCommands}
import Util._
import Dependencies._

lazy val zincRoot: Project = (project in file("."))
  .configure(noPublish)
  .aggregate(
    zinc,
    zincTesting,
    zincPersist,
    zincCore,
    zincIvyIntegration,
    zincCompile,
    zincCompileCore,
    compilerInterface,
    compilerBridge,
    zincBenchmarks,
    zincApiInfo,
    zincClasspath,
    zincClassfile,
    zincScripted
  )
  .settings(
    name := "Zinc Root",
    Scripted.scriptedPrescripted := {(f: File) => ()},
    Scripted.scriptedSource := (sourceDirectory in zinc).value / "sbt-test",
    scriptedPublish := cachedPublishLocal.all(ScopeFilter(inAnyProject)).value,
    Scripted.scripted :=
      BuildDefaults.zincScripted(compilerBridge, compilerInterface, zincScripted).evaluated,
    commands in Global ++=
      BuildCommands.all(compilerBridge, compilerInterface, zincApiInfo, zincBenchmarks),
  )

lazy val zinc = (project in file("zinc"))
  .configure(addTestDependencies)
  .settings(name := "zinc", mimaSettings)
  .dependsOn(
    zincCore,
    zincPersist,
    zincCompileCore,
    zincClassfile,
    zincIvyIntegration % "compile->compile;test->test",
    zincTesting % Test
  )

lazy val zincTesting = (project in internalPath / "zinc-testing")
  .configure(addSbtLmCore, addSbtLmIvy, noPublish)
  .settings(
    name := "zinc Testing",
    libraryDependencies ++= Seq(scalaCheck, scalatest, junit, sjsonnewScalaJson.value),
  )

lazy val zincCompile = (project in file("zinc-compile"))
  .dependsOn(zincCompileCore, zincCompileCore % "test->test")
  .configure(addTestDependencies)
  .configure(addSbtUtilTracking)
  .settings(
    name := "zinc Compile",
    mimaSettings,
  )

// Persists the incremental data structures using Protobuf
lazy val zincPersist = (project in internalPath / "zinc-persist")
  .dependsOn(zincCore, zincCore % "test->test")
  .configure(addTestDependencies)
  .settings(
    name := "zinc Persist",
    libraryDependencies += sbinary,
    compileOrder := sbt.CompileOrder.Mixed,
    PB.targets in Compile := List(scalapb.gen() -> (sourceManaged in Compile).value),
    mimaSettings,
  )

// Implements the core functionality of detecting and propagating changes incrementally.
//   Defines the data structures for representing file fingerprints and relationships and the overall source analysis
lazy val zincCore = (project in internalPath / "zinc-core")
  .configure(addTestDependencies, addSbtIO, addSbtUtilLogging, addSbtUtilRelation)
  .dependsOn(
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
  )

lazy val zincBenchmarks = (project in internalPath / "zinc-benchmarks")
  .dependsOn(compilerInterface % "compile->compile;compile->test")
  .dependsOn(compilerBridge, zincCore, zincTesting % Test)
  .enablePlugins(JmhPlugin)
  .configure(noPublish)
  .settings(
    name := "Benchmarks of Zinc and the compiler bridge",
    javaOptions in Test += "-Xmx600M -Xms600M",
    libraryDependencies ++= List(jgit, affinity),
    tearDownBenchmarkResources := BuildDefaults.tearDownBenchmarkResources.value,
  )

lazy val zincIvyIntegration = (project in internalPath / "zinc-ivy-integration")
  .dependsOn(zincCompileCore, zincTesting % Test)
  .configure(addSbtLmCore, addSbtLmIvyTest)
  .settings(
    name := "zinc Ivy Integration",
    compileOrder := sbt.CompileOrder.ScalaThenJava,
    mimaSettings,
  )

// sbt-side interface to compiler.  Calls compiler-side interface reflectively
lazy val zincCompileCore = (project in internalPath / "zinc-compile-core")
  .enablePlugins(ContrabandPlugin)
  .configure(addTestDependencies, addSbtUtilLogging, addSbtIO, addSbtUtilControl)
  .dependsOn(
    compilerInterface % "compile;test->test",
    zincClasspath,
    zincApiInfo,
    zincClassfile,
    zincTesting % Test
  )
  .settings(
    name := "zinc Compile Core",
    libraryDependencies ++= Seq(scalaCompiler.value % Test, launcherInterface, parserCombinator),
    unmanagedJars in Test := Seq(packageSrc in compilerBridge in Compile value).classpath,
    managedSourceDirectories in Compile += baseDirectory.value / "src" / "main" / "contraband-java",
    sourceManaged in (Compile, generateContrabands) :=
      baseDirectory.value / "src" / "main" / "contraband-java",
    mimaSettings,
    // We disable check here temporarily because of https://github.com/sbt/sbt-header/issues/130
    headerCheck in Test := List()
  )

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the sbt-contraband plugin.
lazy val compilerInterface = (project in internalPath / "compiler-interface")
  .enablePlugins(ContrabandPlugin)
  .configure(addSbtUtilInterface)
  .settings(
    // javaOnlySettings,
    name := "Compiler Interface",
    // Use the smallest Scala version in the compilerBridgeScalaVersions
    // Technically the scalaVersion shouldn't have any effect since scala library is not included,
    // but given that Scala 2.10 compiler cannot parse Java 8 source, it's probably good to keep this.
    crossScalaVersions := Seq(scala210),
    scalaVersion := scala210,
    adaptOptionsForOldScalaVersions,
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
    sourceManaged in (Compile, generateContrabands) :=
      baseDirectory.value / "src" / "main" / "contraband-java",
    crossPaths := false,
    autoScalaLibrary := false,
    mimaSettings,
  )


// Compiler-side interface to compiler that is compiled against the compiler being used either in advance or on the fly.
//   Includes API and Analyzer phases that extract source API and relationships.
lazy val compilerBridge: Project = (project in internalPath / "compiler-bridge")
  .dependsOn(compilerInterface % "compile;test->test", zincApiInfo % "test->test")
  .settings(
    crossScalaVersions := bridgeScalaVersions,
    adaptOptionsForOldScalaVersions,
    libraryDependencies += scalaCompiler.value % "provided",
    autoScalaLibrary := false,
    // precompiledSettings,
    name := "Compiler Bridge",
    exportJars := true,
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    fork in Test := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    inCompileAndTest(unmanagedSourceDirectories ++= BuildDefaults.handleScalaSpecificSources.value),
    mimaSettings,
    cachedPublishLocal := cachedPublishLocal.dependsOn(cachedPublishLocal.in(zincApiInfo)).value,
    // Make sure that the sources are published for the bridge because we need them to compile it
    publishArtifact in (Compile, packageSrc) := true,
  )

// defines operations on the API of a source, including determining whether it has changed and converting it to a string
//   and discovery of Projclasses and annotations
lazy val zincApiInfo = (project in internalPath / "zinc-apiinfo")
  .dependsOn(compilerInterface, zincClassfile % "compile;test->test")
  .configure(addTestDependencies)
  .settings(
    name := "zinc ApiInfo",
    crossScalaVersions := bridgeScalaVersions,
    adaptOptionsForOldScalaVersions,
    mimaSettings,
  )

// Utilities related to reflection, managing Scala versions, and custom class loaders
lazy val zincClasspath = (project in internalPath / "zinc-classpath")
  .dependsOn(compilerInterface)
  .configure(addTestDependencies, addSbtIO)
  .settings(
    name := "zinc Classpath",
    crossScalaVersions := bridgeScalaVersions,
    adaptOptionsForOldScalaVersions,
    libraryDependencies ++= Seq(scalaCompiler.value, launcherInterface),
    mimaSettings,
  )

// class file reader and analyzer
lazy val zincClassfile = (project in internalPath / "zinc-classfile")
  .dependsOn(compilerInterface % "compile;test->test")
  .configure(addTestDependencies, addSbtIO, addSbtUtilLogging)
  .settings(
    name := "zinc Classfile",
    crossScalaVersions := bridgeScalaVersions,
    adaptOptionsForOldScalaVersions,
    mimaSettings,
  )

// re-implementation of scripted engine
lazy val zincScripted = (project in internalPath / "zinc-scripted")
  .dependsOn(zinc, zincIvyIntegration % "test->test")
  .configure(addSbtUtilScripted, noPublish)
  .settings(name := "zinc Scripted")
