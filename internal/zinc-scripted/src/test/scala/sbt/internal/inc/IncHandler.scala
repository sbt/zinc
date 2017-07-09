package sbt
package internal
package inc

import java.io.{ File, FileInputStream }
import java.net.URLClassLoader
import java.util.jar.Manifest

import sbt.util.Logger
import sbt.util.InterfaceUtil._
import sbt.internal.inc.JavaInterfaceUtil.{ EnrichOption, EnrichOptional }
import xsbt.api.Discovery
import xsbti.{ Problem, Severity }
import xsbti.compile.{
  ClasspathOptionsUtil,
  CompileAnalysis,
  CompileOrder,
  CompilerCache,
  DefinesClass,
  IncOptions,
  IncOptionsUtil,
  PerClasspathEntryLookup,
  PreviousResult,
  Compilers => XCompilers
}
import sbt.io.IO
import sbt.io.syntax._
import sbt.io.DirectoryFilter
import java.lang.reflect.Method
import java.lang.reflect.Modifier.{ isPublic, isStatic }
import java.util.{ Optional, Properties }

import sbt.internal.inc.classpath.{ ClassLoaderCache, ClasspathUtilities }
import sbt.internal.scripted.{ StatementHandler, TestFailed }
import sbt.internal.inctest.{ Build, JsonProtocol, Project }
import sbt.internal.util.ManagedLogger
import sjsonnew.support.scalajson.unsafe.{ Converter, Parser => JsonParser }

import scala.{ PartialFunction => ?=> }
import scala.collection.mutable

final case class IncInstance(si: xsbti.compile.ScalaInstance, cs: XCompilers)

