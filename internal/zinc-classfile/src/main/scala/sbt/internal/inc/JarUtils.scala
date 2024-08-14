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

package sbt.internal.inc

import sbt.io.IO
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

import sbt.io.syntax.URL
import xsbti.compile.{ Output, SingleOutput }
import java.nio.file.{ Files, Path, Paths }

/**
 * This is a utility class that provides a set of functions that
 * are used to implement straight to jar compilation.
 *
 * xsbt.JarUtils is a class that has similar purpose and
 *  duplicates some of the code, as it is difficult to share it.
 */
object JarUtils {

  /** Represents a path to a class file located inside a jar, relative to this jar */
  type ClassFilePath = String

  /** `ClassInJar` is an identifier for a class located inside a jar.
   * For plain class files it is enough to simply use the actual file
   * system path. A class in a jar is identified as a path to the jar
   * and path to the class within that jar (`RelClass`). Those two values
   * are held in one string separated by `!`. Slashes in both
   * paths are consistent with `File.separatorChar` as the actual
   * string is usually kept in `File` object.
   *
   * As an example: given a jar file "C:\develop\zinc\target\output.jar"
   * and a relative path to the class "sbt/internal/inc/Compile.class"
   * The resulting identifier would be:
   * "C:\develop\zinc\target\output.jar!sbt\internal\inc\Compile.class"
   */
  class ClassInJar(override val toString: String) extends AnyVal {
    def toClassFilePath: Option[ClassFilePath] = Option(toClassFilePathOrNull)
    def toClassFilePathOrNull: ClassFilePath = {
      val idx = toString.indexOf('!')
      if (idx < 0) null
      else toClassFilePath(idx)
    }
    def splitJarReference: (File, Option[ClassFilePath]) = {
      val idx = toString.indexOf('!')
      if (idx < 0) {
        (new File(toString), None)
      } else {
        (new File(toString.substring(0, idx)), Some(toClassFilePath(idx)))
      }
    }
    private def toClassFilePath(idx: Int): String = {
      // ClassInJar stores RelClass part with File.separatorChar, however actual paths in zips always use '/'
      toString.substring(idx + 1).replace('\\', '/')
    }

    /**
     * Wraps the string value inside a java.io.File object.
     * File is needed to e.g. be compatible with `xsbti.compile.analysis.ReadStamps` interface.
     */
    def toFile: File = new File(toString)

    def toPath: Path = Paths.get(toString)
  }

  object ClassInJar {

    private val forwardSlash = File.separatorChar == '/'

    /**
     * The base constructor for `ClassInJar`
     *
     * @param jar the jar file
     * @param cls the relative path to class within the jar
     * @return a proper ClassInJar identified by given jar and path to class
     */
    def apply(jar: Path, cls: ClassFilePath): ClassInJar = {
      // This identifier will be stored as a java.io.File. Its constructor will normalize slashes
      // which means that the identifier to be consistent should at all points have consistent
      // slashes for safe comparisons, especially in sets or maps.
      val classFilePath = if (forwardSlash) cls else cls.replace('/', File.separatorChar)
      new ClassInJar(s"$jar!$classFilePath")
    }

    /**
     * Converts an URL to a class in a jar to `ClassInJar`. The method is rather trivial
     * as it also takes precomputed path to the jar that it logically should extract itself.
     * However as it is computed at the callsite anyway, to avoid recomputation it is passed
     * as a parameter/
     *
     * As an example, given a URL:
     * "jar:file:///C:/develop/zinc/target/output.jar!/sbt/internal/inc/Compile.class"
     * and a file: "C:\develop\zinc\target\output.jar"
     * it will create a `ClassInJar` represented as:
     * "C:\develop\zinc\target\output.jar!sbt\internal\inc\Compile.class"
     *
     * @param url url to a class inside a jar
     * @param jar a jar file where the class is located in
     * @return the class inside a jar represented as `ClassInJar`
     */
    def fromURL(url: URL, jar: Path): ClassInJar = {
      val path = url.getPath
      if (!path.contains("!/")) sys.error(s"unexpected URL $url that does not include '!/'")
      else {
        val Array(_, cls) = url.getPath.split("!/")
        apply(jar, cls)
      }
    }

