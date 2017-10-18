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
    name := "zinc Core",
    // Forking is mandatory because unit tests enable `usejavacp` and expects deps in the classpath
    fork in Test := true,
    // Tests are run in parallel and forked, so the compiler instances are memory hungry
    javaOptions in Test += "-Xmx1G",
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

// This is a Java-only module, the set Scala version has no effect on it
lazy val compilerInterface = (project in internalPath / "compiler-interface")
  .enablePlugins(ContrabandPlugin)
  .configure(addSbtUtilInterface)
  .settings(
    name := "Compiler Interface",
    // For compatibility, use bridge cross versions that include 2.10
    // 2.10 allows us to test that the java sources can be parsed before JDK8
    crossScalaVersions := bridgeScalaVersions,
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

lazy val compilerBridge: Project = (project in internalPath / "compiler-bridge")
  .dependsOn(compilerInterface % "compile;test->test", zincApiInfo % "test->test")
  .settings(
    name := "Compiler Bridge",
    crossScalaVersions := bridgeScalaVersions,
    adaptOptionsForOldScalaVersions,
    libraryDependencies += scalaCompiler.value % "provided",
    autoScalaLibrary := false,
    exportJars := true,
    // Forking is mandatory because unit tests enable `usejavacp` and expects deps in the classpath
    fork in Test := true,
    // Tests are run in parallel and forked, so the compiler instances are memory hungry
    javaOptions in Test += "-Xmx1G",
    inCompileAndTest(unmanagedSourceDirectories ++= BuildDefaults.handleScalaSpecificSources.value),
    mimaSettings,
    // Make sure that the sources are published for the bridge because we need them to compile it
    publishArtifact in (Compile, packageSrc) := true,
    // Cached publish local in the bridge also publishes api info and the compiler interface
    cachedPublishLocal := cachedPublishLocal
      .dependsOn(cachedPublishLocal.in(zincApiInfo))
      .dependsOn(cachedPublishLocal.in(compilerInterface))
      .value,
  )

lazy val zincApiInfo = (project in internalPath / "zinc-apiinfo")
  .dependsOn(compilerInterface, zincClassfile % "compile;test->test")
  .configure(addTestDependencies)
  .settings(
    name := "zinc ApiInfo",
    crossScalaVersions := bridgeScalaVersions,
    adaptOptionsForOldScalaVersions,
    mimaSettings,
  )

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

lazy val zincClassfile = (project in internalPath / "zinc-classfile")
  .dependsOn(compilerInterface % "compile;test->test")
  .configure(addTestDependencies, addSbtIO, addSbtUtilLogging)
  .settings(
    name := "zinc Classfile",
    crossScalaVersions := bridgeScalaVersions,
    adaptOptionsForOldScalaVersions,
    mimaSettings,
  )

lazy val zincScripted = (project in internalPath / "zinc-scripted")
  .dependsOn(zinc, zincIvyIntegration % "test->test")
  .configure(addSbtUtilScripted, noPublish)
  .settings(name := "zinc Scripted")
