/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.inc

import java.io.File
import java.nio.file.Path

import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.{ ClasspathUtil, DualLoader }
import sbt.io.IO
import sbt.io.Path._

import xsbti.Logger
import xsbti.compile.CompilerBridgeProvider
import xsbti.compile.{ ScalaInstance => XScalaInstance }

case class ScalaBridge(version: String, jars: Seq[File], bridgeJarOrDir: Either[File, File])

final class ConstantBridgeProvider(bridges: List[ScalaBridge], tempDir: Path)
    extends CompilerBridgeProvider {

  override def fetchCompiledBridge(instance: XScalaInstance, logger: Logger): File =
    bridgeOrBoom(instance.version).bridgeJarOrDir match {
      case Left(classesDir) =>
        val javaClassVersion = sys.props("java.class.version")
        val jarName = s"scriptedCompilerBridge-bin_${instance.version}__$javaClassVersion.jar"
        val bridgeJar = tempDir.resolve(jarName).toFile
        // Generate jar from compilation dirs, the resources and a target jarName
        IO.zip(contentOf(classesDir), bridgeJar, Some(0L))
        bridgeJar
      case Right(bridgeJar) => bridgeJar
    }

  override def fetchScalaInstance(scalaVersion: String, logger: Logger): XScalaInstance = {
    val jars = bridgeOrBoom(scalaVersion).jars.toArray
    assert(jars.forall(_.exists), "One or more jar(s) in the Scala instance do not exist.")
    val libraryJars = jars.filter(_.getName.contains("scala-library"))
    val loaderLibraryOnly0 = ClasspathUtil.toLoader(libraryJars.toSeq.map(_.toPath))
    val loaderLibraryOnly =
      if (scalaVersion.startsWith("2.")) loaderLibraryOnly0
      else
        createDualLoader(
          loaderLibraryOnly0,
          getClass.getClassLoader,
        )
    val missingJars = jars.toVector diff libraryJars.toVector
    val loader = ClasspathUtil.toLoader(
      missingJars.map(_.toPath),
      loaderLibraryOnly,
    )
    new ScalaInstance(
      scalaVersion,
      loader = loader,
      loaderCompilerOnly = loader,
      loaderLibraryOnly = loaderLibraryOnly,
      libraryJars = libraryJars,
      compilerJars = jars,
      jars,
      Some(scalaVersion),
    )
  }

  private def createDualLoader(
      loader: ClassLoader,
      sbtLoader: ClassLoader
  ): ClassLoader = {
    val xsbtiFilter = (name: String) => name.startsWith("xsbti.")
    val notXsbtiFilter = (name: String) => !xsbtiFilter(name)
    new DualLoader(
      loader,
      notXsbtiFilter,
      _ => true,
      sbtLoader,
      xsbtiFilter,
      _ => false
    )
  }

  private def bridgeOrBoom(scalaVersion: String): ScalaBridge = {
    bridges.find(_.version == scalaVersion).getOrElse {
      val versions = bridges.map(_.version).mkString(",")
      sys.error(s"Missing $scalaVersion in supported versions $versions")
    }
  }
}