    /** Initialized `ClassInJar` based on its serialized value stored inside a file */
    def fromFile(f: File): ClassInJar = new ClassInJar(f.toString)

    def fromPath(p: Path): ClassInJar = new ClassInJar(p.toString)
  }

  /**
   * Options that have to be specified when running scalac in order
   * for Straight to Jar to work properly.
   *
   * -YdisableFlatCpCaching is needed to disable caching the output jar
   * that changes between compilation runs (incremental compilation cycles).
   * Caching may hide those changes and lead into incorrect results.
   */
  val scalacOptions: Set[ClassFilePath] = Set("-YdisableFlatCpCaching")

  /**
   * Options that have to be specified when running javac in order
   * for Straight to Jar to work properly.
   *
   * -XDuseOptimizedZip=false holds jars open that causes problems
   * with locks on Windows.
   */
  val javacOptions: Set[ClassFilePath] = Set("-XDuseOptimizedZip=false")

  /** Reads current index of a jar file to allow restoring it later with `unstashIndex` */
  def stashIndex(jar: Path): IndexBasedZipFsOps.CentralDir = {
    IndexBasedZipFsOps.readCentralDir(jar.toFile)
  }

  /** Replaces index in given jar file with specified one */
  def unstashIndex(jar: Path, index: IndexBasedZipFsOps.CentralDir): Unit = {
    IndexBasedZipFsOps.writeCentralDir(jar.toFile, index)
  }

  /**
   * Adds plain files to specified jar file. See [[sbt.internal.inc.IndexBasedZipOps#includeInArchive]] for details.
   */
  def includeInJar(jar: File, files: Seq[(File, ClassFilePath)]): Unit = {
    IndexBasedZipFsOps.includeInArchive(jar, files.toVector)
  }

  /**
   * Merges contents of two jars. See sbt.internal.inc.IndexBasedZipOps#mergeArchives for details.
   */
  def mergeJars(into: File, from: File): Unit = {
    IndexBasedZipFsOps.mergeArchives(into, from)
  }

  /** Lists class file entries in jar e.g. sbt/internal/inc/JarUtils.class */
  def listClassFiles(jar: File): Seq[String] = {
    IndexBasedZipFsOps.listEntries(jar).filter(_.endsWith(".class"))
  }

  /** Lists file entries in jar e.g. sbt/internal/inc/JarUtils.class */
  def listFiles(jar: Path): Seq[String] = IndexBasedZipFsOps.listEntries(jar.toFile)

  /**
   * Removes specified entries from a jar file.
   */
  def removeFromJar(jarFile: Path, classes: Iterable[ClassFilePath]): Unit = {
    if (Files.exists(jarFile)) {
      IndexBasedZipFsOps.removeEntries(jarFile.toFile, classes)
    }
  }

  /**
   * Reads all timestamps from given jar file. Returns a function that
   * allows to access them by `ClassInJar` wrapped in `File`.
   */
  def readStamps(jar: Path): Path => Long = {
    val stamps = new IndexBasedZipFsOps.CachedStamps(jar)
    file =>
      val u = file.toUri.toURL
      stamps.getStamp(ClassInJar.fromURL(u, jar).toClassFilePath.get)
  }

  /**
   * Runs the compilation with previous jar if required.
   *
   * When compiling directly to a jar, scalac will produce
   * a jar file, if one exists it will be overwritten rather
   * than updated. For sake of incremental compilation it
   * is required to merge the output from previous compilation(s)
   * and the current one. To make it work, the jar output from
   * previous compilation is stored aside (renamed) to avoid
   * overwrite. The compilation is run normally to the specified
   * output jar. The produced output jar is then merged with
   * jar from previous compilation(s).
   *
   * Classes from previous jar need to be available for the current
   * compiler run - they need to be added to the classpath. This is
   * implemented by taking a function that given additional classpath
   * runs the compilation.
   *
   * If compilation fails, it does not produce a jar, the previous jar
   * is simply reverted (moved to output jar path).
   *
   * If the previous output does not exist or the output is not a jar
   * at all (JarUtils feature is disabled) this function runs a normal
   * compilation.
   *
   * @param output output for scalac compilation
   * @param callback analysis callback used to set previus jar
   * @param compile function that given extra classpath for compiler runs the compilation
   */
  def withPreviousJar(output: Output)(compile: /*extra classpath: */ Seq[Path] => Unit): Unit = {
    preparePreviousJar(output) match {
      case Some((prevJar, outputJar)) =>
        try {
          compile(Seq(prevJar.toPath))
        } catch {
          case e: Exception =>
            IO.move(prevJar, outputJar)
            throw e
        }
        cleanupPreviousJar(prevJar, outputJar)
      case None =>
        compile(Nil)
        createOutputJarIfMissing(output)
    }
  }

