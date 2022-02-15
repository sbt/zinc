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

import java.nio.file.{ Files, Path, Paths }
import java.net.{ URI, URL, URLClassLoader }
import sbt.io.{ IO, PathFinder }
import xsbti.compile.ScalaInstance
import java.io.InputStream
import scala.util.control.Exception.catching

object ClasspathUtil {
  def toLoader(finder: PathFinder): ClassLoader = toLoader(finder, rootLoader)
  def toLoader(finder: PathFinder, parent: ClassLoader): ClassLoader =
    new URLClassLoader(finder.getURLs(), parent)

  def toLoader(paths: Seq[Path]): ClassLoader = toLoader(paths, rootLoader)
  def toLoader(paths: Seq[Path], parent: ClassLoader): ClassLoader =
    new URLClassLoader(toURLs(paths).toArray, parent)

  def toLoader(
      paths: Seq[Path],
      parent: ClassLoader,
      resourceMap: Map[String, String]
  ): ClassLoader =
    new URLClassLoader(toURLs(paths).toArray, parent) with RawResources {
      override def resources = resourceMap
    }

  def toLoader(
      paths: Seq[Path],
      parent: ClassLoader,
      resourceMap: Map[String, String],
      nativeTemp: Path
  ): ClassLoader =
    new URLClassLoader(toURLs(paths).toArray, parent) with RawResources with NativeCopyLoader {
      override def resources = resourceMap
      override val config = new NativeCopyConfig(nativeTemp, paths, javaLibraryPaths)
      override def toString =
        s"""|URLClassLoader with NativeCopyLoader with RawResources(
            |  urls = $paths,
            |  parent = $parent,
            |  resourceMap = ${resourceMap.keySet},
            |  nativeTemp = $nativeTemp
            |)""".stripMargin
    }

  def javaLibraryPaths: Seq[Path] =
    IO.parseClasspath(System.getProperty("java.library.path"))
      .map(_.toPath)

  lazy val rootLoader = {
    def parent(loader: ClassLoader): ClassLoader = {
      val p = loader.getParent
      if (p eq null) loader else parent(p)
    }
    val systemLoader = ClassLoader.getSystemClassLoader
    if (systemLoader ne null) parent(systemLoader)
    else parent(getClass.getClassLoader)
  }
  lazy val xsbtiLoader = classOf[xsbti.Launcher].getClassLoader

  final val AppClassPath = "app.class.path"
  final val BootClassPath = "boot.class.path"

  def createClasspathResources(
      classpath: Seq[Path],
      instance: ScalaInstance
  ): Map[String, String] = {
    createClasspathResources(classpath, instance.libraryJars.map(_.toPath))
  }

  def createClasspathResources(appPaths: Seq[Path], bootPaths: Seq[Path]): Map[String, String] = {
    def make(name: String, paths: Seq[Path]) = name -> makeString(paths)
    Map(make(AppClassPath, appPaths), make(BootClassPath, bootPaths))
  }

  private[sbt] def filterByClasspath(classpath: Seq[Path], loader: ClassLoader): ClassLoader =
    new ClasspathFilter(loader, xsbtiLoader, classpath.toSet)

  /**
   * Creates a ClassLoader that contains the classpath and the scala-library from
   * the given instance.
   */
  def makeLoader(classpath: Seq[Path], instance: ScalaInstance): ClassLoader =
    filterByClasspath(classpath, makeLoader(classpath, instance.loaderLibraryOnly, instance))

  def makeLoader(classpath: Seq[Path], instance: ScalaInstance, nativeTemp: Path): ClassLoader =
    filterByClasspath(
      classpath,
      makeLoader(classpath, instance.loaderLibraryOnly, instance, nativeTemp)
    )

  def makeLoader(classpath: Seq[Path], parent: ClassLoader, instance: ScalaInstance): ClassLoader =
    toLoader(classpath, parent, createClasspathResources(classpath, instance))

  def makeLoader(
      classpath: Seq[Path],
      parent: ClassLoader,
      instance: ScalaInstance,
      nativeTemp: Path
  ): ClassLoader =
    toLoader(classpath, parent, createClasspathResources(classpath, instance), nativeTemp)

