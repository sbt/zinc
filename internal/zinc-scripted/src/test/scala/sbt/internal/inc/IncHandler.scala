package sbt
package internal
package inc

import java.io.{ File, FileInputStream }
import sbt.util.Logger
import sbt.util.InterfaceUtil._
import xsbt.api.Discovery
import xsbti.{ Maybe, Problem, Severity }
import xsbti.compile.{ CompileAnalysis, CompileOrder, DefinesClass, IncOptionsUtil, PreviousResult, Compilers => XCompilers, IncOptions }
import xsbti.compile.PerClasspathEntryLookup
import sbt.io.IO
import sbt.io.syntax._

import java.lang.reflect.Method
import java.lang.reflect.Modifier.{ isPublic, isStatic }
import java.util.Properties
import sbt.internal.inc.classpath.ClasspathUtilities

import sbt.internal.scripted.{ StatementHandler, TestFailed }

final case class IncInstance(si: ScalaInstance, cs: XCompilers)

final class IncHandler(directory: File, scriptedLog: Logger) extends BridgeProviderSpecification with StatementHandler {
  type State = Option[IncInstance]
  type IncCommand = (List[String], IncInstance) => Unit
  val compiler = new IncrementalCompilerImpl
  val scalaVersion = scala.util.Properties.versionNumberString
  val si = scalaInstance(scalaVersion)
  val maxErrors = 100
  class Lookup(am: File => Maybe[CompileAnalysis]) extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Maybe[CompileAnalysis] =
      am(classpathEntry)