final class IncHandler(directory: File, cacheDir: File, scriptedLog: ManagedLogger)
    extends BridgeProviderSpecification
    with StatementHandler {

  type State = Option[IncInstance]
  type IncCommand = (ProjectStructure, List[String], IncInstance) => Unit

  val compiler = new IncrementalCompilerImpl
  def initialState: Option[IncInstance] = None
  def finish(state: Option[IncInstance]): Unit = ()
  val buildStructure: mutable.Map[String, ProjectStructure] = mutable.Map.empty
  def initBuildStructure(): Unit = {
    val build = initBuild
    build.projects.foreach { p =>
      val in = p.in.getOrElse(directory / p.name)
      val version = p.scalaVersion.getOrElse(scala.util.Properties.versionNumberString)
      val project = ProjectStructure(p.name, p.dependsOn, in, scriptedLog, lookupProject, version)
      buildStructure(p.name) = project
    }
  }

  initBuildStructure()

  private final val RootIdentifier = "root"
  def initBuild: Build = {
    import JsonProtocol._
    if ((directory / "build.json").exists) {
      val json = JsonParser.parseFromFile(directory / "build.json").get
      Converter.fromJsonUnsafe[Build](json)
    } else Build(projects = Vector(Project(name = RootIdentifier).withIn(directory)))
  }

  def lookupProject(name: String): ProjectStructure = buildStructure(name)

  override def apply(command: String, arguments: List[String], state: State): State = {
    val splitCommands = command.split("/").toList
    // Note that root does not do aggregation as sbt does.
    val (project, commandToRun) = splitCommands match {
      case sub :: cmd :: Nil => buildStructure(sub) -> cmd
      case cmd :: Nil        => buildStructure(RootIdentifier) -> cmd
      case _                 => sys.error(s"The command is either empty or has more than one `/`: $command")
    }
    val runner = (ii: IncInstance) => commands(commandToRun)(project, arguments, ii)
    Some(onIncInstance(state, project)(runner))
  }

  def onIncInstance(i: Option[IncInstance], p: ProjectStructure)(
      run: IncInstance => Unit): IncInstance = {
    val instance = i.getOrElse(onNewIncInstance(p))
    run(instance)
    instance
  }

  private final val noLogger = Logger.Null
  private[this] def onNewIncInstance(p: ProjectStructure): IncInstance = {
    val scalaVersion = p.scalaVersion
    val (compilerBridge, si) = IncHandler.scriptedCompilerCache.get(scalaVersion) match {
      case Some(alreadyInstantiated) =>
        alreadyInstantiated
      case None =>
        val compilerBridge = getCompilerBridge(cacheDir, noLogger, scalaVersion)
        val si = scalaInstance(scalaVersion, cacheDir, noLogger)
        val toCache = (compilerBridge, si)
        IncHandler.scriptedCompilerCache.put(scalaVersion, toCache)
        toCache
    }
    val analyzingCompiler = scalaCompiler(si, compilerBridge)
    IncInstance(si, compiler.compilers(si, ClasspathOptionsUtil.boot, None, analyzingCompiler))
  }

  private final val unit = (_: Seq[String]) => ()
  def scalaCompiler(instance: xsbti.compile.ScalaInstance, bridgeJar: File): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(instance, bridgeJar)
    val classpath = ClasspathOptionsUtil.boot
    new AnalyzingCompiler(instance, bridgeProvider, classpath, unit, IncHandler.classLoaderCache)
  }

  lazy val commands: Map[String, IncCommand] = Map(
    noArgs("compile") { case (p, i) => p.compile(i); () },
    noArgs("clean") { case (p, _)   => p.clean() },
    onArgs("checkIterations") {
      case (p, x :: Nil, i) => p.checkNumberOfCompilerIterations(i, x.toInt)
    },
    onArgs("checkRecompilations") {
      case (p, step :: classNames, i) => p.checkRecompilations(i, step.toInt, classNames)
    },
    onArgs("checkClasses") {
      case (p, src :: products, i) => p.checkClasses(i, dropRightColon(src), products)
    },
    onArgs("checkMainClasses") {
      case (p, src :: products, i) => p.checkMainClasses(i, dropRightColon(src), products)
    },
    onArgs("checkProducts") {
      case (p, src :: products, i) => p.checkProducts(i, dropRightColon(src), products)
    },
    onArgs("checkDependencies") {
      case (p, cls :: dependencies, i) => p.checkDependencies(i, dropRightColon(cls), dependencies)
    },
    noArgs("checkSame") { case (p, i) => p.checkSame(i) },
    onArgs("run") {
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
    noArgs("package") { case (p, i) => p.packageBin(i) },
    onArgs("checkWarnings") {
      case (p, count :: Nil, _) => p.checkMessages(count.toInt, Severity.Warn)
    },
    onArgs("checkWarning") {
      case (p, index :: expected :: Nil, _) => p.checkMessage(index.toInt, expected, Severity.Warn)
    },
    onArgs("checkErrors") {
      case (p, count :: Nil, _) => p.checkMessages(count.toInt, Severity.Error)
    },
    onArgs("checkError") {
      case (p, idx :: expected :: Nil, _) => p.checkMessage(idx.toInt, expected, Severity.Error)
    },
    noArgs("checkNoClassFiles") { case (p, _) => p.checkNoGeneratedClassFiles() }
  )

  private def dropRightColon(s: String) = if (s endsWith ":") s dropRight 1 else s

  private def onArgs(commandName: String)(
      pf: (ProjectStructure, List[String], IncInstance) ?=> Unit
  ): (String, IncCommand) =
    commandName ->
      ((p, xs, i) => applyOrElse(pf, (p, xs, i), p.unrecognizedArguments(commandName, xs)))

  private def noArgs(commandName: String)(
      pf: (ProjectStructure, IncInstance) ?=> Unit
  ): (String, IncCommand) =
    commandName ->
      ((p, xs, i) => applyOrElse(pf, (p, i), p.acceptsNoArguments(commandName, xs)))

  private def applyOrElse[A, B](pf: A ?=> B, x: A, fb: => B) = pf.applyOrElse(x, (_: A) => fb)
}