  /**
   * If compilation to jar is enabled and previous jar existed
   * will prepare the prev jar, i.e. move the existing output
   * to temporary location. It will return tuple of the path
   * to moved prev jar and path to output jar.
   * The returned prev jar file should be added to the classpath
   * of the compiler.
   */
  def preparePreviousJar(output: Output): Option[(File, File)] = {
    getOutputJar(output)
      .filter(Files.exists(_))
      .map { outputJar =>
        val prevJar = createPrevJarPath()
        val out = outputJar.toFile
        IO.move(out, prevJar)
        (prevJar, out)
      }
  }

  /**
   * Performs cleanup after successful compilation that involved
   * previous jar. It merges the previous jar with the new output
   * and puts the merged file back into output jar path.
   * */
  def cleanupPreviousJar(prevJar: File, outputJar: File): Unit = {
    if (outputJar.exists()) {
      JarUtils.mergeJars(into = prevJar, from = outputJar)
    }
    IO.move(prevJar, outputJar)
  }

  private var tempDir: Path = _

  /**
   * Ensures that temporary directory exists.
   *
   * @param temporaryClassesDirectory path to temporary directory for classes.
   *                                  If not specified, a default will be used.
   */
  def setupTempClassesDir(temporaryClassesDirectory: Option[Path]): Unit = {
    temporaryClassesDirectory match {
      case Some(dir) =>
        Files.createDirectories(dir)
        tempDir = dir
      case None =>
        tempDir = (new File(IO.temporaryDirectory, "zinc_temp_classes_dir")).toPath
    }
  }

  private def createPrevJarPath(): File = {
    val prevJarName = s"$prevJarPrefix-${UUID.randomUUID()}.jar"
    tempDir.resolve(prevJarName).toFile
  }

  val prevJarPrefix: String = "prev-jar"

  /** Checks if given file stores a ClassInJar */
  def isClassInJar(file: File): Boolean = {
    file.toString.split("!") match {
      case Array(jar, _) => jar.endsWith(".jar")
      case _             => false
    }
  }

  /**
   * Return JAR component of class-in-jar notation.
   */
  def getJarInClassInJar(path: Path): Option[Path] = {
    path.toString.split("!") match {
      case Array(jar, _) if jar.endsWith(".jar") => Some(Paths.get(jar))
      case _                                     => None
    }
  }

  /**
   * Determines if Straight to Jar compilations is enabled
   * by inspecting if compilation output is a jar file
   */
  def isCompilingToJar(output: Output): Boolean = {
    getOutputJar(output).isDefined
  }

  /** Extracts a jar file from the output if it is set to be a single jar. */
  def getOutputJar(output: Output): Option[Path] = {
    output match {
      case s: SingleOutput =>
        Some(s.getOutputDirectoryAsPath).filter(_.toString.endsWith(".jar"))
      case _ => None
    }
  }

  def createOutputJarIfMissing(output: Output): Unit =
    getOutputJar(output).filter(!Files.exists(_)).foreach { jar =>
      val outputStream = new ZipOutputStream(new FileOutputStream(jar.toFile))
      outputStream.close()
    }

  /**
   * As some javac implementations do not support compiling directly to jar it is
   * required to change its output to a directory that is temporary, as after compilation
   * the plain classes are put into a zip file and merged with the output jar.
   *
   * This method returns path to this directory based on output jar. The result
   * of this method has to be deterministic as it is called from different places
   * independently.
   */
  def javacTempOutput(outputJar: Path): Path = {
    val outJarName = outputJar.getFileName.toString
    val outDirName = outJarName + "-javac-output"
    outputJar.resolveSibling(outDirName)
  }