  private[sbt] def printSource(c: Class[_]) =
    println(c.getName + " loader=" + c.getClassLoader + " location=" + IO.classLocationPath(c))

  def isArchive(file: Path): Boolean = isArchive(file, contentFallback = false)

  def isArchive(file: Path, contentFallback: Boolean): Boolean =
    Files.isRegularFile(file) && (isArchiveName(
      file.getFileName.toString
    ) || (contentFallback && hasZipContent(
      file
    )))

  def isArchiveName(fileName: String) = fileName.endsWith(".jar") || fileName.endsWith(".zip")

  def hasZipContent(file: Path): Boolean =
    try {
      usingFileInputStream(file) { in =>
        (in.read() == 0x50) &&
        (in.read() == 0x4b) &&
        (in.read() == 0x03) &&
        (in.read() == 0x04)
      }
    } catch { case _: Exception => false }

  private[sbt] def usingFileInputStream[A](file: Path)(f: InputStream => A): A = {
    val st = Files.newInputStream(file)
    try {
      f(st)
    } finally {
      st.close()
    }
  }

  /** Returns all entries in 'classpath' that correspond to a compiler plugin.*/
  private[sbt] def compilerPlugins(classpath: Seq[Path], isDotty: Boolean): Iterable[Path] = {
    import collection.JavaConverters._
    val loader = new URLClassLoader(toURLs(classpath).toArray)
    val metaFile = if (isDotty) "plugin.properties" else "scalac-plugin.xml"
    loader.getResources(metaFile).asScala.toList.flatMap(asFile(true))
  }

  /** Converts the given URL to a File.  If the URL is for an entry in a jar, the File for the jar is returned. */
  private[sbt] def asFile(url: URL): List[Path] = asFile(false)(url)
  private[sbt] def asFile(jarOnly: Boolean)(url: URL): List[Path] = {
    try {
      url.getProtocol match {
        case "file" if !jarOnly =>
          Paths.get(url.toURI) :: Nil
        case "jar" =>
          val path = url.getPath
          val end = path.indexOf('!')
          Paths.get(new URI(if (end == -1) path else path.substring(0, end))) :: Nil
        case _ => Nil
      }
    } catch { case _: Exception => Nil }
  }

  private[sbt] def toURLs(files: Seq[Path]): Seq[URL] =
    files.map(_.toUri.toURL)

  private[sbt] def makeString(paths: Seq[Path]): String =
    makeString(paths, java.io.File.pathSeparator)
  private[sbt] def makeString(paths: Seq[Path], sep: String): String = {
    val separated = paths.map(_.toAbsolutePath.toString)
    separated.find(_ contains sep).foreach(p => sys.error(s"Path '$p' contains separator '$sep'"))
    separated.mkString(sep)
  }

  private[sbt] def relativize(base: Path, file: Path): Option[String] = {
    // "On UNIX systems, a pathname is absolute if its prefix is "/"."
    // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/io/File.html#isAbsolute
    // "This typically involves removing redundant names such as "." and ".." from the pathname, resolving symbolic links (on UNIX platforms)"
    // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/io/File.html#getCanonicalPath()
    // We do not want to use getCanonicalPath because if we resolve the symbolic link, that could change
    // the outcome of copyDirectory's target structure.
    // Path#normailize is able to expand ".." without expanding the symlink.
    // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/nio/file/Path.html#normalize()
    // "Returns a path that is this path with redundant name elements eliminated."
    def toAbsolutePath(x: Path): Path = {
      if (!x.isAbsolute) x.toAbsolutePath
      else x
    }
    val basePath = toAbsolutePath(base).normalize
    val filePath = toAbsolutePath(file).normalize
    if (filePath startsWith basePath) {
      val relativePath =
        catching(classOf[IllegalArgumentException]) opt (basePath relativize filePath)
      relativePath map (_.toString)
    } else None
  }
}

// old File-based implementation kept for compatibility
@deprecated("internal", "1.4.0")
object ClasspathUtilities {
  import java.io.File

