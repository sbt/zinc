import sbt._, Keys._
import sbt.contraband.ContrabandPlugin.autoImport._

object Dependencies {
  val scala210 = "2.10.6"
  val scala211 = "2.11.11"
  val scala212 = "2.12.2"

  private val ioVersion = "1.0.0-M13"
  private val utilVersion = "1.0.0-M27"
  private val lmVersion = "1.0.0-X18"

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  private val utilControl = "org.scala-sbt" %% "util-control" % utilVersion
  private val utilRelation = "org.scala-sbt" %% "util-relation" % utilVersion
  private val utilTesting = "org.scala-sbt" %% "util-testing" % utilVersion
  private val utilTracking = "org.scala-sbt" %% "util-tracking" % utilVersion
  private val utilInterface = "org.scala-sbt" % "util-interface" % utilVersion
  private val utilScripted = "org.scala-sbt" %% "util-scripted" % utilVersion

  private val libraryManagementCore = "org.scala-sbt" %% "librarymanagement-core" % lmVersion
  private val libraryManagementIvy  = "org.scala-sbt" %% "librarymanagement-ivy" % lmVersion

  val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"

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
        p dependsOn c.fold[ClasspathDep[ProjectReference]](ProjectRef(file(f), projectName))(
          ProjectRef(file(f), projectName) % _)
      case None => p settings (libraryDependencies += c.fold(m)(m % _))
    }

  lazy val sbtIoPath = getSbtModulePath("sbtio.path", "sbt/io")
  lazy val sbtUtilPath = getSbtModulePath("sbtutil.path", "sbt/util")
  lazy val sbtLmPath = getSbtModulePath("sbtlm.path", "sbt/librarymanagement")

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
  def addSbtUtilTesting(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilTesting", utilTesting, Some(Test))
  def addSbtUtilTracking(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilTracking", utilTracking)

  def addSbtLmCore(p: Project): Project = addSbtModule(p, sbtLmPath, "lmCore", libraryManagementCore)
  def addSbtLmIvy(p: Project): Project = addSbtModule(p, sbtLmPath, "lmIvy", libraryManagementIvy)
  def addSbtLmIvyTest(p: Project): Project = addSbtModule(p, sbtLmPath, "lmIvy", libraryManagementIvy, Some(Test))

  val scalaLibrary = Def.setting { "org.scala-lang" % "scala-library" % scalaVersion.value }
  val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }

  val parserCombinator = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5"
  val sbinary = "org.scala-sbt" %% "sbinary" % "0.4.4"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.1"
  val junit = "junit" % "junit" % "4.11"
  val sjsonnew = Def.setting { "com.eed3si9n" %% "sjson-new-core" % contrabandSjsonNewVersion.value }
  val sjsonnewScalaJson = Def.setting { "com.eed3si9n" %% "sjson-new-scalajson" % contrabandSjsonNewVersion.value }
  val xxHashLibrary = "net.jpountz.lz4" % "lz4" % "1.3.0"

  def addTestDependencies(p: Project): Project =
    p.settings(
        libraryDependencies ++=
          Seq(
            scalaCheck % Test,
            scalatest % Test,
            junit % Test
          ))
      .configure(addSbtUtilTesting)
}