  /**
   * The returned `OutputJarContent` object provides access
   * to current content of output jar.
   * It is prepared to be accessed from zinc's custom compiler
   * phases. With some assumptions on how it works the content
   * can be cached and read only when necessary.
   *
   * Implementation details:
   * The content has to be `reset` before each zinc run. This
   * sets the output and reads the current contents of output
   * jar if it exists. The next reading will be necessary
   * in xsbt-analyzer phase which is after jvm phase, so the
   * jar with new contents will appear. To figure out that it
   * is in that place, a call to `dependencyPhaseCompleted` is
   * expected. As content is not accessed between dependency
   * and analysis phases, we can be sure that we are after jvm.
   * The contents of jar will not change until next scalac run
   * (except for javac case) so we should not read the content.
   * This is ensured by a call to `scalacRunCompleted` method.
   * After scalac run, it is possible that javac will run, and
   * its output will be added to the output jar. To have consistent
   * state, after adding those classes `addClasses` should be called.
   * Thanks to this we know what is the content of prev jar during
   * the compilation, without the need to actually read it.
   * The completion of next dependency phase will trigger reading
   * the output jar again. Note that at the point of reading we
   * have both prev jar and new output jar with just compiled classes
   * so the contents of those (currently stored and just read) have
   * bo be combined. Last thing to do is track class deletions
   * while pruning between iterations, which is done through
   * `removeClasses` method.
   */
  def createOutputJarContent(output: Output): OutputJarContent = {
    getOutputJar(output) match {
      case Some(jar) => new ValidOutputJarContent(jar)
      case None      => NoOutputJar
    }
  }

  sealed abstract class OutputJarContent {
    def dependencyPhaseCompleted(): Unit
    def scalacRunCompleted(): Unit
    def addClasses(classes: Set[ClassFilePath]): Unit
    def removeClasses(classes: Set[ClassFilePath]): Unit
    def get(): Set[ClassFilePath]
  }

  private object NoOutputJar extends OutputJarContent {
    def dependencyPhaseCompleted(): Unit = ()
    def scalacRunCompleted(): Unit = ()
    def addClasses(classes: Set[ClassFilePath]): Unit = ()
    def removeClasses(classes: Set[ClassFilePath]): Unit = ()
    def get(): Set[ClassFilePath] = Set.empty
  }

  private class ValidOutputJarContent(outputJar: Path) extends OutputJarContent {
    private var content: Set[ClassFilePath] = Set.empty
    private var shouldReadJar: Boolean = false

    update()

    def dependencyPhaseCompleted(): Unit = {
      shouldReadJar = true
    }

    def scalacRunCompleted(): Unit = {
      shouldReadJar = false
    }

    def addClasses(classes: Set[ClassFilePath]): Unit = {
      content ++= classes
    }

    def removeClasses(classes: Set[ClassFilePath]): Unit = {
      content --= classes
    }

    def get(): Set[ClassFilePath] = {
      if (shouldReadJar) update()
      shouldReadJar = false
      content
    }

    private def update(): Unit = {
      if (Files.exists(outputJar)) {
        content ++= JarUtils.listClassFiles(outputJar.toFile).toSet
      }
    }
  }

  /* Methods below are only used for test code. They are not optimized for performance. */
  /** Reads timestamp of given jared class */
  def readModifiedTime(jc: ClassInJar): Long = {
    val (jar, cls) = jc.splitJarReference
    if (jar.exists()) {
      withZipFile(jar) { zip =>
        Option(zip.getEntry(cls.get)).map(_.getLastModifiedTime.toMillis).getOrElse(0)
      }
    } else 0
  }

  /** Checks if given jared class exists */
  def exists(jc: ClassInJar): Boolean = {
    val (jar, clsOpt) = jc.splitJarReference
    jar.exists() &&
    (clsOpt match {
      case Some(cls) => withZipFile(jar)(zip => zip.getEntry(cls) != null)
      case _         => true
    })
  }

  private def withZipFile[A](zip: File)(f: ZipFile => A): A = {
    val file = new ZipFile(zip)
    try f(file)
    finally file.close()
  }
}
