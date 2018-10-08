package localzinc

import sbt._
import Keys._
import Def.Initialize
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath

import scala.language.reflectiveCalls

object Scripted {
  def scriptedPath = file("scripted")
  val publishLocalBinAll = taskKey[Unit]("")
  val scriptedUnpublished = inputKey[Unit]("Execute scripted without publishing sbt first. " +
        "Saves you some time when only your test has changed")
  val scriptedSource = settingKey[File]("")
  val scriptedPrescripted = taskKey[File => Unit]("")
  val scriptedCompileToJar = settingKey[Boolean]("Compile directly to jar in scripted tests")

  import sbt.complete._
  import DefaultParsers._
  // Paging, 1-index based.
  case class ScriptedTestPage(page: Int, total: Int)
  def scriptedParser(scriptedBase: File): Parser[Seq[String]] = {
    val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map {
      (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
    }
    val pairMap = pairs.groupBy(_._1).mapValues(_.map(_._2).toSet);

    val id = charClass(c => !c.isWhitespace && c != '/').+.string
    val groupP = token(id.examples(pairMap.keySet.toSet)) <~ token('/')

    // A parser for page definitions
    val pageP: Parser[ScriptedTestPage] = ("*" ~ NatBasic ~ "of" ~ NatBasic) map {
      case _ ~ page ~ _ ~ total => ScriptedTestPage(page, total)
    }
    // Grabs the filenames from a given test group in the current page definition.
    def pagedFilenames(group: String, page: ScriptedTestPage): Seq[String] = {
      val files = pairMap(group).toSeq.sortBy(_.toLowerCase)
      val pageSize = files.size / page.total
      // The last page may loose some values, so we explicitly keep them
      val dropped = files.drop(pageSize * (page.page - 1))
      if (page.page == page.total) dropped
      else dropped.take(pageSize)
    }
    def nameP(group: String) = {
      token("*".id | id.examples(pairMap(group)))
    }
    val PagedIds: Parser[Seq[String]] =
      for {
        group <- groupP
        page <- pageP
        files = pagedFilenames(group, page)
        // TODO -  Fail the parser if we don't have enough files for the given page size
        //if !files.isEmpty
      } yield files map (f => group + '/' + f)

    val testID = (for (group <- groupP; name <- nameP(group)) yield (group, name))
    val testIdAsGroup = matched(testID) map (test => Seq(test))
    //(token(Space) ~> matched(testID)).*
    (token(Space) ~> (PagedIds | testIdAsGroup)).* map (_.flatten)
  }

  // Interface to cross class loader
  type IncScriptedRunner = {
    def run(resourceBaseDirectory: File, bufferLog: Boolean, compileToJar: Boolean, tests: Array[String]): Unit
  }

  def doScripted(scriptedSbtClasspath: Seq[Attributed[File]],
                 scriptedSbtInstance: ScalaInstance,
                 sourcePath: File,
                 args: Seq[String],
                 bufferLog: Boolean,
                 compileToJar: Boolean,
                 prescripted: File => Unit): Unit = {
    System.err.println(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")
    // Force Log4J to not use a thread context classloader otherwise it throws a CCE
    sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"
    val noJLine = new classpath.FilteredLoader(scriptedSbtInstance.loader, "jline." :: Nil)
    val loader = classpath.ClasspathUtilities.toLoader(scriptedSbtClasspath.files, noJLine)
    val bridgeClass = Class.forName("sbt.internal.inc.IncScriptedRunner", true, loader)
    val bridge = bridgeClass.newInstance.asInstanceOf[IncScriptedRunner]
    // val launcherVmOptions = Array("-XX:MaxPermSize=256M") // increased after a failure in scripted source-dependencies/macro
    try {
      bridge.run(sourcePath, bufferLog, compileToJar, args.toArray)
    } catch { case ite: java.lang.reflect.InvocationTargetException => throw ite.getCause }
  }
}