case class ProjectStructure(
    name: String,
    dependsOn: Vector[String],
    baseDirectory: File,
    scriptedLog: ManagedLogger,
    lookupProject: String => ProjectStructure,
    scalaVersion: String
) extends BridgeProviderSpecification {
  val compiler = new IncrementalCompilerImpl
  val maxErrors = 100
  class PerClasspathEntryLookupImpl(
      am: File => Option[CompileAnalysis],
      definesClassLookup: File => DefinesClass
  ) extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Optional[CompileAnalysis] =
      am(classpathEntry).toOptional
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
  val fileStore = AnalysisStore.cached(FileBasedStore.binary(cacheFile))
  def prev =
    fileStore.get match {
      case Some((a, s)) => new PreviousResult(Optional.of(a), Optional.of(s))
      case _            => compiler.emptyPreviousResult
    }
  def unmanagedJars: List[File] = (baseDirectory / "lib" ** "*.jar").get.toList
  def lookupAnalysis: File => Option[CompileAnalysis] = {
    val f0: PartialFunction[File, Option[CompileAnalysis]] = {
      case x if x.getAbsoluteFile == classesDir.getAbsoluteFile => prev.analysis.toOption
    }
    val f1 = (f0 /: dependsOnRef) { (acc, dep) =>
      acc orElse {
        case x if x.getAbsoluteFile == dep.classesDir.getAbsoluteFile => dep.prev.analysis.toOption
      }
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

  def clean(): Unit = IO.delete(classesDir)

  def checkNumberOfCompilerIterations(i: IncInstance, expected: Int): Unit = {
    val analysis = compile(i)
    assert(
      (analysis.compilations.allCompilations.size: Int) == expected,
      "analysis.compilations.allCompilations.size = %d (expected %d)"
        .format(analysis.compilations.allCompilations.size, expected)
    )
    ()
  }

  def checkRecompilations(i: IncInstance, step: Int, expected: List[String]): Unit = {
    val analysis = compile(i)
    val allCompilations = analysis.compilations.allCompilations
    val recompiledClasses: Seq[Set[String]] = allCompilations map { c =>
      val recompiledClasses = analysis.apis.internal.collect {
        case (className, api) if api.compilationTimestamp() == c.getStartTime => className
      }
      recompiledClasses.toSet
    }
    def recompiledClassesInIteration(iteration: Int, classNames: Set[String]) = {
      assert(recompiledClasses(iteration) == classNames,
             "%s != %s".format(recompiledClasses(iteration), classNames))
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

  def checkMainClasses(i: IncInstance, src: String, expected: List[String]): Unit = {
    val analysis = compile(i)
    def mainClasses(src: String): Set[String] =
      analysis.infos.get(baseDirectory / src).getMainClasses.toSet
    def assertClasses(expected: Set[String], actual: Set[String]) =
      assert(expected == actual, s"Expected $expected classes, got $actual")

    assertClasses(expected.toSet, mainClasses(src))
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

  def compile(i: IncInstance): Analysis = {
    dependsOnRef map { dep =>
      dep.compile(i)
    }
    import i._
    val sources = scalaSources ++ javaSources
    val prev0 = prev
    val lookup = new PerClasspathEntryLookupImpl(lookupAnalysis, Locate.definesClass)
    val transactional: Optional[xsbti.compile.ClassFileManagerType] =
      Optional.of(
        new xsbti.compile.TransactionalManagerType(targetDir / "classes.bak",
                                                   sbt.util.Logger.Null))
    // We specify the class file manager explicitly even though it's noew possible
    // to specify it in the incremental option property file (this is the default for sbt)
    val incOptionsFile = baseDirectory / "incOptions.properties"
    val (incOptions, scalacOptions) = loadIncOptions(incOptionsFile)
    val reporter = new LoggerReporter(maxErrors, scriptedLog, identity)
    val extra = Array(t2(("key", "value")))
    val setup = compiler.setup(lookup,
                               skip = false,
                               cacheFile,
                               cache = CompilerCache.fresh,
                               incOptions.withClassfileManagerType(transactional),
                               reporter,
                               optionProgress = None,
                               extra)

    val classpath =
      (i.si.allJars.toList ++ (unmanagedJars :+ classesDir) ++ internalClasspath).toArray
    val in = compiler.inputs(classpath,
                             sources.toArray,
                             classesDir,
                             scalacOptions,
                             Array(),
                             maxErrors,
                             Array(),
                             CompileOrder.Mixed,
                             cs,
                             setup,
                             prev0)
    val result = compiler.compile(in, scriptedLog)
    val analysis = result.analysis match { case a: Analysis => a }
    fileStore.set(analysis, result.setup)
    scriptedLog.info(s"""Compilation done: ${sources.toList.mkString(", ")}""")
    analysis
  }

  def packageBin(i: IncInstance): Unit = {
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
    scriptError(
      "Command '" + commandName + "' does not accept arguments (found '" + spaced(args) + "').")

  def spaced[T](l: Seq[T]): String = l.mkString(" ")

  def scriptError(message: String): Unit = sys.error("Test script error: " + message)

  def discoverMainClasses(apisOpt: Option[APIs]): Seq[String] =
    apisOpt match {
      case Some(apis) =>
        def companionsApis(c: xsbti.api.Companions): Seq[xsbti.api.ClassLike] =
          Seq(c.classApi, c.objectApi)
        val allDefs = apis.internal.values.flatMap(x => companionsApis(x.api)).toSeq
        Discovery
          .applications(allDefs)
          .collect({ case (definition, discovered) if discovered.hasMain => definition.name })
          .sorted
      case None => Nil
    }

  // Taken from Run.scala in sbt/sbt
  def getMainMethod(mainClassName: String, loader: ClassLoader) = {
    val mainClass = Class.forName(mainClassName, true, loader)
    val method = mainClass.getMethod("main", classOf[Array[String]])
    // jvm allows the actual main class to be non-public and to run a method in the non-public class,
    //  we need to make it accessible
    method.setAccessible(true)
    val modifiers = method.getModifiers
    if (!isPublic(modifiers))
      throw new NoSuchMethodException(mainClassName + ".main is not public")
    if (!isStatic(modifiers))
      throw new NoSuchMethodException(mainClassName + ".main is not static")
    method
  }

  def invokeMain(loader: ClassLoader, main: Method, options: Seq[String]): Unit = {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try { main.invoke(null, options.toArray[String]); () } finally {
      currentThread.setContextClassLoader(oldLoader)
    }
    ()
  }

  def loadIncOptions(src: File): (IncOptions, Array[String]) = {
    if (src.exists) {
      import scala.collection.JavaConverters._
      val properties = new Properties()
      properties.load(new FileInputStream(src))
      val map = new java.util.HashMap[String, String]
      properties.asScala foreach { case (k: String, v: String) => map.put(k, v) }

      val scalacOptions =
        Option(map.get("scalac.options")).map(_.toString.split(" +")).getOrElse(Array.empty)

      (IncOptionsUtil.fromStringMap(map, scriptedLog), scalacOptions)
    } else (IncOptionsUtil.defaultIncOptions, Array.empty)
  }

  def getProblems(): Seq[Problem] =
    fileStore.get match {
      case Some((analysis: Analysis, _)) =>
        val allInfos = analysis.infos.allInfos.values.toSeq
        allInfos flatMap (i => i.getReportedProblems ++ i.getUnreportedProblems)
      case _ =>
        Nil
    }

  def checkMessages(expected: Int, severity: Severity): Unit = {
    val messages = getProblems() filter (_.severity == severity)
    assert(
      messages.length == expected,
      s"""Expected $expected messages with severity $severity but ${messages.length} found:
                                           |${messages mkString "\n"}""".stripMargin
    )
    ()
  }

  def checkMessage(index: Int, expected: String, severity: Severity): Unit = {
    val problems = getProblems() filter (_.severity == severity)
    problems lift index match {
      case Some(problem) =>
        assert(problem.message contains expected,
               s"""'${problem.message}' doesn't contain '$expected'.""")
      case None =>
        throw new TestFailed(
          s"Problem not found: $index (there are ${problems.length} problem with severity $severity).")
    }
    ()
  }
}

object IncHandler {
  type Cached = (File, xsbti.compile.ScalaInstance)
  private[internal] final val scriptedCompilerCache = new mutable.WeakHashMap[String, Cached]()
  private[internal] final val classLoaderCache = Some(
    new ClassLoaderCache(new URLClassLoader(Array())))
}
