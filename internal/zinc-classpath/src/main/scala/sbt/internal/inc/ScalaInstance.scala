/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File
import xsbti.ArtifactInfo.ScalaOrganization
import sbt.io.IO

/**
 * A Scala instance encapsulates all the information that is bound to a concrete
 * Scala version, like the [[java.lang.ClassLoader loader]] or all the JARs
 * required for Scala compilation: library jar, compiler jar and others.
 *
 *
 * Both a `ClassLoader` and the jars are required because the compiler's
 * boot classpath requires the location of the library and compiler jar
 * on the classpath to compile any Scala program and macros.
 *
 * @param version Version used to obtain the Scala compiled classes.
 * @param loader Class loader used to load the Scala classes.
 * @param libraryJar Classpath entry that stores the Scala library classes.
 * @param compilerJar Classpath entry that stores the Scala compiler classes.
 * @param allJars Classpath entries for the `loader`.
 * @param explicitActual Classpath entry that stores the Scala compiler classes.
 *
 * @note A jar can actually be any valid classpath entry, not just a jar file.
 */
final class ScalaInstance(
  val version: String,
  val loader: ClassLoader,
  val libraryJar: File,
  val compilerJar: File,
  val allJars: Array[File],
  val explicitActual: Option[String]
) extends xsbti.compile.ScalaInstance {

  /**
   * Check whether `scalaInstance` comes from a managed (i.e. ivy-resolved)
   * scala **or** if it's a free-floating `ScalaInstance`, in which case we
   * need to do tricks in the classpaths because it won't be on them.
   */
  def isManagedVersion = explicitActual.isDefined

  def otherJars = (allJars.toSet - libraryJar - compilerJar).toArray

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
    val other = otherJars.mkString(", ")
    s"""library jar: $libraryJar, compiler jar: $compilerJar, other jars: $other"""
  }

  override def toString: String =
    s"Scala instance { version label $version, actual version $actualVersion, $jarStrings }"
}
object ScalaInstance {

  /** Name of scala organisation to be used for artifact resolution. */
  val ScalaOrg = ScalaOrganization

  /** The prefix being used for Scala artifacts name creation. */
  val VersionPrefix = "version "

  /**
   * Distinguish Dotty and Scala version given the version number.
   * FIXME: This implementation assumes that dotty will be `0.x` for some time.
   */
  def isDotty(version: String): Boolean = version.startsWith("0.")

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
    val loader = provider.loader
    val libraryJar = findOrCrash(jars, "scala-library.jar")
    val compilerJar = findOrCrash(jars, "scala-compiler.jar")
    new ScalaInstance(version, loader, libraryJar, compilerJar, jars, None)
  }

  def apply(scalaHome: File, launcher: xsbti.Launcher): ScalaInstance =
    apply(scalaHome)(scalaLoader(launcher))

  def apply(scalaHome: File)(classLoader: List[File] => ClassLoader): ScalaInstance = {
    val all = allJars(scalaHome)
    val loader = classLoader(all.toList)
    val library = libraryJar(scalaHome)
    val version = actualVersion(loader)(" (library jar  " + library.getAbsolutePath + ")")
    val compiler = compilerJar(scalaHome)
    new ScalaInstance(version, loader, library, compiler, all.toArray, None)
  }

  def apply(version: String, scalaHome: File, launcher: xsbti.Launcher): ScalaInstance = {
    val library = libraryJar(scalaHome)
    val compiler = compilerJar(scalaHome)
    val jars = allJars(scalaHome)
    apply(version, library, compiler, jars: _*)(scalaLoader(launcher))
  }

  /* *******************************************************************
       The following apply methods are deprecated but used internally.
     ******************************************************************* */

  private def apply(
    version: String,
    libraryJar: File,
    compilerJar: File,
    extraJars: File*
  )(classLoader: List[File] => ClassLoader): ScalaInstance =
    apply(version, None, libraryJar, compilerJar, extraJars: _*)(classLoader)

  private def apply(
    version: String,
    explicitActual: Option[String],
    libraryJar: File,
    compilerJar: File,
    extraJars: File*
  )(classLoader: List[File] => ClassLoader): ScalaInstance = {
    val loader = classLoader(libraryJar :: compilerJar :: extraJars.toList)
    new ScalaInstance(
      version, loader, libraryJar, compilerJar, extraJars.toArray, explicitActual
    )
  }

  /** Return all the required Scala jars from a path `scalaHome`. */
  def allJars(scalaHome: File): Seq[File] =
    IO.listFiles(scalaLib(scalaHome)).filter(f => !blacklist(f.getName))

  private[this] def scalaLib(scalaHome: File): File =
    new File(scalaHome, "lib")

  private[this] val blacklist: Set[String] = Set(
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
  private def compilerJar(scalaHome: File) =
    scalaJar(scalaHome, "scala-compiler.jar")
  private def libraryJar(scalaHome: File) =
    scalaJar(scalaHome, "scala-library.jar")

  /** Gets the version of Scala in the compiler.properties file from the loader.*/
  private def actualVersion(scalaLoader: ClassLoader)(label: String) = {
    try fastActualVersion(scalaLoader)
    catch { case e: Exception => slowActualVersion(scalaLoader)(label) }
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

  private def scalaLoader(launcher: xsbti.Launcher): Seq[File] => ClassLoader = {
    jars =>
      import java.net.{ URL, URLClassLoader }
      new URLClassLoader(
        jars.map(_.toURI.toURL).toArray[URL],
        launcher.topLoader
      )
  }
}

/** Runtime exception representing a failure when finding a `ScalaInstance`. */
class InvalidScalaInstance(message: String, cause: Throwable)
  extends RuntimeException(message, cause)

/** Runtime exception representing a failure when finding a `ScalaProvider`. */
class InvalidScalaProvider(message: String) extends RuntimeException(message)
