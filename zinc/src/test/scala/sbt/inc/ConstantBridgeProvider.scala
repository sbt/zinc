/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.inc

import java.nio.file.Path
import java.io.File

import sbt.internal.inc.classpath.ClasspathUtil
import xsbti.Logger
import xsbti.compile.CompilerBridgeProvider

case class ScalaBridge(version: String, jars: Seq[File], classesDir: File)

final class ConstantBridgeProvider(
    bridges: List[ScalaBridge],
    tempDir: Path
) extends CompilerBridgeProvider {

  import xsbti.compile.ScalaInstance

  final val binSeparator = "-bin_"
  final val javaClassVersion = System.getProperty("java.class.version")

  /** Get the location of the compiled Scala compiler bridge for a concrete Scala version. */
  override def fetchCompiledBridge(instance: ScalaInstance, logger: Logger): File = {

    /** Generate jar from compilation dirs, the resources and a target name. */
    def generateJar(outputDir: File, targetJar: File) = {
      import sbt.io.IO._
      import sbt.io.syntax._
      import sbt.io.Path._
      val toBeZipped = outputDir.allPaths.pair(relativeTo(outputDir), errorIfNone = true)
      zip(toBeZipped, targetJar, Some(0L))
      targetJar
    }

    val scalaVersion = instance.version
    bridges.find(_.version == scalaVersion) match {
      case None =>
        sys.error(s"Missing $scalaVersion in supported versions ${bridges.map(_.version).mkString}")
      case Some(bridge) =>
        val targetJar: File = {
          val id = s"scriptedCompilerBridge$binSeparator${bridge.version}__$javaClassVersion.jar"
          tempDir.resolve(id).toFile
        }

        generateJar(bridge.classesDir, targetJar)
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
    bridges.find(_.version == scalaVersion) match {
      case None =>
        sys.error(s"""Missing $scalaVersion in supported versions ${bridges
          .map(_.version)
          .mkString(",")}""")
      case Some(bridge) =>
        import sbt.internal.inc.ScalaInstance
        val jars = bridge.jars.toArray
        assert(jars.forall(_.exists), "One or more jar(s) in the Scala instance do not exist.")

        val libraryJar = jars.filter(_.getName.contains("scala-library")).head
        val compilerJar = jars.filter(_.getName.contains("scala-compiler")).head
        val libraryL = ClasspathUtil.toLoader(Vector(libraryJar.toPath))
        val missingJars = jars.toVector filterNot { _ == libraryJar }
        val loader = ClasspathUtil.toLoader(missingJars.map(_.toPath), libraryL)
        new ScalaInstance(scalaVersion, loader, libraryL, libraryJar, compilerJar, jars, None)
    }
  }
}
