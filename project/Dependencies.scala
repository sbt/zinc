import sbt._
import Keys._

object Dependencies {

  val scala282 = "2.8.2"
  val scala293 = "2.9.3"
  val scala210 = "2.10.6"
  val scala211 = "2.11.8"

  val bootstrapSbtVersion = "0.13.8"
  val ioVersion = "1.0.0-M6"
  val utilVersion = "0.1.0-M15"
  val lmVersion = "0.1.0-X2"

  private lazy val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private lazy val utilLogging   = "org.scala-sbt" %% "util-logging"   % utilVersion
  private lazy val utilControl   = "org.scala-sbt" %% "util-control"   % utilVersion
  private lazy val utilRelation  = "org.scala-sbt" %% "util-relation"  % utilVersion
  private lazy val utilTesting   = "org.scala-sbt" %% "util-testing"   % utilVersion
  private lazy val utilTracking  = "org.scala-sbt" %% "util-tracking"  % utilVersion
  private lazy val utilInterface = "org.scala-sbt"  % "util-interface" % utilVersion
  private lazy val utilScripted  = "org.scala-sbt" %% "util-scripted"  % utilVersion

  private lazy val libraryManagement = "org.scala-sbt" %% "librarymanagement" % lmVersion

  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"

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

  def addSbtIO(p: Project): Project            = addSbtModule(p, sbtIoPath, "io", sbtIO)

  def addSbtUtilControl(p: Project): Project   = addSbtModule(p, sbtUtilPath, "utilControl",   utilControl)
  def addSbtUtilInterface(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilInterface", utilInterface)
  def addSbtUtilLogging(p: Project): Project   = addSbtModule(p, sbtUtilPath, "utilLogging",   utilLogging)
  def addSbtUtilRelation(p: Project): Project  = addSbtModule(p, sbtUtilPath, "utilRelation",  utilRelation)
  def addSbtUtilScripted(p: Project): Project  = addSbtModule(p, sbtUtilPath, "utilScripted",  utilScripted, Some(Test))
  def addSbtUtilTesting(p: Project): Project   = addSbtModule(p, sbtUtilPath, "utilTesting",   utilTesting, Some(Test))
  def addSbtUtilTracking(p: Project): Project  = addSbtModule(p, sbtUtilPath, "utilTracking",  utilTracking)

  def addSbtLm(p: Project): Project = addSbtModule(p, sbtLmPath, "lm", libraryManagement)

  lazy val scalaLibrary = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

  lazy val sbinary = "org.scala-sbt" %% "sbinary" % "0.4.3"
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.1"
  lazy val scalatest = "org.scalatest" %% "scalatest" % "2.2.6"
  lazy val junit = "junit" % "junit" % "4.11"
  lazy val diffUtils = "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"
  lazy val sjsonnewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % "0.4.1"

  def addTestDependencies(p: Project): Project = p.settings(libraryDependencies ++=
    Seq(
      scalaCheck % Test,
      scalatest % Test,
      junit % Test,
      diffUtils % Test
    )).configure(addSbtUtilTesting)
}
