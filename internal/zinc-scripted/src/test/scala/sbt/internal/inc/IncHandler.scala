package sbt
package internal
package inc

import java.io.{ File, FileInputStream }
import java.net.URLClassLoader
import java.util.jar.Manifest
import sbt.util.Logger
import sbt.util.InterfaceUtil._
import xsbt.api.Discovery
import xsbti.{ Maybe, Problem, Severity }
import xsbti.compile.{ CompileAnalysis, CompileOrder, DefinesClass, IncOptionsUtil, PreviousResult, Compilers => XCompilers, IncOptions }
import xsbti.compile.PerClasspathEntryLookup
import sbt.io.IO
import sbt.io.syntax._
import sbt.io.DirectoryFilter

import java.lang.reflect.Method
import java.lang.reflect.Modifier.{ isPublic, isStatic }
import java.util.Properties
import sbt.internal.inc.classpath.{ ClasspathUtilities, ClassLoaderCache }
import sbt.internal.scripted.{ StatementHandler, TestFailed }
import sbt.internal.inctest.{ Build, Project, JsonProtocol }
import sjsonnew.support.scalajson.unsafe.{ Converter, Parser => JsonParser }
import scala.collection.mutable

final case class IncInstance(si: ScalaInstance, cs: XCompilers)

final class IncHandler(directory: File, scriptedLog: Logger) extends BridgeProviderSpecification with StatementHandler {
  type State = Option[IncInstance]
  type IncCommand = (ProjectStructure, List[String], IncInstance) => Unit
  val scalaVersion = scala.util.Properties.versionNumberString
  val si = scalaInstance(scalaVersion)
  val compiler = new IncrementalCompilerImpl
  def initialState: Option[IncInstance] = None
  def finish(state: Option[IncInstance]): Unit = ()
  val buildStructure: mutable.Map[String, ProjectStructure] = mutable.Map.empty
  def initBuildStructure(): Unit =
    {
      val b = initBuild
      b.projects foreach { p =>
        buildStructure(p.name) =
          ProjectStructure(p.name, p.dependsOn,
            p.in match {
              case Some(x) => x
              case None    => directory / p.name
            },
            scriptedLog,
            lookupProject)
      }
    }
  initBuildStructure()
  def initBuild: Build =
    if ((directory / "build.json").exists) {
      import JsonProtocol._
      val json = JsonParser.parseFromFile(directory / "build.json").get
      Converter.fromJsonUnsafe[Build](json)
    } else Build(projects = Vector(
      Project(name = "root").withIn(directory)
    ))
  def lookupProject(name: String): ProjectStructure = buildStructure(name)

