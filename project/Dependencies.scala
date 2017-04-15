import sbt._
import Keys._

object Dependencies {

  val scala282 = "2.8.2"
  val scala293 = "2.9.3"
  val scala210 = "2.10.6"
  val scala211 = "2.11.8"
  val scala212 = "2.12.1"

  val bootstrapSbtVersion = "0.13.8"
  private val ioVersion = "1.0.0-M10"
  private val utilVersion = "1.0.0-M22"
  private val lmVersion = "1.0.0-X9"

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val utilLogging   = "org.scala-sbt" %% "util-logging"   % utilVersion
  private val utilControl   = "org.scala-sbt" %% "util-control"   % utilVersion
  private val utilRelation  = "org.scala-sbt" %% "util-relation"  % utilVersion
  private val utilTesting   = "org.scala-sbt" %% "util-testing"   % utilVersion
  private val utilTracking  = "org.scala-sbt" %% "util-tracking"  % utilVersion
  private val utilInterface = "org.scala-sbt"  % "util-interface" % utilVersion
  private val utilScripted  = "org.scala-sbt" %% "util-scripted"  % utilVersion

  private val libraryManagement = "org.scala-sbt" %% "librarymanagement" % lmVersion

  val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"

  def getSbtModulePath(key: String, name: String) = {
    val localProps = new java.util.Properties()
    IO.load(localProps, file("project/local.properties"))
    val path = Option(localProps getProperty key) orElse (sys.props get key)
    path foreach (f => println(s"Using $name from $f"))
    path
  }

  lazy val sbtIoPath   = getSbtModulePath("sbtio.path",   "sbt/io")
  lazy val sbtUtilPath = getSbtModulePath("sbtutil.path", "sbt/util")
  lazy val sbtLmPath   = getSbtModulePath("sbtlm.path",   "sbt/lm")

  def addSbtModule(p: Project, path: Option[String], projectName: String, m: ModuleID, c: Option[Configuration] = None) =
    path match {
      case Some(f) => p dependsOn c.fold[ClasspathDependency](ProjectRef(file(f), projectName))(ProjectRef(file(f), projectName) % _)
      case None    => p settings (libraryDependencies += c.fold(m)(m % _))
    }

  def addSbtIO(p: Project): Project = addSbtModule(p, sbtIoPath, "io", sbtIO)

  def addSbtUtilControl(p: Project): Project   = addSbtModule(p, sbtUtilPath, "utilControl",   utilControl)
  def addSbtUtilInterface(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilInterface", utilInterface)
  def addSbtUtilLogging(p: Project): Project   = addSbtModule(p, sbtUtilPath, "utilLogging",   utilLogging)
  def addSbtUtilRelation(p: Project): Project  = addSbtModule(p, sbtUtilPath, "utilRelation",  utilRelation)
  def addSbtUtilScripted(p: Project): Project  = addSbtModule(p, sbtUtilPath, "utilScripted",  utilScripted, Some(Test))
  def addSbtUtilTesting(p: Project): Project   = addSbtModule(p, sbtUtilPath, "utilTesting",   utilTesting, Some(Test))
  def addSbtUtilTracking(p: Project): Project  = addSbtModule(p, sbtUtilPath, "utilTracking",  utilTracking)

  def addSbtLm(p: Project): Project = addSbtModule(p, sbtLmPath, "lm", libraryManagement)

  val scalaLibrary = Def.setting { "org.scala-lang" % "scala-library" % scalaVersion.value }
  val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

  val parserCombinator = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5"
  val sbinary = "org.scala-sbt" %% "sbinary" % "0.4.4"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.1"
  val junit = "junit" % "junit" % "4.11"
  val diffUtils = "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"
  val sjsonnewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % "0.7.0"

  def addTestDependencies(p: Project): Project = p.settings(libraryDependencies ++=
    Seq(
      scalaCheck % Test,
      scalatest % Test,
      junit % Test,
      diffUtils % Test
    )).configure(addSbtUtilTesting)
}
