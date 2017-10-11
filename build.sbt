import BuildImplementation.{ BuildDefaults, BuildCommands }
import Util._
import Dependencies._
import Scripted._

lazy val zincRoot: Project = (project in file("."))
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
    name := "zinc Root",
    scriptedBufferLog := true,
    scriptedPrescripted := BuildDefaults.addSbtAlternateResolver _,
    scriptedSource := (sourceDirectory in zinc).value / "sbt-test",
    scripted :=
      BuildDefaults.zincScripted(compilerBridge, compilerInterface, zincScripted).evaluated,
    scriptedUnpublished := BuildDefaults.zincOnlyScripted(zincScripted).evaluated,
    scriptedPublishAll := publishLocal.all(ScopeFilter(inAnyProject)).value,
    commands in Global ++=
      BuildCommands.all(compilerBridge, compilerBridgeTest, compilerInterface, zincBenchmarks),
    noPublish,
  )

lazy val zinc = (project in file("zinc"))
  .settings(name := "zinc", mimaSettings)
  .configure(addTestDependencies)
  .dependsOn(
    zincCore,
    zincPersist,
    zincCompileCore,
    zincClassfile,
    zincIvyIntegration % "compile->compile;test->test",
    zincTesting % Test
  )

lazy val zincTesting = (project in internalPath / "zinc-testing")
  .settings(
    name := "zinc Testing",
    libraryDependencies ++= Seq(scalaCheck, scalatest, junit, sjsonnewScalaJson.value),
    noPublish,
  )
  .configure(addSbtLmCore, addSbtLmIvy)

lazy val zincCompile = (project in file("zinc-compile"))
  .dependsOn(zincCompileCore, zincCompileCore % "test->test")
  .settings(name := "zinc Compile", mimaSettings)
  .configure(addSbtUtilTracking, addTestDependencies)

// Persists the incremental data structures using Protobuf
lazy val zincPersist = (project in internalPath / "zinc-persist")
  .dependsOn(zincCore, zincCore % "test->test")
  .settings(
    name := "zinc Persist",
    libraryDependencies += sbinary,
    compileOrder := sbt.CompileOrder.Mixed,
    PB.targets in Compile := List(scalapb.gen() -> (sourceManaged in Compile).value),
    mimaSettings,
  )
  .configure(addTestDependencies)

// Implements the core functionality of detecting and propagating changes incrementally.
//   Defines the data structures for representing file fingerprints and relationships and the overall source analysis
lazy val zincCore = (project in internalPath / "zinc-core")
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
  .configure(addSbtIO, addSbtUtilLogging, addSbtUtilRelation, addTestDependencies)

lazy val zincBenchmarks = (project in internalPath / "zinc-benchmarks")
  .dependsOn(compilerInterface % "compile->compile;compile->test")
  .dependsOn(compilerBridge, zincCore, zincTesting % Test)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "Benchmarks of Zinc and the compiler bridge",
    libraryDependencies ++= List(jgit, affinity),
    javaOptions in Test += "-Xmx600M -Xms600M",
    tearDownBenchmarkResources := BuildDefaults.tearDownBenchmarkResources.value,
    noPublish,
  )

lazy val zincIvyIntegration = (project in internalPath / "zinc-ivy-integration")
  .dependsOn(zincCompileCore, zincTesting % Test)
  .settings(
    name := "zinc Ivy Integration",
    compileOrder := sbt.CompileOrder.ScalaThenJava,
    mimaSettings,
  )
  .configure(addSbtLmCore, addSbtLmIvyTest)

// sbt-side interface to compiler.  Calls compiler-side interface reflectively
lazy val zincCompileCore = (project in internalPath / "zinc-compile-core")
  .enablePlugins(ContrabandPlugin)
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
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-java",
    sourceManaged in (Compile, generateContrabands) :=
      baseDirectory.value / "src" / "main" / "contraband-java",
    mimaSettings,
  )
  .configure(addSbtUtilLogging, addSbtIO, addSbtUtilControl, addTestDependencies)

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the sbt-contraband plugin.
lazy val compilerInterface = (project in internalPath / "compiler-interface")
  .enablePlugins(ContrabandPlugin)
  .settings(
    // javaOnlySettings,
    name := "Compiler Interface",
    // Use the smallest Scala version in the bridgeScalaVersions
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
    zincPublishLocalSettings,
    mimaSettings,
  )
  .configure(addSbtUtilInterface)


