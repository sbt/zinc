import sbt._, Keys._
import sbt.contraband.ContrabandPlugin.autoImport._

object Dependencies {
  def nightlyVersion: Option[String] =
    sys.env.get("BUILD_VERSION") orElse sys.props.get("sbt.build.version")

  val scala210 = "2.10.7"
  val scala211 = "2.11.12"
  val scala212 = "2.12.15"
  val scala213 = "2.13.8"
  val defaultScalaVersion = scala212
  val allScalaVersions = Seq(defaultScalaVersion, scala210, scala211, scala213)
  val scala212_213 = Seq(defaultScalaVersion, scala213)

  private val ioVersion = nightlyVersion.getOrElse("1.6.0")
  private val utilVersion = nightlyVersion.getOrElse("1.6.2")

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  private val utilControl = "org.scala-sbt" %% "util-control" % utilVersion
  private val utilRelation = "org.scala-sbt" %% "util-relation" % utilVersion
  private val utilTracking = "org.scala-sbt" %% "util-tracking" % utilVersion
  private val utilInterface = "org.scala-sbt" % "util-interface" % utilVersion
  private val utilScripted = "org.scala-sbt" %% "util-scripted" % utilVersion

  val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.1.3"

  def getSbtModulePath(key: String, name: String) = {
    val localProps = new java.util.Properties()
    IO.load(localProps, file("project/local.properties"))
    val path = Option(localProps getProperty key) orElse (sys.props get key)
    path foreach (f => println(s"Using $name from $f"))
    path
  }

  def addSbtModule(
      p: Project,
      path: Option[String],
      projectName: String,
      m: ModuleID,
      c: Option[Configuration] = None
  ) =
    path match {
      case Some(f) =>
        p dependsOn ClasspathDependency(ProjectRef(file(f), projectName), c.map(_.name))
      case None =>
        p settings (libraryDependencies += m.withConfigurations(c.map(_.name)))
    }

  lazy val sbtIoPath = getSbtModulePath("sbtio.path", "sbt/io")
  lazy val sbtUtilPath = getSbtModulePath("sbtutil.path", "sbt/util")

  def addSbtIO(p: Project): Project = addSbtModule(p, sbtIoPath, "io", sbtIO)

  def addSbtUtilControl(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilControl", utilControl)
  def addSbtUtilInterface(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilInterface", utilInterface)
  def addSbtUtilLogging(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilLogging", utilLogging)
  def addSbtUtilRelation(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilRelation", utilRelation)
  def addSbtUtilScripted(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilScripted", utilScripted, Some(Test))
  def addSbtUtilTracking(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilTracking", utilTracking)

  val scalaLibrary = Def.setting { "org.scala-lang" % "scala-library" % scalaVersion.value }
  val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }

  val parserCombinator = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
  val sbinary = "org.scala-sbt" %% "sbinary" % "0.5.1"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.15.3"
  val scalatest = "org.scalatest" %% "scalatest" % "3.2.10"
  val junit = "junit" % "junit" % "4.13.2"
  val verify = "com.eed3si9n.verify" %% "verify" % "0.2.0"
  val sjsonnew = Def.setting {
    "com.eed3si9n" %% "sjson-new-core" % contrabandSjsonNewVersion.value
  }
  val sjsonnewScalaJson = Def.setting {
    "com.eed3si9n" %% "sjson-new-scalajson" % contrabandSjsonNewVersion.value
  }
  val zeroAllocationHashing = "net.openhft" % "zero-allocation-hashing" % "0.15"

  def log4jVersion = "2.16.0"
  val log4jApi = "org.apache.logging.log4j" % "log4j-api" % log4jVersion
  val log4jCore = "org.apache.logging.log4j" % "log4j-core" % log4jVersion
  val log4jSlf4jImpl = "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion
  // specify all of log4j modules to prevent misalignment
  val log4jDependencies = Vector(log4jApi, log4jCore, log4jSlf4jImpl)

  def addTestDependencies(p: Project): Project =
    p.settings(
      libraryDependencies ++= Seq(
        scalaCheck % Test,
        scalatest % Test,
        verify % Test,
        junit % Test
      )
    )
}
