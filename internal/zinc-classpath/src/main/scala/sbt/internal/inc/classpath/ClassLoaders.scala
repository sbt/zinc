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
package classpath

import java.nio.file.{ Files, Path, StandardCopyOption }
import java.net.{ URL, URLClassLoader }
import annotation.tailrec

/**
 * This is a starting point for defining a custom ClassLoader.  Override 'doLoadClass' to define
 * loading a class that has not yet been loaded.
 */
abstract class LoaderBase(urls: Seq[URL], parent: ClassLoader)
    extends URLClassLoader(urls.toArray, parent) {
  require(parent != null) // included because a null parent is legitimate in Java
  @throws(classOf[ClassNotFoundException])
  override final def loadClass(className: String, resolve: Boolean): Class[_] = {
    val loaded = findLoadedClass(className)
    val found =
      if (loaded == null)
        doLoadClass(className)
      else
        loaded

    if (resolve)
      resolveClass(found)
    found
  }

  /** Provides the implementation of finding a class that has not yet been loaded. */
  protected def doLoadClass(className: String): Class[_]

  /** Provides access to the default implementation of 'loadClass'. */
  protected final def defaultLoadClass(className: String): Class[_] =
    super.loadClass(className, false)
}

/** Searches self first before delegating to the parent. */
final class SelfFirstLoader(classpath: Seq[URL], parent: ClassLoader)
    extends LoaderBase(classpath, parent) {
  @throws(classOf[ClassNotFoundException])
  override final def doLoadClass(className: String): Class[_] = {
    try {
      findClass(className)
    } catch {
      case _: ClassNotFoundException => defaultLoadClass(className)
    }
  }
}

/** Doesn't load any classes itself, but instead verifies that all classes loaded through `parent` either come from `root` or `classpath`. */
final class ClasspathFilter(parent: ClassLoader, root: ClassLoader, classpath: Set[Path])
    extends ClassLoader(parent) {

  def close(): Unit = {
    parent match {
      case ucl: URLClassLoader => ucl.close()
      case _                   => ()
    }
  }

  override def toString =
    s"""|ClasspathFilter(
        |  parent = $parent
        |  root = $root
        |  cp = $classpath
        |)""".stripMargin

  private[this] val directories: Seq[Path] = classpath.toSeq.filter { p =>
    !p.toString.endsWith(".jar") && Files.isDirectory(p)
  }
  override def loadClass(className: String, resolve: Boolean): Class[_] = {
    val c = super.loadClass(className, resolve)
    if (includeLoader(c.getClassLoader, root) || fromClasspath(c))
      c
    else
      throw new ClassNotFoundException(className)
  }
  private[this] def fromClasspath(c: Class[_]): Boolean = {
    val codeSource = c.getProtectionDomain.getCodeSource
    (codeSource eq null) ||
    onClasspath(codeSource.getLocation)
  }
  private[this] def onClasspath(src: URL): Boolean =
    (src eq null) || (
      ClasspathUtil.asFile(src).headOption match {
        case Some(f) =>
          classpath(f) || directories.exists(dir => ClasspathUtil.relativize(dir, f).isDefined)
        case None => false
      }
    )

  override def getResource(name: String): URL = {
    val u = super.getResource(name)
    if (onClasspath(u)) u else null
  }

  override def getResources(name: String): java.util.Enumeration[URL] = {
    import scala.collection.JavaConverters._
    val us = super.getResources(name)
    if (us ne null) us.asScala.filter(onClasspath).asJavaEnumeration else null
  }

  @tailrec private[this] def includeLoader(c: ClassLoader, base: ClassLoader): Boolean =
    (base ne null) &&
      (c ne null) &&
      ((c eq base) || includeLoader(c.getParent, base))
}

/**
 * Delegates class loading to `parent` for all classes included by `filter`.  An attempt to load classes excluded by `filter`
 * results in a `ClassNotFoundException`.
 */
final class FilteredLoader(parent: ClassLoader, filter: ClassFilter) extends ClassLoader(parent) {
  require(parent != null) // included because a null parent is legitimate in Java
  def this(parent: ClassLoader, excludePackages: Iterable[String]) =
    this(parent, new ExcludePackagesFilter(excludePackages))

  @throws(classOf[ClassNotFoundException])
  override final def loadClass(className: String, resolve: Boolean): Class[_] = {
    if (filter.include(className))
      super.loadClass(className, resolve)
    else
      throw new ClassNotFoundException(className)
  }
}

/** Defines a filter on class names. */
trait ClassFilter {
  def include(className: String): Boolean
}
abstract class PackageFilter(packages: Iterable[String]) extends ClassFilter {
  require(packages.forall(_.endsWith(".")))
  protected final def matches(className: String): Boolean = packages.exists(className.startsWith)
}

/**
 * Excludes class names that begin with one of the packages in `exclude`.
 * Each package name in `packages` must end with a `.`
 */
final class ExcludePackagesFilter(exclude: Iterable[String]) extends PackageFilter(exclude) {
  def include(className: String): Boolean = !matches(className)
}

/**
 * Includes class names that begin with one of the packages in `include`.
 * Each package name in `include` must end with a `.`
 */
final class IncludePackagesFilter(include: Iterable[String]) extends PackageFilter(include) {
  def include(className: String): Boolean = matches(className)
}

/**
 * Configures a [[NativeCopyLoader]].
 * The loader will provide native libraries listed in `explicitLibraries` and on `searchPaths` by copying them to `tempDirectory`.
 * If `tempDirectory` is unique to the class loader, this ensures that the class loader gets a unique path for
 * the native library and avoids the restriction on a native library being loaded by a single class loader.
 */
final class NativeCopyConfig(
    val tempDirectory: Path,
    val explicitLibraries: Seq[Path],
    val searchPaths: Seq[Path]
)

/**
 * Loads native libraries from a temporary location in order to work around the jvm native library uniqueness restriction.
 * See [[NativeCopyConfig]] for configuration details.
 */
trait NativeCopyLoader extends ClassLoader {

  /** Configures this loader.  See [[NativeCopyConfig]] for details. */
  protected val config: NativeCopyConfig
  import config._

  private[this] val mapped = new collection.mutable.HashMap[String, String]

  override protected def findLibrary(name: String): String =
    synchronized { mapped.getOrElseUpdate(name, findLibrary0(name)) }

  private[this] def findLibrary0(name: String): String = {
    val mappedName = System.mapLibraryName(name)
    val explicit = explicitLibraries.toIterator.filter(_.getFileName.toString == mappedName)
    val search = searchPaths.toIterator flatMap relativeLibrary(mappedName)
    val combined = explicit ++ search
    if (combined.hasNext) copy(combined.next) else null
  }
  private[this] def relativeLibrary(mappedName: String)(base: Path): Seq[Path] = {
    val f = base.resolve(mappedName)
    if (Files.isRegularFile(f)) f :: Nil
    else Nil
  }
  private[this] def copy(f: Path): String = {
    val target = tempDirectory.resolve(f.getFileName.toString)
    Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING)
    target.toAbsolutePath.toString
  }
}
