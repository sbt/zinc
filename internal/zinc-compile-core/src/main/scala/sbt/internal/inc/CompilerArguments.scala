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

package sbt
package internal
package inc

import xsbti.{ ArtifactInfo, PathBasedFile, VirtualFile }
import xsbti.compile.{ MultipleOutput, Output, SingleOutput }
import scala.util
import java.nio.file.Path
import CompilerArguments.{ absString, BootClasspathOption }
import sbt.io.IO
import sbt.io.syntax._

/**
 * Construct the list of compiler arguments that are passed to the Scala
 * compiler based on the current `xsbti.compile.ScalaInstance` and the
 * user-defined `xsbti.compile.ClasspathOptions`.
 *
 * This is required because Scala compiler arguments change depending on
 * the Scala version, e.g. the jars for the Scala library and the Scala
 * compiler have to be present in the classpath and match the Scala version
 * of the current Scala compiler.
 *
 * The Scala home property (`scala.home`) must be unset because Scala puts
 * jars in that directory and pass it in as `bootclasspath`. Therefore, the
 * contents of this property are managed by this implementation and it's
 * strictly forbidden that the client manages this property.
 *
 * @param scalaInstance The instance that defines the Scala version and the
 *                      options that depend on it (e.g. Scala library JAR).
 * @param cpOptions The classpath options for the Scala compiler.
 */
final class CompilerArguments(
    scalaInstance: xsbti.compile.ScalaInstance,
    cpOptions: xsbti.compile.ClasspathOptions
) {
  def makeArguments(
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      output: Option[Path],
      options: Seq[String]
  ): Seq[String] =
    CompilerArguments.outputOption(output) ++
      makeArguments(sources, classpath, options)

  def makeArguments(
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      output: Output,
      options: Seq[String]
  ): Seq[String] =
    CompilerArguments.outputOption(output) ++
      makeArguments(sources, classpath, options)

  def makeArguments(
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      options: Seq[String]
  ): Seq[String] = {
    /* Add dummy to avoid Scalac misbehaviour for empty classpath (as of 2.9.1). */
    def dummy: String = "dummy_" + Integer.toHexString(util.Random.nextInt)

    checkScalaHomeUnset()
    val cp = classpath map {
      case x: PathBasedFile => x.toPath
    }
    val compilerClasspath = finishClasspath(cp)
    val stringClasspath =
      if (compilerClasspath.isEmpty) dummy
      else absString(compilerClasspath)
    val classpathOption = Seq("-classpath", stringClasspath)
    val bootClasspath = bootClasspathOption(hasLibrary(cp))
    val sources1 = sources map {
      case x: PathBasedFile => x.toPath
    }
    options ++ bootClasspath ++ classpathOption ++ abs(sources1)
  }

  /**
   * Finish the classpath by adding extra Scala classpath entries if required.
   *
   * @param classpath The classpath seed to be modified.
   * @return A classpath ready to be passed to the Scala compiler.
   */
  def finishClasspath(classpath: Seq[Path]): Seq[Path] = {
    val filteredClasspath = filterLibrary(classpath)
    val extraCompiler = include(cpOptions.compiler, scalaInstance.compilerJar.toPath)
    val otherJars = scalaInstance.otherJars.toList.map(_.toPath)
    val extraClasspath = include(cpOptions.extra, otherJars: _*)
    filteredClasspath ++ extraCompiler ++ extraClasspath
  }

  def createBootClasspathFor(classpath: Seq[Path]): String =
    createBootClasspath(hasLibrary(classpath) || cpOptions.compiler || cpOptions.extra)

  /**
   * Return the Scala library to the boot classpath if `addLibrary` is true.
   * @param addLibrary Flag to return the Scala library.
   */
  def createBootClasspath(addLibrary: Boolean): String = {
    def findBoot: String = {
      import scala.collection.JavaConverters._
      System.getProperties.asScala.iterator
        .collectFirst {
          case (k, v) if k.endsWith(".boot.class.path") => v
        }
        .getOrElse("")
    }
    val originalBoot = Option(System.getProperty("sun.boot.class.path")).getOrElse(findBoot)
    if (addLibrary) {
      val newBootPrefix =
        if (originalBoot.isEmpty) ""
        else originalBoot + java.io.File.pathSeparator
      newBootPrefix + absString(scalaInstance.libraryJars.map(_.toPath))
    } else originalBoot
  }

  def filterLibrary(classpath: Seq[Path]) =
    if (!cpOptions.filterLibrary) classpath
    else classpath.filterNot(isScalaLibrary)

  def hasLibrary(classpath: Seq[Path]) = classpath.exists(isScalaLibrary)

  def bootClasspathOption(addLibrary: Boolean): Seq[String] =
    if (!cpOptions.autoBoot) Nil
    else Seq(BootClasspathOption, createBootClasspath(addLibrary))

  def bootClasspath(addLibrary: Boolean): Seq[Path] =
    if (!cpOptions.autoBoot) Nil
    else IO.parseClasspath(createBootClasspath(addLibrary)).map(_.toPath)

  def bootClasspathFor(classpath: Seq[Path]) =
    bootClasspath(hasLibrary(classpath))

  def extClasspath: Seq[Path] =
    List("java.ext.dirs", "scala.ext.dirs").flatMap(
      k => (IO.parseClasspath(System.getProperty(k, "")) * "*.jar").get.map(_.toPath)
    )

  private[this] def include(flag: Boolean, jars: Path*) =
    if (flag) jars
    else Nil

  private[this] def abs(files: Seq[Path]) =
    files.map(_.toAbsolutePath.toString).sortWith(_ < _)

  private[this] def checkScalaHomeUnset(): Unit = {
    val scalaHome = System.getProperty("scala.home")
    assert(
      (scalaHome eq null) || scalaHome.isEmpty,
      "'scala.home' should not be set (was " + scalaHome + ")"
    )
  }

  private[this] val isScalaLibrary: Path => Boolean = file => {
    val name = file.getFileName.toString
    name.contains(ArtifactInfo.ScalaLibraryID) ||
    scalaInstance.libraryJars.exists(_.getName == name)
  }
}

object CompilerArguments {
  val BootClasspathOption = "-bootclasspath"

  def abs(files: Seq[Path]): List[String] = files.toList.map(_.toAbsolutePath.toString)

  def abs(files: Set[Path]): List[String] = abs(files.toList)

  def absString(files: Seq[Path]): String =
    abs(files).mkString(java.io.File.pathSeparator)

  def absString(files: Set[Path]): String = absString(files.toList)

  def absString(files: Array[Path]): String = absString(files.toList)

  def outputOption(output: Output): Seq[String] = {
    /* Oracle Javac doesn't support multiple output directories
     * However, we use multiple output directories in case the
     * user provides their own Javac compiler that can indeed
     * make use of it (e.g. the Eclipse compiler does this via EJC).
     * See https://github.com/sbt/zinc/issues/163. */
    val target = output match {
      case so: SingleOutput  => Some(so.getOutputDirectoryAsPath)
      case _: MultipleOutput => None
    }
    outputOption(target)
  }

  def outputOption(outputDirectory: Option[Path]): Seq[String] = {
    outputDirectory
      .map(output => List("-d", output.toAbsolutePath.toString))
      .getOrElse(Nil)
  }
}
