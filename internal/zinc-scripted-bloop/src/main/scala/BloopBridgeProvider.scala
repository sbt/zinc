package sbt.inc

import java.io.File

import sbt.internal.inc.classpath.ClasspathUtilities
import xsbti.Logger
import xsbti.compile.{ CompilerBridgeProvider, ScalaInstance }
import bloop.config.Config

final class BloopBridgeProvider(
    defaultBridge: Config.Project,
    bridges: List[Config.Project],
    tempDir: File
) extends CompilerBridgeProvider {
  import BloopBridgeProvider.XScala

  /**
   * Get the location of the compiled Scala compiler bridge for a concrete Scala version.
   */
  override def fetchCompiledBridge(instance: ScalaInstance, logger: Logger): File = {
    /** Generate jar from compilation dirs, the resources and a target name. */
    def generateJar(outputDir: File, targetJar: File) = {
      import sbt.io.IO._
      import sbt.io.syntax._
      import sbt.io.Path._
      val toBeZipped = outputDir.allPaths.pair(relativeTo(outputDir), errorIfNone = true)
      zip(toBeZipped, targetJar)
      targetJar
    }

    val scalaVersion = instance.version()
    bridges.find(_.getScala.version == scalaVersion) match {
      case None => sys.error(s"Missing $scalaVersion in ${bridges.map(_.project.name).mkString}")
      case Some(bridge) =>
        val targetJar: File = {
          import sbt.internal.inc.ZincComponentCompiler.{ binSeparator, javaClassVersion }
          val id = s"${bridge.name}$binSeparator${bridge.getScala.version}__$javaClassVersion.jar"
          tempDir.toPath.resolve(id).toFile
        }

        generateJar(bridge.classesDir.toFile, targetJar)
    }
  }

  /**
   * Get the Scala instance for a given Scala version.
   *
   * @param scalaVersion The scala version we want the instance for.
   * @param logger       A logger.
   * @return A scala instance, useful to get a compiled bridge.
   * @see ScalaInstance
   * @see CompilerBridgeProvider#fetchCompiledBridge
   */
  override def fetchScalaInstance(scalaVersion: String, logger: Logger): ScalaInstance = {

    bridges.find(_.getScala.version == scalaVersion) match {
      case None => sys.error(s"Missing $scalaVersion in ${bridges.map(_.project.name).mkString}")
      case Some(bridge) =>
        val scala = bridge.getScala
        import sbt.internal.inc.ScalaInstance
        val jars = scala.jars.toArray.map(_.toFile)
        assert(jars.forall(_.exists), "One or more jar(s) in the Scala instance do not exist.")

        val libraryJar = jars.filter(_.getName.contains("scala-library")).head
        val compilerJar = jars.filter(_.getName.contains("scala-compiler")).head
        val libraryL = ClasspathUtilities.toLoader(Vector(libraryJar))
        val missingJars = jars.toVector filterNot { _ == libraryJar }
        val loader = ClasspathUtilities.toLoader(missingJars, libraryL)
        new ScalaInstance(scalaVersion, loader, libraryL, libraryJar, compilerJar, jars, None)
    }
  }
}

object BloopBridgeProvider {
  implicit class XScala(val project: Config.Project) extends AnyVal {
    def getScala: Config.Scala =
      project.scala.getOrElse(sys.error(s"No scala found in bridge ${project.name}"))
  }
}
