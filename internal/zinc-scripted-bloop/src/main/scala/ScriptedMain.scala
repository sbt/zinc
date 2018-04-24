package sbt.inc

import java.io.File
import java.nio.file.Path

import sbt.io.syntax._
import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.Parser._
import sbt.internal.util.complete.Parsers._
import sbt.io.{ AllPassFilter, NameFilter }
import bloop.config.{ Config, ConfigDecoders }
import metaconfig.{ Conf, Configured }
import org.langmeta.inputs.Input

object ScriptedMain {
  def main(args: Array[String]): Unit = {
    val workingDirectory = file(System.getProperty("user.dir"))
    val bloopDir = workingDirectory./(".bloop")
    if (!bloopDir.exists()) {
      System.err.println(s"Bloop config files missing in ${bloopDir.getAbsolutePath}")
      System.exit(1)
    } else {
      val scripted = configFromFile(bloopDir./("zincScripted-test.json"))
      val zinc = configFromFile(workingDirectory./(".bloop")./("zinc.json"))
      val sourceDir = zinc.project.directory.resolve("src").resolve("sbt-test")
      val fullClasspath = scripted.project.classesDir :: scripted.project.classpath.toList
      val parser = scriptedParser(sourceDir.toFile)
      val toParse = args.headOption.map(s => s" $s").getOrElse("")
      Parser.parse(toParse, parser) match {
        case Left(error) => System.err.println(s"Parser error: $error")
        case Right(scriptedArgs) =>
          val buffer: Boolean =
            if (toParse.isEmpty) true
            else args.tail.headOption.map(java.lang.Boolean.getBoolean(_)).getOrElse(true)
          runScripted(fullClasspath, sourceDir, scriptedArgs, buffer)
      }
    }
  }

  private def configFromFile(config: File): Config.File = {
    import metaconfig.typesafeconfig.typesafeConfigMetaconfigParser
    println(s"Loading project from '$config'")
    val input = Input.File(config)
    val configured = Conf.parseInput(input)(typesafeConfigMetaconfigParser)
    ConfigDecoders.allConfigDecoder.read(configured) match {
      case Configured.Ok(file)     => file
      case Configured.NotOk(error) => sys.error(error.toString())
    }
  }

  private def scriptedParser(scriptedBase: File): Parser[Seq[String]] = {
    case class ScriptedTestPage(page: Int, total: Int)
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
      } yield files map (f => group + '/' + f)

    val testID = (for (group <- groupP; name <- nameP(group)) yield (group, name))
    val testIdAsGroup = matched(testID) map (test => Seq(test))
    (token(Space) ~> (PagedIds | testIdAsGroup)).* map (_.flatten)
  }

  type IncScriptedRunner = {
    def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String]): Unit
  }

  import sbt.internal.inc.classpath.ClasspathUtilities
  def runScripted(classpath: Seq[Path], source: Path, args: Seq[String], buffer: Boolean): Unit = {
    println(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")
    // Force Log4J to not use a thread context classloader otherwise it throws a CCE
    sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"
    val loader = ClasspathUtilities.toLoader(classpath.map(_.toFile))
    val bridgeClass = Class.forName("sbt.internal.inc.IncScriptedRunner", true, loader)
    val bridge = bridgeClass.newInstance.asInstanceOf[IncScriptedRunner]
    try bridge.run(source.toFile, buffer, args.toArray)
    catch { case ite: java.lang.reflect.InvocationTargetException => throw ite.getCause }
  }
}