  def apply(command: String, arguments: List[String], i: Option[IncInstance]): Option[IncInstance] =
    onIncInstance(i) { x: IncInstance =>
      command.split("/").toList match {
        case sub :: cmd :: Nil =>
          val p = buildStructure(sub)
          commands(cmd)(p, arguments, x)
        case cmd :: Nil =>
          val p = buildStructure("root")
          // This does not do aggregation.
          commands(cmd)(p, arguments, x)
        case _ =>
          sys.error(s"$command")
      }
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
    new AnalyzingCompiler(instance, CompilerBridgeProvider.constant(bridgeJar), ClasspathOptionsUtil.boot,
      _ => (), Some(new ClassLoaderCache(new URLClassLoader(Array()))))

  lazy val commands: Map[String, IncCommand] = Map(
    "compile" -> {
      case (p, Nil, i) =>
        p.compile(i)
        ()
      case (p, xs, _) => p.acceptsNoArguments("compile", xs)
    },
    "clean" -> {
      case (p, Nil, i) =>
        p.clean(i)
        ()
      case (p, xs, _) => p.acceptsNoArguments("clean", xs)
    },
    "checkIterations" -> {
      case (p, x :: Nil, i) =>
        p.checkNumberOfCompilerIterations(i, x.toInt)
      case (p, xs, _) => p.unrecognizedArguments("checkIterations", xs)
    },
    "checkRecompilations" -> {
      case (p, Nil, _) => p.unrecognizedArguments("checkRecompilations", Nil)
      case (p, step :: classNames, i) =>
        p.checkRecompilations(i, step.toInt, classNames)
    },
    "checkClasses" -> {
      case (p, src :: products, i) =>
        val srcFile = if (src endsWith ":") src dropRight 1 else src
        p.checkClasses(i, srcFile, products)
      case (p, other, _) => p.unrecognizedArguments("checkClasses", other)
    },
    "checkProducts" -> {
      case (p, src :: products, i) =>
        val srcFile = if (src endsWith ":") src dropRight 1 else src
        p.checkProducts(i, srcFile, products)
      case (p, other, _) => p.unrecognizedArguments("checkProducts", other)
    },
    "checkDependencies" -> {
      case (p, cls :: dependencies, i) =>
        val className = if (cls endsWith ":") cls dropRight 1 else cls
        p.checkDependencies(i, className, dependencies)
      case (p, other, _) => p.unrecognizedArguments("checkDependencies", other)
    },
    "checkSame" -> {
      case (p, Nil, i) =>
        p.checkSame(i)
        ()
      case (p, xs, _) => p.acceptsNoArguments("checkSame", xs)
    },
    "run" -> {
      case (p, params, i) =>
        val analysis = p.compile(i)
        p.discoverMainClasses(Some(analysis.apis)) match {
          case Seq(mainClassName) =>
            val classpath = i.si.allJars :+ p.classesDir
            val loader = ClasspathUtilities.makeLoader(classpath, i.si, directory)
            val main = p.getMainMethod(mainClassName, loader)
            p.invokeMain(loader, main, params)
          case _ =>
            throw new TestFailed("Found more than one main class.")
        }
    },
    "package" -> {
      case (p, Nil, i) =>
        p.packageBin(i)
      case (p, other, _) =>
        p.unrecognizedArguments("package", other)
    },
    "checkWarnings" -> {
      case (p, count :: Nil, _) =>
        p.checkMessages(count.toInt, Severity.Warn)
      case (p, other, _) =>
        p.unrecognizedArguments("checkWarnings", other)
    },
    "checkWarning" -> {
      case (p, index :: expected :: Nil, _) =>
        p.checkMessage(index.toInt, expected, Severity.Warn)
      case (p, other, _) =>
        p.unrecognizedArguments("checkWarning", other)
    },
    "checkErrors" -> {
      case (p, count :: Nil, _) =>
        p.checkMessages(count.toInt, Severity.Error)
      case (p, other, _) =>
        p.unrecognizedArguments("checkErrors", other)
    },
    "checkError" -> {
      case (p, index :: expected :: Nil, _) =>
        p.checkMessage(index.toInt, expected, Severity.Error)
      case (p, other, _) =>
        p.unrecognizedArguments("checkError", other)
    },
    "checkNoClassFiles" -> {
      case (p, Nil, i) =>
        p.checkNoGeneratedClassFiles()
        ()
      case (p, xs, _) => p.acceptsNoArguments("checkNoClassFiles", xs)
    }
  )

}

case class ProjectStructure(name: String, dependsOn: Vector[String], baseDirectory: File, scriptedLog: Logger,
  lookupProject: String => ProjectStructure) extends BridgeProviderSpecification {
  val scalaVersion = scala.util.Properties.versionNumberString
  val compiler = new IncrementalCompilerImpl
  val maxErrors = 100
  class PerClasspathEntryLookupImpl(
    am: File => Option[CompileAnalysis],
    definesClassLookup: File => DefinesClass
  ) extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Maybe[CompileAnalysis] =
      o2m(am(classpathEntry))
    override def definesClass(classpathEntry: File): DefinesClass =
      definesClassLookup(classpathEntry)
  }
  val targetDir = baseDirectory / "target"
  val classesDir = targetDir / "classes"
  val generatedClassFiles = classesDir ** "*.class"
  val scalaSourceDirectory = baseDirectory / "src" / "main" / "scala"
  val javaSourceDirectory = baseDirectory / "src" / "main" / "java"
  def scalaSources: List[File] =
    (scalaSourceDirectory ** "*.scala").get.toList ++
      (baseDirectory * "*.scala").get.toList
  def javaSources: List[File] =
    (javaSourceDirectory ** "*.java").get.toList ++
      (baseDirectory * "*.java").get.toList
  val cacheFile = baseDirectory / "target" / "inc_compile.zip"
  val fileStore = AnalysisStore.cached(FileBasedStore(cacheFile))
  def prev =
    fileStore.get match {
      case Some((a, s)) => new PreviousResult(Maybe.just(a), Maybe.just(s))
      case _            => compiler.emptyPreviousResult
    }
  def unmanagedJars: List[File] = (baseDirectory / "lib" ** "*.jar").get.toList
  def lookupAnalysis: File => Option[CompileAnalysis] =
    {
      val f0: PartialFunction[File, Option[CompileAnalysis]] = { case x if x.getAbsoluteFile == classesDir.getAbsoluteFile => m2o(prev.analysis) }
      val f1 = (f0 /: dependsOnRef) { (acc, dep) =>
        acc orElse { case x if x.getAbsoluteFile == dep.classesDir.getAbsoluteFile => m2o(dep.prev.analysis) }
      }
      f1 orElse { case _ => None }
    }
  def dependsOnRef: Vector[ProjectStructure] = dependsOn map { lookupProject(_) }
  def internalClasspath: Vector[File] = dependsOnRef map { _.classesDir }

