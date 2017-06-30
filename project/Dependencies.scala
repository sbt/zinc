import sbt._, Keys._

object Dependencies {
  val scala210 = "2.10.6"
  val scala211 = "2.11.11"
  val scala212 = "2.12.2"

  private val ioVersion = "1.0.0-M11"
  private val lmVersion = "1.0.0-X14"

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val lmLogging = "org.scala-sbt" %% "lm-logging" % lmVersion
  private val lmInterface = "org.scala-sbt" % "lm-interface" % lmVersion
  private val lmCache = "org.scala-sbt" % "lm-cache" % lmVersion
  private val libraryManagement = "org.scala-sbt" %% "librarymanagement" % lmVersion

  val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"

  def getSbtModulePath(key: String, name: String) = {
    val localProps = new java.util.Properties()
    IO.load(localProps, file("project/local.properties"))
    val path = Option(localProps getProperty key) orElse (sys.props get key)
    path foreach (f => println(s"Using $name from $f"))
    path
  }

  lazy val sbtIoPath = getSbtModulePath("sbtio.path", "sbt/io")
  lazy val sbtLmPath = getSbtModulePath("sbtlm.path", "sbt/lm")

  def addSbtModule(
      p: Project,
      path: Option[String],
      projectName: String,
      m: ModuleID,
      c: Option[Configuration] = None
  ) =
    path match {
      case Some(f) =>
        p dependsOn c.fold[ClasspathDependency](ProjectRef(file(f), projectName))(
          ProjectRef(file(f), projectName) % _)
      case None => p settings (libraryDependencies += c.fold(m)(m % _))
    }

  def addSbtIO(p: Project): Project = addSbtModule(p, sbtIoPath, "io", sbtIO)

  def addSbtLmInterface(p: Project): Project =
    addSbtModule(p, sbtLmPath, "lmInterface", lmInterface)
  def addSbtLmLogging(p: Project): Project = addSbtModule(p, sbtLmPath, "lmLogging", lmLogging)
  def addSbtLmCache(p: Project): Project = addSbtModule(p, sbtLmPath, "lmCache", lmCache)
  def addSbtLm(p: Project): Project = addSbtModule(p, sbtLmPath, "lm", libraryManagement)

  val scalaLibrary = Def.setting { "org.scala-lang" % "scala-library" % scalaVersion.value }
  val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  val parserCombinator211 = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"

  val parserCombinator = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5"
  val sbinary = "org.scala-sbt" %% "sbinary" % "0.4.4"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.1"
  val junit = "junit" % "junit" % "4.11"
  val diffUtils = "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"
  val sjsonnewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % "0.7.0"

  def addTestDependencies(p: Project): Project =
    p.settings(
        libraryDependencies ++= Seq(
          scalaCheck % Test,
          scalatest % Test,
          junit % Test,
          diffUtils % Test
        )
    )
}
