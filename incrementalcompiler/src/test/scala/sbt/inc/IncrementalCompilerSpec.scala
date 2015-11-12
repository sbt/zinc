package sbt
package inc

import java.io.File

import sbt.io.IO
import sbt.util.Logger
import sbt.internal.util.UnitSpec
import sbt.internal.inc.{ AnalyzingCompiler, IncrementalCompilerImpl, ScalaInstance, RawCompiler, ClasspathOptions, ComponentCompiler }
import sbt.internal.inc.{ CompilerInterfaceProvider }
import sbt.internal.librarymanagement.{ BaseIvySpecification, IvySbt, InlineIvyConfiguration }
import sbt.librarymanagement.{ ModuleID, UpdateOptions, Resolver }
import java.net.URLClassLoader
import sbt.internal.inc.classpath.ClasspathUtilities
import sbt.io.Path._

class IncrementalCompilerSpec extends BaseIvySpecification {
  override def resolvers: Seq[Resolver] = super.resolvers ++ Seq(Resolver.mavenLocal)
  val ivyConfiguration = mkIvyConfiguration(UpdateOptions())
  val ivySbt = new IvySbt(ivyConfiguration)

  val home = new File(sys.props("user.home"))
  val ivyCache = home / ".ivy2" / "cache"
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val CompilerBridgeId = "compiler-bridge_2.11"
  val JavaClassVersion = System.getProperty("java.class.version")
  val target = new File("target")

  "incremental compiler" should "compile" in {
    val scala211 = scalaCompiler(scala2117Instance, compilerBridge(scala2117Instance, target, log))
    val cs = compiler.compilers(scala2117Instance, ClasspathOptions.boot, None, scala211)
    // val setup = ???
    // val prev = ???
    // val in = compiler.inputs(Array(), Array(), target, Array(), Array(), 100, Array(), CompileOrder.Mixed,
    //  cs, setup, prev)
    assert(true)
  }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(bridgeJar), ClasspathOptions.boot)

  def compilerBridge(scalaInstance: ScalaInstance, cacheDir: File, log: Logger): File = {

    val dir = cacheDir / bridgeId(scalaInstance.actualVersion)
    val bridgeJar = dir / (CompilerBridgeId + ".jar")

    if (!bridgeJar.exists) {
      dir.mkdirs()

      val sourceModule = {
        val dummyModule = ModuleID(xsbti.ArtifactInfo.SbtOrganization + "-tmp", "tmp-module", ComponentCompiler.incrementalVersion, Some("compile"))
        val source = ModuleID(xsbti.ArtifactInfo.SbtOrganization, CompilerBridgeId, ComponentCompiler.incrementalVersion, Some("compile")).sources()
        module(dummyModule, Seq(source), None)
      }

      val compilerBridgeArtifacts =
        for {
          conf <- ivyUpdate(sourceModule).configurations
          m <- conf.modules
          (_, f) <- m.artifacts
        } yield f

      val compilerBridgeSource = compilerBridgeArtifacts find (_.getName endsWith "-sources.jar") getOrElse ???
      val compilerInterfaceJar = compilerBridgeArtifacts find (_.getName startsWith "compiler-interface") getOrElse ???
      val utilInterfaceJar = compilerBridgeArtifacts find (_.getName startsWith "util-interface") getOrElse ???

      compileBridgeJar(CompilerBridgeId, compilerBridgeSource, bridgeJar, compilerInterfaceJar :: utilInterfaceJar :: Nil, scalaInstance, log)
    }
    bridgeJar
  }
  def bridgeId(scalaVersion: String) = CompilerBridgeId + "-" + scalaVersion + "-" + JavaClassVersion
  def compileBridgeJar(label: String, sourceJar: File, targetJar: File, xsbtiJars: Iterable[File], instance: ScalaInstance, log: Logger): Unit = {
    val raw = new RawCompiler(instance, ClasspathOptions.auto, log)
    AnalyzingCompiler.compileSources(sourceJar :: Nil, targetJar, xsbtiJars, label, raw, log)
  }

  val scala2117Instance: ScalaInstance =
    scalaInstance(
      ivyCache / "org.scala-lang" / "scala-compiler" / "jars" / "scala-compiler-2.11.7.jar",
      ivyCache / "org.scala-lang" / "scala-library" / "jars" / "scala-library-2.11.7.jar",
      Seq(ivyCache / "org.scala-lang" / "scala-reflect" / "jars" / "scala-reflect-2.11.7.jar")
    )
  def scalaInstance(scalaCompiler: File, scalaLibrary: File, scalaExtra: Seq[File]): ScalaInstance =
    {
      val loader = scalaLoader(scalaLibrary +: scalaCompiler +: scalaExtra)
      val version = scalaVersion(loader)
      val allJars = (scalaLibrary +: scalaCompiler +: scalaExtra).toArray
      new ScalaInstance(version.getOrElse("unknown"), loader, scalaLibrary, scalaCompiler, allJars, version)
    }
  def scalaLoader(jars: Seq[File]) = new URLClassLoader(toURLs(jars), ClasspathUtilities.rootLoader)
  def scalaVersion(scalaLoader: ClassLoader): Option[String] = {
    propertyFromResource("compiler.properties", "version.number", scalaLoader)
  }
  /**
   * Get a property from a properties file resource in the classloader.
   */
  def propertyFromResource(resource: String, property: String, classLoader: ClassLoader): Option[String] = {
    val props = propertiesFromResource(resource, classLoader)
    Option(props.getProperty(property))
  }
  /**
   * Get all properties from a properties file resource in the classloader.
   */
  def propertiesFromResource(resource: String, classLoader: ClassLoader): java.util.Properties = {
    val props = new java.util.Properties
    val stream = classLoader.getResourceAsStream(resource)
    try { props.load(stream) }
    catch { case e: Exception => }
    finally { if (stream ne null) stream.close }
    props
  }
}