  def checkSame(i: IncInstance): Unit =
    fileStore.get match {
      case Some((prevAnalysis: Analysis, _)) =>
        val analysis = compile(i)
        analysis.apis.internal foreach {
          case (k, api) =>
            assert(api.apiHash == prevAnalysis.apis.internalAPI(k).apiHash)
        }
      case _ => ()
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
      ()
    }

  def checkRecompilations(i: IncInstance, step: Int, expected: List[String]): Unit =
    {
      val analysis = compile(i)
      val allCompilations = analysis.compilations.allCompilations
      val recompiledClasses: Seq[Set[String]] = allCompilations map { c =>
        val recompiledClasses = analysis.apis.internal.collect {
          case (className, api) if api.compilationTimestamp() == c.startTime => className
        }
        recompiledClasses.toSet
      }
      def recompiledClassesInIteration(iteration: Int, classNames: Set[String]) = {
        assert(recompiledClasses(iteration) == classNames, "%s != %s".format(recompiledClasses(iteration), classNames))
      }

      assert(step < allCompilations.size.toInt)
      recompiledClassesInIteration(step, expected.toSet)
      ()
    }

  def checkClasses(i: IncInstance, src: String, expected: List[String]): Unit = {
    val analysis = compile(i)
    def classes(src: String): Set[String] = analysis.relations.classNames(baseDirectory / src)
    def assertClasses(expected: Set[String], actual: Set[String]) =
      assert(expected == actual, s"Expected $expected classes, got $actual")

    assertClasses(expected.toSet, classes(src))
    ()
  }

  def checkProducts(i: IncInstance, src: String, expected: List[String]): Unit = {
    val analysis = compile(i)
    def relativeClassDir(f: File): File = f.relativeTo(classesDir) getOrElse f
    def products(srcFile: String): Set[String] = {
      val productFiles = analysis.relations.products(baseDirectory / srcFile)
      productFiles.map(relativeClassDir).map(_.getPath)
    }
    def assertClasses(expected: Set[String], actual: Set[String]) =
      assert(expected == actual, s"Expected $expected products, got $actual")

    assertClasses(expected.toSet, products(src))
    ()
  }

  def checkNoGeneratedClassFiles(): Unit = {
    val allClassFiles = generatedClassFiles.get.mkString("\n\t")
    if (!allClassFiles.isEmpty)
      sys.error(s"Classes existed:\n\t$allClassFiles")
  }

  def checkDependencies(i: IncInstance, className: String, expected: List[String]): Unit = {
    val analysis = compile(i)
    def classDeps(cls: String): Set[String] = analysis.relations.internalClassDep.forward(cls)
    def assertDependencies(expected: Set[String], actual: Set[String]) =
      assert(expected == actual, s"Expected $expected dependencies, got $actual")

    assertDependencies(expected.toSet, classDeps(className))
    ()
  }

