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

import java.io.File
import java.nio.file.Path

import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.io.IO
import sbt.io.Path._

import xsbti.Logger
import xsbti.compile.CompilerBridgeProvider
import xsbti.compile.{ ScalaInstance => XScalaInstance }

case class ScalaBridge(version: String, jars: Seq[File], classesDir: File)

final class ConstantBridgeProvider(bridges: List[ScalaBridge], tempDir: Path)
    extends CompilerBridgeProvider {

  override def fetchCompiledBridge(instance: XScalaInstance, logger: Logger): File = {
    val javaClassVersion = sys.props("java.class.version")
    val jarName = s"scriptedCompilerBridge-bin_${instance.version}__$javaClassVersion.jar"
    val bridgeJar = tempDir.resolve(jarName).toFile
    // Generate jar from compilation dirs, the resources and a target jarName
    IO.zip(contentOf(bridgeOrBoom(instance.version).classesDir), bridgeJar, Some(0L))
    bridgeJar
  }

  override def fetchScalaInstance(scalaVersion: String, logger: Logger): XScalaInstance = {
    val jars = bridgeOrBoom(scalaVersion).jars.toArray
    assert(jars.forall(_.exists), "One or more jar(s) in the Scala instance do not exist.")
    val libraryJar = jars.find(_.getName.contains("scala-library")).get
    val compilerJar = jars.find(_.getName.contains("scala-compiler")).get
    val loaderLibraryOnly = ClasspathUtil.toLoader(Seq(libraryJar.toPath))
    val missingJars = jars.filter(_ != libraryJar)
    val loader = ClasspathUtil.toLoader(missingJars.map(_.toPath), loaderLibraryOnly)
    new ScalaInstance(scalaVersion, loader, loaderLibraryOnly, libraryJar, compilerJar, jars, None)
  }

  private def bridgeOrBoom(scalaVersion: String) = {
    bridges.find(_.version == scalaVersion).getOrElse {
      val versions = bridges.map(_.version).mkString(",")
      sys.error(s"Missing $scalaVersion in supported versions $versions")
    }
  }

}
