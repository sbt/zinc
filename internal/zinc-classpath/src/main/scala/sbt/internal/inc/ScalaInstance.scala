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

package sbt
package internal
package inc

import java.io.File
import xsbti.ArtifactInfo.ScalaOrganization
import sbt.io.IO
import scala.language.reflectiveCalls
import sbt.internal.inc.classpath.ClasspathUtil

/**
 * A Scala instance encapsulates all the information that is bound to a concrete
 * Scala version, like the `java.lang.ClassLoader` or all the JARs
 * required for Scala compilation: library jar, compiler jar and others.
 *
 *
 * Both a `ClassLoader` and the jars are required because the compiler's
 * boot classpath requires the location of the library and compiler jar
 * on the classpath to compile any Scala program and macros.
 *
 * @see xsbti.compile.ScalaInstance
 */
final class ScalaInstance(
    val version: String,
    val loader: ClassLoader,
    val loaderCompilerOnly: ClassLoader,
    val loaderLibraryOnly: ClassLoader,
    val libraryJars: Array[File],
    val compilerJars: Array[File],
    val allJars: Array[File],
    val explicitActual: Option[String]
) extends xsbti.compile.ScalaInstance {

  @deprecated("Use constructor with loaderCompilerOnly", "1.5.0")
  def this(
      version: String,
      loader: ClassLoader,
      loaderLibraryOnly: ClassLoader,
      libraryJar: File,
      compilerJar: File,
      allJars: Array[File],
      explicitActual: Option[String]
  ) = {
    this(
      version,
      loader,
      loader,
      loaderLibraryOnly,
      Array(libraryJar),
      compilerJars = allJars,
      allJars = allJars,
      explicitActual
    )
  }

  @deprecated("Use constructor with loaderCompilerOnly", "1.5.0")
  def this(
      version: String,
      loader: ClassLoader,
      loaderLibraryOnly: ClassLoader,
      libraryJars: Array[File],
      compilerJar: File,
      allJars: Array[File],
      explicitActual: Option[String]
  ) = {
    this(
      version,
      loader,
      loader,
      loaderLibraryOnly,
      libraryJars,
      compilerJars = allJars,
      allJars = allJars,
      explicitActual
    )
  }

  @deprecated("Use constructor with loaderLibraryOnly and compilerLibraryOnly", "1.1.2")
  def this(
      version: String,
      loader: ClassLoader,
      libraryJar: File,
      compilerJar: File,
      allJars: Array[File],
      explicitActual: Option[String]
  ) = {
    this(
      version,
      loader,
      loader,
      ClasspathUtil.rootLoader,
      Array(libraryJar),
      compilerJars = allJars,
      allJars,
      explicitActual
    )
  }

  /**
   * Check whether `scalaInstance` comes from a managed (i.e. ivy-resolved)
   * scala **or** if it's a free-floating `ScalaInstance`, in which case we
   * need to do tricks in the classpaths because it won't be on them.
   */
  def isManagedVersion = explicitActual.isDefined

  def otherJars: Array[File] =
    allJars.filterNot(f => compilerJars.contains(f) || libraryJars.contains(f))

  require(
    version.indexOf(' ') < 0,
    "Version cannot contain spaces (was '" + version + "')"
  )

  /**
   * Get version of Scala in the `compiler.properties` file from the loader.
   * This version may be different than the one passed in by `version`.
   */
  lazy val actualVersion: String = {
    explicitActual.getOrElse {
      val label = "\n    version " + version + ", " + jarStrings
      ScalaInstance.actualVersion(loader)(label)
    }
  }

  /** Get the string representation of all the available jars. */
  private def jarStrings: String = {
    val libs = libraryJars.mkString(", ")
    val compiler = compilerJars.mkString(", ")
    val other = otherJars.mkString(", ")
    s"""library jars: $libs, compiler jars: $compiler, other jars: $other"""
  }

  override def toString: String =
    s"Scala instance { version label $version, actual version $actualVersion, $jarStrings }"
}

object ScalaInstance {
  /*
   * Structural extension for the ScalaProvider post 1.0.3 launcher.
   * See https://github.com/sbt/zinc/pull/505.
   */
  private type ScalaProvider2 = { def loaderLibraryOnly: ClassLoader }

  /** Name of scala organisation to be used for artifact resolution. */
  val ScalaOrg = ScalaOrganization

  /** The prefix being used for Scala artifacts name creation. */
  val VersionPrefix = "version "

  /**
   * Distinguish Dotty and Scala 2 version given the version number.
   */
  def isDotty(version: String): Boolean = version.startsWith("0.") || version.startsWith("3.")

  /** Create a [[ScalaInstance]] from a given org, version and launcher. */
  def apply(org: String, version: String, launcher: xsbti.Launcher): ScalaInstance = {
    /* For launcher compatibility, use overload for `ScalaOrg`. */
    if (org == ScalaOrg)
      apply(version, launcher)
    else {
      try {
        apply(version, launcher.getScala(version, "", org))
      } catch {
        case _: NoSuchMethodError =>
          val message =
            """Incompatible version of the `xsbti.Launcher` interface.
              |Use an sbt 0.12+ launcher instead.
            """.stripMargin
          sys.error(message)
      }
    }
  }

  /** Creates a ScalaInstance using the given provider to obtain the jars and loader. */
  def apply(version: String, launcher: xsbti.Launcher): ScalaInstance =
    apply(version, launcher.getScala(version))