  def compile(i: IncInstance): Analysis =
    {
      dependsOnRef map { dep => dep.compile(i) }
      import i._
      val sources = scalaSources ++ javaSources
      val prev0 = prev
      val lookup = new PerClasspathEntryLookupImpl(lookupAnalysis, Locate.definesClass)
      val transactional: xsbti.Maybe[xsbti.compile.ClassfileManagerType] =
        Maybe.just(new xsbti.compile.TransactionalManagerType(targetDir / "classes.bak", sbt.util.Logger.Null))
      // you can't specify class file manager in the properties files so let's overwrite it to be the transactional
      // one (that's the default for sbt)
      val incOptions = loadIncOptions(baseDirectory / "incOptions.properties").withClassfileManagerType(transactional)
      val reporter = new LoggerReporter(maxErrors, scriptedLog, identity)
      val extra = Array(t2(("key", "value")))
      val setup = compiler.setup(lookup, skip = false, cacheFile, CompilerCache.fresh, incOptions, reporter, None, extra)
      val classpath = (i.si.allJars.toList ++ (unmanagedJars :+ classesDir) ++ internalClasspath).toArray
      val in = compiler.inputs(classpath, sources.toArray, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev0)
      val result = compiler.compile(in, scriptedLog)
      val analysis = result.analysis match { case a: Analysis => a }
      fileStore.set(analysis, result.setup)
      scriptedLog.info(s"""Compilation done: ${sources.toList.mkString(", ")}""")
      analysis
    }

  def packageBin(i: IncInstance): Unit =
    {
      compile(i)
      val jar = targetDir / s"$name.jar"
      val manifest = new Manifest
      val sources =
        (classesDir ** -DirectoryFilter).get flatMap {
          case x =>
            IO.relativize(classesDir, x) match {
              case Some(path) => List((x, path))
              case _          => Nil
            }
        }
      IO.jar(sources, jar, manifest)
    }

  def unrecognizedArguments(commandName: String, args: List[String]): Unit =
    scriptError("Unrecognized arguments for '" + commandName + "': '" + spaced(args) + "'.")

  def acceptsNoArguments(commandName: String, args: List[String]): Unit =
    scriptError("Command '" + commandName + "' does not accept arguments (found '" + spaced(args) + "').")

  def spaced[T](l: Seq[T]): String = l.mkString(" ")

  def scriptError(message: String): Unit = sys.error("Test script error: " + message)

  def discoverMainClasses(apisOpt: Option[APIs]): Seq[String] =
    apisOpt match {
      case Some(apis) =>
        def companionsApis(c: xsbti.api.Companions): Seq[xsbti.api.ClassLike] =
          Seq(c.classApi, c.objectApi)
        val allDefs = apis.internal.values.flatMap(x =>
          companionsApis(x.api)).toSeq
        Discovery.applications(allDefs).collect({ case (definition, discovered) if discovered.hasMain => definition.name }).sorted
      case None => Nil
    }

  // Taken from Run.scala in sbt/sbt
  def getMainMethod(mainClassName: String, loader: ClassLoader) =
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

  def invokeMain(loader: ClassLoader, main: Method, options: Seq[String]): Unit = {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try { main.invoke(null, options.toArray[String]); () }
    finally { currentThread.setContextClassLoader(oldLoader) }
    ()
  }

  def loadIncOptions(src: File): IncOptions = {
    if (src.exists) {
      import collection.JavaConversions._
      val properties = new Properties()
      properties.load(new FileInputStream(src))
      val map = new java.util.HashMap[String, String]
      properties foreach { case (k: String, v: String) => map.put(k, v) }
      IncOptionsUtil.fromStringMap(map)
    } else IncOptionsUtil.defaultIncOptions
  }

  def getProblems(): Seq[Problem] =
    fileStore.get match {
      case Some((analysis: Analysis, _)) =>
        val allInfos = analysis.infos.allInfos.values.toSeq
        allInfos flatMap (i => i.reportedProblems ++ i.unreportedProblems)
      case _ =>
        Nil
    }

  def checkMessages(expected: Int, severity: Severity): Unit = {
    val messages = getProblems() filter (_.severity == severity)
    assert(messages.length == expected, s"""Expected $expected messages with severity $severity but ${messages.length} found:
                                           |${messages mkString "\n"}""".stripMargin)
    ()
  }

  def checkMessage(index: Int, expected: String, severity: Severity): Unit = {
    val problems = getProblems() filter (_.severity == severity)
    problems lift index match {
      case Some(problem) =>
        assert(problem.message contains expected, s"""'${problem.message}' doesn't contain '$expected'.""")
      case None =>
        throw new TestFailed(s"Problem not found: $index (there are ${problems.length} problem with severity $severity).")
    }
    ()
  }
}