    override def definesClass(classpathEntry: File): DefinesClass =
      Locate.definesClass(classpathEntry)
  }
  val targetDir = directory / "target"
  val classesDir = targetDir / "classes"
  val scalaSourceDirectory = directory / "src" / "main" / "scala"
  val javaSourceDirectory = directory / "src" / "main" / "java"
  def scalaSources: List[File] =
    (scalaSourceDirectory ** "*.scala").get.toList ++
      (directory * "*.scala").get.toList
  def javaSources: List[File] =
    (javaSourceDirectory ** "*.java").get.toList ++
      (directory * "*.java").get.toList
  val cacheFile = directory / "target" / "inc_compile"
  val fileStore = AnalysisStore.cached(FileBasedStore(cacheFile))
  def unmanagedJars: List[File] = (directory / "lib" ** "*.jar").get.toList

  lazy val commands: Map[String, IncCommand] = Map(
    "compile" -> {
      case (Nil, i) =>
        compile(i)
        ()
      case (xs, _) => acceptsNoArguments("compile", xs)
    },
    "clean" -> {
      case (Nil, i) =>
        clean(i)
        ()
      case (xs, _) => acceptsNoArguments("clean", xs)
    },
    "checkIterations" -> {
      case (x :: Nil, i) =>
        checkNumberOfCompilerIterations(i, x.toInt)
      case (xs, _) => unrecognizedArguments("checkIterations", xs)
    },
    "checkRecompilations" -> {
      case (Nil, _) => unrecognizedArguments("checkRecompilations", Nil)
      case (step :: classNames, i) =>
        checkRecompilations(i, step.toInt, classNames)
    },
    "checkClasses" -> {
      case (src :: products, i) =>
        val srcFile = if (src endsWith ":") src dropRight 1 else src
        checkClasses(i, srcFile, products)
      case (other, _) => unrecognizedArguments("checkClasses", other)
    },
    "checkProducts" -> {
      case (src :: products, i) =>
        val srcFile = if (src endsWith ":") src dropRight 1 else src
        checkProducts(i, srcFile, products)
      case (other, _) => unrecognizedArguments("checkProducts", other)
    },
    "checkDependencies" -> {
      case (cls :: dependencies, i) =>
        val className = if (cls endsWith ":") cls dropRight 1 else cls
        checkDependencies(i, className, dependencies)
      case (other, _) => unrecognizedArguments("checkDependencies", other)
    },
    "checkSame" -> {
      case (Nil, i) =>
        checkSame(i)
        ()
      case (xs, _) => acceptsNoArguments("checkSame", xs)
    },
    "run" -> {
      case (params, i) =>
        val analysis = compile(i)
        discoverMainClasses(analysis) match {
          case Seq(mainClassName) =>
            val classpath = i.si.allJars :+ classesDir
            val loader = ClasspathUtilities.makeLoader(classpath, i.si, directory)
            val main = getMainMethod(mainClassName, loader)
            invokeMain(loader, main, params)
          case _ =>
            throw new TestFailed("Found more than one main class.")
        }
    },
    "checkWarnings" -> {
      case (count :: Nil, _) =>
        checkMessages(count.toInt, Severity.Warn)
      case (other, _) =>
        unrecognizedArguments("checkWarnings", other)
    },
    "checkWarning" -> {
      case (index :: expected :: Nil, _) =>
        checkMessage(index.toInt, expected, Severity.Warn)
      case (other, _) =>
        unrecognizedArguments("checkWarning", other)
    },
    "checkErrors" -> {
      case (count :: Nil, _) =>
        checkMessages(count.toInt, Severity.Error)
      case (other, _) =>
        unrecognizedArguments("checkErrors", other)
    },
    "checkError" -> {
      case (index :: expected :: Nil, _) =>
        checkMessage(index.toInt, expected, Severity.Error)
      case (other, _) =>
        unrecognizedArguments("checkError", other)
    }
  )

  def checkSame(i: IncInstance): Unit =
    {
      val analysis = compile(i)
      analysis.apis.internal foreach {
        case (_, api) =>
          assert(xsbt.api.SameAPI(api.api, api.api))
      }
    }

  def clean(i: IncInstance): Unit =
    IO.delete(classesDir)

  def checkNumberOfCompilerIterations(i: IncInstance, expected: Int): Unit =
    {
      val analysis = compile(i)
      assert(
        (analysis.compilations.allCompilations.size: Int) == expected,
        "analysis.compilations.allCompilations.size = %d (expected %d)".format(analysis.compilations.allCompilations.size, expected)
      )
    }

  def checkRecompilations(i: IncInstance, step: Int, expected: List[String]): Unit =
    {
      val analysis = compile(i)
      val allCompilations = analysis.compilations.allCompilations
      val recompiledClasses: Seq[Set[String]] = allCompilations map { c =>
        val recompiledClasses = analysis.apis.internal.collect {
          case (className, api) if api.compilation.startTime == c.startTime => className
        }
        recompiledClasses.toSet
      }
      def recompiledClassesInIteration(iteration: Int, classNames: Set[String]) = {
        assert(recompiledClasses(iteration) == classNames, "%s != %s".format(recompiledClasses(iteration), classNames))
      }

      assert(step < allCompilations.size.toInt)
      recompiledClassesInIteration(step, expected.toSet)

    }

  def checkClasses(i: IncInstance, src: String, expected: List[String]): Unit = {
    val analysis = compile(i)
    def classes(src: String): Set[String] = analysis.relations.classNames(directory / src)
    def assertClasses(expected: Set[String], actual: Set[String]) =
      assert(expected == actual, s"Expected $expected classes, got $actual")

    assertClasses(expected.toSet, classes(src))
  }

  def checkProducts(i: IncInstance, src: String, expected: List[String]): Unit = {
    val analysis = compile(i)
    def relativeClassDir(f: File): File = f.relativeTo(classesDir) getOrElse f
    def products(srcFile: String): Set[String] = {
      val productFiles = analysis.relations.products(directory / srcFile)
      productFiles.map(relativeClassDir).map(_.getPath)
    }
    def assertClasses(expected: Set[String], actual: Set[String]) =
      assert(expected == actual, s"Expected $expected products, got $actual")

    assertClasses(expected.toSet, products(src))
  }

  def checkDependencies(i: IncInstance, className: String, expected: List[String]): Unit = {
    val analysis = compile(i)
    def classDeps(cls: String): Set[String] = analysis.relations.internalClassDep.forward(cls)
    def assertDependencies(expected: Set[String], actual: Set[String]) =
      assert(expected == actual, s"Expected $expected dependencies, got $actual")

    assertDependencies(expected.toSet, classDeps(className))
  }

  def compile(i: IncInstance): Analysis =
    {
      import i._
      val sources = scalaSources ++ javaSources
      val prev = fileStore.get match {
        case Some((a, s)) => new PreviousResult(Maybe.just(a), Maybe.just(s))
        case _            => compiler.emptyPreviousResult
      }
      val lookup = new Lookup(_ => prev.analysis)
      val transactional: xsbti.Maybe[xsbti.compile.ClassfileManagerType] =
        Maybe.just(new xsbti.compile.TransactionalManagerType(targetDir / "classes.bak", sbt.util.Logger.Null))
      // you can't specify class file manager in the properties files so let's overwrite it to be the transactional
      // one (that's the default for sbt)
      val incOptions = loadIncOptions(directory / "incOptions.properties").withClassfileManagerType(transactional)
      val reporter = new LoggerReporter(maxErrors, scriptedLog, identity)
      val extra = Array(t2(("key", "value")))
      val setup = compiler.setup(lookup, skip = false, cacheFile, CompilerCache.fresh, incOptions, reporter, extra)
      val classpath = (i.si.allJars.toList ++ unmanagedJars :+ classesDir).toArray
      val in = compiler.inputs(classpath, sources.toArray, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev)
      val result = compiler.compile(in, scriptedLog)
      val analysis = result.analysis match { case a: Analysis => a }
      fileStore.set(analysis, result.setup)
      analysis
    }

  def initialState: State = None

  def apply(command: String, arguments: List[String], i: Option[IncInstance]): Option[IncInstance] =
    onIncInstance(i) { x: IncInstance =>
      commands(command)(arguments, x)
    }

  def onIncInstance(i: Option[IncInstance])(f: IncInstance => Unit): Option[IncInstance] =
    i match {
      case Some(x) =>
        f(x)
        i
      case None =>
        onNewIncInstance(f)
    }

  private[this] def onNewIncInstance(f: IncInstance => Unit): Option[IncInstance] =
    {
      val compilerBridge = getCompilerBridge(directory, Logger.Null, scalaVersion)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val i = IncInstance(si, cs)
      f(i)
      Some(i)
    }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(bridgeJar), ClasspathOptionsUtil.boot)

  def finish(state: Option[IncInstance]): Unit = ()

  def unrecognizedArguments(commandName: String, args: List[String]): Unit =
    scriptError("Unrecognized arguments for '" + commandName + "': '" + spaced(args) + "'.")

  def acceptsNoArguments(commandName: String, args: List[String]): Unit =
    scriptError("Command '" + commandName + "' does not accept arguments (found '" + spaced(args) + "').")

  def spaced[T](l: Seq[T]): String = l.mkString(" ")

  def scriptError(message: String): Unit = sys.error("Test script error: " + message)

  // Taken from Defaults.scala in sbt/sbt
  private def discoverMainClasses(analysis: inc.Analysis): Seq[String] = {
    def companionsApis(c: xsbti.api.Companions): Seq[xsbti.api.ClassLike] =
      Seq(c.classApi, c.objectApi)
    val allDefs = analysis.apis.internal.values.flatMap(x => companionsApis(x.api)).toSeq
    Discovery.applications(allDefs).collect({ case (definition, discovered) if discovered.hasMain => definition.name }).sorted
  }

  // Taken from Run.scala in sbt/sbt
  private def getMainMethod(mainClassName: String, loader: ClassLoader) =
    {
      val mainClass = Class.forName(mainClassName, true, loader)
      val method = mainClass.getMethod("main", classOf[Array[String]])
      // jvm allows the actual main class to be non-public and to run a method in the non-public class,
      //  we need to make it accessible
      method.setAccessible(true)
      val modifiers = method.getModifiers
      if (!isPublic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not public")
      if (!isStatic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not static")
      method
    }

  private def invokeMain(loader: ClassLoader, main: Method, options: Seq[String]): Unit = {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try { main.invoke(null, options.toArray[String]); () }
    finally { currentThread.setContextClassLoader(oldLoader) }
  }

  private def loadIncOptions(src: File): IncOptions = {
    if (src.exists) {
      import collection.JavaConversions._
      val properties = new Properties()
      properties.load(new FileInputStream(src))
      val map = new java.util.HashMap[String, String]
      properties foreach { case (k: String, v: String) => map.put(k, v) }
      IncOptionsUtil.fromStringMap(map)
    } else IncOptionsUtil.defaultIncOptions
  }

  private def getProblems(): Seq[Problem] =
    fileStore.get() match {
      case Some((analysis: Analysis, _)) =>
        val allInfos = analysis.infos.allInfos.values.toSeq
        allInfos flatMap (i => i.reportedProblems ++ i.unreportedProblems)
      case _ =>
        Nil
    }

  private def checkMessages(expected: Int, severity: Severity): Unit = {
    val messages = getProblems() filter (_.severity == severity)
    assert(messages.length == expected, s"""Expected $expected messages with severity $severity but ${messages.length} found:
                                           |${messages mkString "\n"}""".stripMargin)
  }

  private def checkMessage(index: Int, expected: String, severity: Severity): Unit = {
    val problems = getProblems() filter (_.severity == severity)
    problems lift index match {
      case Some(problem) =>
        assert(problem.message contains expected, s"""'${problem.message}' doesn't contain '$expected'.""")
      case None =>
        throw new TestFailed(s"Problem not found: $index (there are ${problems.length} problem with severity $severity).")
    }

  }

}