  /**
   * Create a ScalaInstance from a version and a given provider that
   * defines the location of the jars and the loader to be used.
   */
  def apply(version: String, provider: xsbti.ScalaProvider): ScalaInstance = {
    def findOrCrash(jars: Array[File], jarName: String) = {
      jars.find(_.getName == jarName).getOrElse {
        throw new InvalidScalaProvider(s"Couldn't find '$jarName'")
      }
    }
    val jars = provider.jars
    val libraryJar = findOrCrash(jars, "scala-library.jar")
    def fallbackClassLoaders = {
      val l = ClasspathUtil.toLoader(Vector(libraryJar.toPath))
      val c = scalaLoader(l)(jars.toVector filterNot { _ == libraryJar })
      (c, l)
    }
    // sbt launcher 1.0.3 will construct layered classloader. Use them if we find them.
    // otherwise, construct layered loaders manually.
    val (loader, loaderLibraryOnly) = {
      (try {
        provider match {
          case p: ScalaProvider2 @unchecked => Option((provider.loader, p.loaderLibraryOnly))
        }
      } catch {
        case _: NoSuchMethodException => None
      }) getOrElse fallbackClassLoaders
    }
    new ScalaInstance(
      version,
      loader,
      loader,
      loaderLibraryOnly,
      Array(libraryJar),
      compilerJars = jars,
      jars,
      None
    )
  }

  def apply(scalaHome: File, launcher: xsbti.Launcher): ScalaInstance =
    apply(scalaHome)(scalaLibraryLoader(launcher))

  def apply(scalaHome: File)(classLoader: List[File] => ClassLoader): ScalaInstance = {
    val all = allJars(scalaHome).toArray
    val library = libraryJar(scalaHome)
    val loaderLibraryOnly = classLoader(List(library))
    val loader = scalaLoader(loaderLibraryOnly)(all.toVector filterNot { _ == library })
    val version = actualVersion(loader)(" (library jar  " + library.getAbsolutePath + ")")
    new ScalaInstance(
      version,
      loader,
      loader,
      loaderLibraryOnly,
      Array(library),
      compilerJars = all,
      all,
      None
    )
  }

  def apply(version: String, scalaHome: File, launcher: xsbti.Launcher): ScalaInstance = {
    val all = allJars(scalaHome).toArray
    val library = libraryJar(scalaHome)
    val loaderLibraryOnly = scalaLibraryLoader(launcher)(List(library))
    val loader = scalaLoader(loaderLibraryOnly)(all.toVector)
    new ScalaInstance(
      version,
      loader,
      loader,
      loaderLibraryOnly,
      Array(library),
      compilerJars = all,
      all,
      None
    )
  }

  /** Return all the required Scala jars from a path `scalaHome`. */
  def allJars(scalaHome: File): Seq[File] =
    IO.listFiles(scalaLib(scalaHome)).toIndexedSeq.filter(f => !excludeList(f.getName))

  private[this] def scalaLib(scalaHome: File): File =
    new File(scalaHome, "lib")

  private[this] val excludeList: Set[String] = Set(
    "scala-actors.jar",
    "scalacheck.jar",
    "scala-partest.jar",
    "scala-partest-javaagent.jar",
    "scalap.jar",
    "scala-swing.jar"
  )

  /** Get a scala artifact from a given directory. */
  def scalaJar(scalaHome: File, name: String) =
    new File(scalaLib(scalaHome), name)
  private def libraryJar(scalaHome: File) =
    scalaJar(scalaHome, "scala-library.jar")

  /** Gets the version of Scala in the compiler.properties file from the loader.*/
  private def actualVersion(scalaLoader: ClassLoader)(label: String) = {
    try fastActualVersion(scalaLoader)
    catch { case _: Exception => slowActualVersion(scalaLoader)(label) }
  }

  private def slowActualVersion(scalaLoader: ClassLoader)(label: String) = {
    val scalaVersion = {
      try {
        // Get scala version from the `Properties` file in Scalac
        Class
          .forName("scala.tools.nsc.Properties", true, scalaLoader)
          .getMethod("versionString")
          .invoke(null)
          .toString
      } catch {
        case cause: Exception =>
          val msg = s"Scala instance doesn't exist or is invalid: $label"
          throw new InvalidScalaInstance(msg, cause)
      }
    }

    if (scalaVersion.startsWith(VersionPrefix))
      scalaVersion.substring(VersionPrefix.length)
    else scalaVersion
  }

  private def fastActualVersion(scalaLoader: ClassLoader): String = {
    val stream = scalaLoader.getResourceAsStream("compiler.properties")
    try {
      val props = new java.util.Properties
      props.load(stream)
      props.getProperty("version.number")
    } finally stream.close()
  }

  private def scalaLibraryLoader(launcher: xsbti.Launcher): Seq[File] => ClassLoader = { jars =>
    ClasspathUtil.toLoader(jars.map(_.toPath), launcher.topLoader)
  }

  private def scalaLoader(parent: ClassLoader): Seq[File] => ClassLoader = { jars =>
    ClasspathUtil.toLoader(jars.map(_.toPath), parent)
  }
}

/** Runtime exception representing a failure when finding a `ScalaInstance`. */
class InvalidScalaInstance(message: String, cause: Throwable)
    extends RuntimeException(message, cause)

/** Runtime exception representing a failure when finding a `ScalaProvider`. */
class InvalidScalaProvider(message: String) extends RuntimeException(message)