  @deprecated("internal", "1.4.0")
  def toLoader(finder: PathFinder): ClassLoader = ClasspathUtil.toLoader(finder)
  @deprecated("internal", "1.4.0")
  def toLoader(finder: PathFinder, parent: ClassLoader): ClassLoader =
    ClasspathUtil.toLoader(finder, parent)
  @deprecated("internal", "1.4.0")
  def toLoader(paths: Seq[File]): ClassLoader =
    ClasspathUtil.toLoader(paths.map(_.toPath), ClasspathUtil.rootLoader)
  @deprecated("internal", "1.4.0")
  def toLoader(paths: Seq[File], parent: ClassLoader): ClassLoader =
    ClasspathUtil.toLoader(paths.map(_.toPath), parent)

  @deprecated("internal", "1.4.0")
  def toLoader(
      paths: Seq[File],
      parent: ClassLoader,
      resourceMap: Map[String, String]
  ): ClassLoader =
    ClasspathUtil.toLoader(paths.map(_.toPath), parent, resourceMap)

  @deprecated("internal", "1.4.0")
  def toLoader(
      paths: Seq[File],
      parent: ClassLoader,
      resourceMap: Map[String, String],
      nativeTemp: File
  ): ClassLoader =
    ClasspathUtil.toLoader(paths.map(_.toPath), parent, resourceMap, nativeTemp.toPath)

  @deprecated("internal", "1.4.0")
  def makeLoader(classpath: Seq[File], instance: ScalaInstance): ClassLoader =
    ClasspathUtil.makeLoader(classpath.map(_.toPath), instance)

  @deprecated("internal", "1.4.0")
  def makeLoader(classpath: Seq[File], instance: ScalaInstance, nativeTemp: File): ClassLoader =
    ClasspathUtil.makeLoader(classpath.map(_.toPath), instance, nativeTemp.toPath)

  @deprecated("internal", "1.4.0")
  def makeLoader(classpath: Seq[File], parent: ClassLoader, instance: ScalaInstance): ClassLoader =
    ClasspathUtil.makeLoader(classpath.map(_.toPath), parent, instance)

  @deprecated("internal", "1.4.0")
  def makeLoader(
      classpath: Seq[File],
      parent: ClassLoader,
      instance: ScalaInstance,
      nativeTemp: File
  ): ClassLoader =
    ClasspathUtil.makeLoader(classpath.map(_.toPath), parent, instance, nativeTemp.toPath)

  // This is used by sbt-assembly
  @deprecated("internal", "1.4.0")
  def isArchive(file: File): Boolean = ClasspathUtil.isArchive(file.toPath)

  @deprecated("internal", "1.4.0")
  def isArchive(file: File, contentFallback: Boolean): Boolean =
    ClasspathUtil.isArchive(file.toPath, contentFallback)

  @deprecated("internal", "1.4.0")
  def isArchiveName(fileName: String) = ClasspathUtil.isArchiveName(fileName)

  @deprecated("internal", "1.4.0")
  def hasZipContent(file: File): Boolean = ClasspathUtil.hasZipContent(file.toPath)

  @deprecated("internal", "1.4.0")
  private[sbt] def filterByClasspath(classpath: Seq[File], loader: ClassLoader): ClassLoader =
    ClasspathUtil.filterByClasspath(classpath.map(_.toPath), loader)

  @deprecated("internal", "1.4.0")
  def javaLibraryPaths: Seq[File] = ClasspathUtil.javaLibraryPaths.map(_.toFile)

  @deprecated("internal", "1.4.0")
  lazy val rootLoader: ClassLoader = ClasspathUtil.rootLoader
  @deprecated("internal", "1.4.0")
  lazy val xsbtiLoader: ClassLoader = ClasspathUtil.xsbtiLoader
  @deprecated("internal", "1.4.0")
  final val AppClassPath = ClasspathUtil.AppClassPath
  @deprecated("internal", "1.4.0")
  final val BootClassPath = ClasspathUtil.BootClassPath

  @deprecated("internal", "1.4.0")
  def createClasspathResources(
      classpath: Seq[File],
      instance: ScalaInstance
  ): Map[String, String] = ClasspathUtil.createClasspathResources(classpath.map(_.toPath), instance)

  @deprecated("internal", "1.4.0")
  def createClasspathResources(appPaths: Seq[File], bootPaths: Seq[File]): Map[String, String] =
    ClasspathUtil.createClasspathResources(appPaths.map(_.toPath), bootPaths.map(_.toPath))

}
