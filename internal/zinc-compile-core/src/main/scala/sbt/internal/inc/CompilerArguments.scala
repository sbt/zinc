/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbti.ArtifactInfo
import scala.util
import java.io.File
import CompilerArguments.{ abs, absString, BootClasspathOption }
import sbt.io.IO
import sbt.io.syntax._

/**
 * Construct the list of compiler arguments that are passed to the Scala
 * compiler based on the current [[xsbti.compile.ScalaInstance]] and the
 * user-defined [[xsbti.compile.ClasspathOptions]].
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
  def apply(
    sources: Seq[File],
    classpath: Seq[File],
    outputDirectory: Option[File],
    options: Seq[String]
  ): Seq[String] = {
    /* Add dummy to avoid Scalac misbehaviour for empty classpath (as of 2.9.1). */
    def dummy: String = "dummy_" + Integer.toHexString(util.Random.nextInt)

    checkScalaHomeUnset()
    val compilerClasspath = finishClasspath(classpath)
    val stringClasspath =
      if (compilerClasspath.isEmpty) dummy
      else absString(compilerClasspath)
    val classpathOption = Seq("-classpath", stringClasspath)
    val outputOption: Seq[String] = outputDirectory
      .map(output => List("-d", output.getAbsolutePath)).getOrElse(Nil)
    val bootClasspath = bootClasspathOption(hasLibrary(classpath))
    options ++ outputOption ++ bootClasspath ++ classpathOption ++ abs(sources)
  }

  /**
   * Finish the classpath by adding extra Scala classpath entries if required.
   *
   * @param classpath The classpath seed to be modified.
   * @return A classpath ready to be passed to the Scala compiler.
   */
  def finishClasspath(classpath: Seq[File]): Seq[File] = {
    val filteredClasspath = filterLibrary(classpath)
    val extraCompiler = include(cpOptions.compiler, scalaInstance.compilerJar)
    val extraClasspath = include(cpOptions.extra, scalaInstance.otherJars(): _*)
    filteredClasspath ++ extraCompiler ++ extraClasspath
  }

  def createBootClasspathFor(classpath: Seq[File]): String =
    createBootClasspath(hasLibrary(classpath) || cpOptions.compiler || cpOptions.extra)

  /**
   * Return the Scala library to the boot classpath if `addLibrary` is true.
   * @param addLibrary Flag to return the Scala library.
   */
  def createBootClasspath(addLibrary: Boolean): String = {
    val originalBoot = System.getProperty("sun.boot.class.path", "")
    if (addLibrary) {
      val newBootPrefix =
        if (originalBoot.isEmpty) ""
        else originalBoot + File.pathSeparator
      newBootPrefix + scalaInstance.libraryJar.getAbsolutePath
    } else originalBoot
  }

  def filterLibrary(classpath: Seq[File]) =
    if (!cpOptions.filterLibrary) classpath
    else classpath.filterNot(isScalaLibrary)

  def hasLibrary(classpath: Seq[File]) = classpath.exists(isScalaLibrary)

  def bootClasspathOption(addLibrary: Boolean): Seq[String] =
    if (!cpOptions.autoBoot) Nil
    else Seq(BootClasspathOption, createBootClasspath(addLibrary))

  def bootClasspath(addLibrary: Boolean): Seq[File] =
    if (!cpOptions.autoBoot) Nil
    else IO.parseClasspath(createBootClasspath(addLibrary))

  def bootClasspathFor(classpath: Seq[File]) =
    bootClasspath(hasLibrary(classpath))

  def extClasspath: Seq[File] =
    (IO.parseClasspath(System.getProperty("java.ext.dirs")) * "*.jar").get

  private[this] def include(flag: Boolean, jars: File*) =
    if (flag || ScalaInstance.isDotty(scalaInstance.version)) jars
    else Nil

  private[this] def abs(files: Seq[File]) =
    files.map(_.getAbsolutePath).sortWith(_ < _)

  private[this] def checkScalaHomeUnset(): Unit = {
    val scalaHome = System.getProperty("scala.home")
    assert(
      (scalaHome eq null) || scalaHome.isEmpty,
      "'scala.home' should not be set (was " + scalaHome + ")"
    )
  }

  private[this] val isScalaLibrary: File => Boolean = file => {
    val name = file.getName
    name.contains(ArtifactInfo.ScalaLibraryID) ||
      name == scalaInstance.libraryJar.getName
  }
}

object CompilerArguments {
  val BootClasspathOption = "-bootclasspath"

  def abs(files: Seq[File]): List[String] = files.toList.map(_.getAbsolutePath)

  def abs(files: Set[File]): List[String] = abs(files.toList)

  def absString(files: Seq[File]): String =
    abs(files).mkString(File.pathSeparator)

  def absString(files: Set[File]): String = absString(files.toSeq)
}
