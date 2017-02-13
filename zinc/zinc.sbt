/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

def resurcesDir = (file("zinc") / "src" /  "test" / "resources" / "bin").getAbsoluteFile


val genTestResTask = TaskKey[Seq[File]]("gen-test-resources")

def relaxNon212: Seq[Setting[_]] = Seq(
  scalacOptions := {
    val old = scalacOptions.value
    scalaBinaryVersion.value match {
      case "2.12" => old
      case _      => old filterNot Set("-Xfatal-warnings", "-deprecation", "-Ywarn-unused", "-Ywarn-unused-import")
    }
  }
)

def projectSettings(ext: String) = Seq(
  (scalaSource in Compile) := baseDirectory.value / "src",
  genTestResTask := {
    val target = resurcesDir /  s"${name.value}.$ext"
    IO.copyFile((packageBin in Compile).value, target)
    Seq(target)
  }
) ++ relaxNon212

val resGenFile = file("resGenerator")

val jar1 = (project in resGenFile / "jar1")  settings(projectSettings("jar"):_*)
val jar2 = (project in resGenFile / "jar2") settings(projectSettings("jar"):_*)
val classesDep1 = (project in resGenFile / "classesDep1") settings(projectSettings("zip"):_*)


resourceGenerators in Test ++=  Seq(
  (genTestResTask in jar1).taskValue,
  (genTestResTask in jar2).taskValue,
  (genTestResTask in classesDep1).taskValue)