/**
 * Compiler-side interface to compiler that is compiled against the compiler being used either in advance or on the fly.
 * Includes API and Analyzer phases that extract source API and relationships.
 * As this is essentially implementations of the compiler-interface (per Scala compiler),
 * the code here should not be consumed without going through the classloader trick and the interface.
 * Due to the hermetic nature of the bridge, there's no necessity to keep binary compatibility across Zinc versions,
 * and therefore there's no `mimaSettings` added.
 * For the case of Scala 2.13 bridge, we didn't even have the bridge to compare against when Zinc 1.0.0 came out.
 */
lazy val compilerBridge: Project = (project in internalPath / "compiler-bridge")
  .dependsOn(compilerInterface)
  .settings(
    crossScalaVersions := bridgeScalaVersions,
    adaptOptionsForOldScalaVersions,
    libraryDependencies += scalaCompiler.value % "provided",
    autoScalaLibrary := false,
    // precompiledSettings,
    name := "Compiler Bridge",
    inCompileAndTest(unmanagedSourceDirectories ++= BuildDefaults.handleScalaSpecificSources.value),
    // Use a bootstrap compiler bridge to compile the compiler bridge.
    scalaCompilerBridgeSource := BuildDefaults.customCompilerBridge.value,
    exportJars := true,
    cleanSbtBridge := BuildDefaults.cleanSbtBridge.value,
    publishLocal := publishLocal.dependsOn(cleanSbtBridge).value,
    zincPublishLocalSettings,
  )

/**
 * Tests for the compiler bridge.
 * This is split into a separate subproject because testing introduces more dependencies
 * (Zinc API Info, which transitively depends on IO).
 */
lazy val compilerBridgeTest = (project in internalPath / "compiler-bridge-test")
  .dependsOn(compilerBridge, compilerInterface % "test->test", zincApiInfo % "test->test")
  .settings(
    name := "Compiler Bridge Test",
    adaptOptionsForOldScalaVersions,
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    fork in Test := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    crossScalaVersions := bridgeTestScalaVersions,
    libraryDependencies += scalaCompiler.value,
    zincPublishLocalSettings,
    skip in publish := true,
  )

// defines operations on the API of a source, including determining whether it has changed and converting it to a string
//   and discovery of Projclasses and annotations
lazy val zincApiInfo = (project in internalPath / "zinc-apiinfo")
  .dependsOn(compilerInterface, zincClassfile % "compile;test->test")
  .settings(
    name := "zinc ApiInfo",
    crossScalaVersions := bridgeTestScalaVersions,
    adaptOptionsForOldScalaVersions,
    mimaSettings,
  )
  .configure(addTestDependencies)

// Utilities related to reflection, managing Scala versions, and custom class loaders
lazy val zincClasspath = (project in internalPath / "zinc-classpath")
  .dependsOn(compilerInterface)
  .settings(
    name := "zinc Classpath",
    crossScalaVersions := bridgeTestScalaVersions,
    adaptOptionsForOldScalaVersions,
    libraryDependencies ++= Seq(scalaCompiler.value, launcherInterface),
    mimaSettings,
  )
  .configure(addSbtIO, addTestDependencies)

// class file reader and analyzer
lazy val zincClassfile = (project in internalPath / "zinc-classfile")
  .dependsOn(compilerInterface % "compile;test->test")
  .settings(
    name := "zinc Classfile",
    crossScalaVersions := bridgeTestScalaVersions,
    adaptOptionsForOldScalaVersions,
    mimaSettings,
  )
  .configure(addSbtIO, addSbtUtilLogging, addTestDependencies)

// re-implementation of scripted engine
lazy val zincScripted = (project in internalPath / "zinc-scripted")
  .dependsOn(zinc, zincIvyIntegration % "test->test")
  .settings(name := "zinc Scripted", noPublish)
  .configure(addSbtUtilScripted)
