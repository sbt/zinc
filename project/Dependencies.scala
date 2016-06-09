import sbt._
import Keys._

object Dependencies {

  val scala282 = "2.8.2"
  val scala293 = "2.9.3"
  val scala210 = "2.10.6"
  val scala211 = "2.11.8"

  val bootstrapSbtVersion = "0.13.8"
  val ioVersion = "1.0.0-M6"
  val utilVersion = "0.1.0-M13"
  val lmVersion = "0.1.0-M11"
  lazy val sbtIO = "org.scala-sbt" %% "io" % ioVersion
  lazy val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  lazy val utilControl = "org.scala-sbt" %% "util-control" % utilVersion
  lazy val utilRelation = "org.scala-sbt" %% "util-relation" % utilVersion
  lazy val utilTesting = "org.scala-sbt" %% "util-testing" % utilVersion
  lazy val utilTracking = "org.scala-sbt" %% "util-tracking" % utilVersion
  lazy val utilInterface = "org.scala-sbt" % "util-interface" % utilVersion
  lazy val utilScripted = "org.scala-sbt" %% "util-scripted" % utilVersion
  lazy val libraryManagement = "org.scala-sbt" %% "librarymanagement" % lmVersion

  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"

  lazy val scalaLibrary = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

  lazy val sbinary = "org.scala-sbt" %% "sbinary" % "0.4.3"
  lazy val barbaryWatchService = "net.incongru.watchservice" % "barbary-watchservice" % "1.0"
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.1"
  lazy val scalatest = "org.scalatest" %% "scalatest" % "2.2.6"
  lazy val junit = "junit" % "junit" % "4.11"
  def testDependencies = Seq(libraryDependencies ++=
    Seq(
      utilTesting % Test,
      scalaCheck % Test,
      scalatest % Test,
      junit % Test
    ))
}
