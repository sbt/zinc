package sbt.inc

import java.io.File

import sbt.internal.inc.Analysis.NonLocalProduct
import sbt.internal.inc._
import sbt.io.IO
import sbt.util.InterfaceUtil._
import sbt.util.Logger
import xsbti.Maybe
import xsbti.compile._

/**
 * Author: Krzysztof Romanowski
 */
class BaseIncCompilerSpec extends BridgeProviderSpecification {

  def mockedCompiler(in: File): ScalaCompiler = {
    val scalaVersion = scala.util.Properties.versionNumberString
    val compilerBridge = getCompilerBridge(in, Logger.Null, scalaVersion)
    val instance = scalaInstance(scalaVersion)
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(compilerBridge), ClasspathOptionsUtil.boot)
  }

  def mockedMiniSetup: MiniSetup = new MiniSetup(
    new Output {},
    new MiniOptions(Array.empty, Array.empty),
    scala.util.Properties.versionNumberString,
    CompileOrder.Mixed,
    /*_nameHashing*/ true,
    true,
    Array.empty
  )

  case class TestCompilerClasspathConfig(
    override val classpath: Seq[File],
    override val perClasspathEntryLookup: PerClasspathEntryLookup,
    override val compiler: xsbti.compile.ScalaCompiler,
    override val currentSetup: MiniSetup = mockedMiniSetup,
    override val incOptions: IncOptions = IncOptionsUtil.defaultIncOptions()
  ) extends CompilerClasspathConfig

  protected def mockedConfig(mockedClasspath: Seq[File], mockedLookup: PerClasspathEntryLookup, tempDir: File): TestCompilerClasspathConfig =
    TestCompilerClasspathConfig(mockedClasspath, mockedLookup, mockedCompiler(tempDir))
}

abstract class ClasspathEntry(in: DirectorySetup) {
  def classpathEntry: File

  def analysis: Option[Analysis]
  def definesClass(className: String): Boolean

  in.register(this)
}

class VirtualDir(val name: String, val classes: Seq[String], val in: DirectorySetup) extends ClasspathEntry(in) {
  val dir = new File(in.baseDir, name)
  dir.mkdirs()

  val classFiles = classes.map(classFile)
  classFiles.foreach(_.createNewFile())

  override def classpathEntry: File = dir

  override def analysis: Option[Analysis] = None

  def classFile(binaryName: String) = new File(dir, s"$binaryName.class")

  override def definesClass(className: String): Boolean = classes.contains(className)
}

case class VirtualJar(override val name: String, override val classes: Seq[String], override val in: DirectorySetup)
  extends VirtualDir(name + "_tmp", classes, in) {
  val location = new File(in.baseDir, s"$name.jar")

  IO.zip(classFiles.map(f => f -> f.getName), location)

  override def classpathEntry: File = location

  override def analysis: Option[Analysis] = None

  override def definesClass(className: String): Boolean = classes.contains(className)
}

case class VirtualProject(name: String, in: DirectorySetup) extends ClasspathEntry(in) {
  private var _analysis: Analysis = Analysis.Empty

  def newSource(v: VirtualSource): Analysis = {
    val newAnalysis = Analysis.empty(true)
      .addSource(
        v.sourceFile,
        Seq(APIs.emptyAnalyzedClass.withName(v.name)),
        Stamp.hash(v.sourceFile),
        SourceInfos.emptyInfo,
        Seq(NonLocalProduct(v.name, v.name, v.classFile, Stamp.lastModified(v.classFile))),
        Nil, Nil, Nil, Nil
      )
    _analysis ++= newAnalysis
    newAnalysis
  }

  val baseDir = new File(in.baseDir, name)
  baseDir.mkdirs()

  val src = new File(baseDir, "src")
  src.mkdir()

  val out = new File(baseDir, "out")
  out.mkdir()

  override def classpathEntry: File = out

  override def analysis: Option[Analysis] = Some(_analysis)

  override def definesClass(className: String): Boolean =
    _analysis.relations.definesClass(className).nonEmpty
}

case class VirtualSource(name: String, in: VirtualProject) {
  val sourceFile = new File(in.src, s"$name.scala")
  val classFile = new File(in.out, s"$name.class")

  sourceFile.createNewFile()
  classFile.createNewFile()

  val analysis = in.newSource(this)
}

class DirectorySetup(val baseDir: File) {
  private var myEntries = Seq.empty[ClasspathEntry]

  def register(entry: ClasspathEntry) = myEntries :+= entry

  def classpath: Seq[File] = myEntries.map(_.classpathEntry)

  def analysisMap = myEntries.map(e => e.classpathEntry -> e.analysis).toMap
  def analyses = myEntries flatMap { _.analysis.toSeq }

  def lookupOnClasspath(binaryClassName: String): Option[File] =
    (myEntries collect {
      case x: ClasspathEntry if x.definesClass(binaryClassName) => x.classpathEntry
    }).headOption

  def entryLookup = new PerClasspathEntryLookup() {
    override def lookupAnalysis(classpathEntry: File): Maybe[CompileAnalysis] =
      o2m(analysisMap.get(classpathEntry).flatten orElse
        (analyses collectFirst {
          case (a: Analysis) if a.relations.allProducts contains classpathEntry => a
        }))
    override def lookupAnalysis(binaryDependency: File, binaryClassName: String): Maybe[CompileAnalysis] =
      lookupOnClasspath(binaryClassName) match {
        case Some(defines) =>
          if (binaryDependency != Locate.resolve(defines, binaryClassName)) Maybe.nothing()
          else lookupAnalysis(defines)
        case None => Maybe.nothing()
      }
    override def lookupAnalysis(binaryClassName: String): Maybe[CompileAnalysis] =
      lookupOnClasspath(binaryClassName) match {
        case Some(defines) =>
          lookupAnalysis(defines)
        case None => Maybe.nothing()
      }
    override def definesClass(classpathEntry: File): DefinesClass = Locate.definesClass(classpathEntry)
  }
}
