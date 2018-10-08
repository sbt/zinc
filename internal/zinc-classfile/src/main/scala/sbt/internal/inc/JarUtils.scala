package sbt.internal.inc

import sbt.io.IO
import java.util.zip.ZipFile
import java.io.File
import java.util.UUID

import sbt.io.syntax.URL
import xsbti.compile.{ Output, SingleOutput }

/**
 * This is a utility class that provides a set of functions that
 * are used to implement straight to jar compilation.
 *
 *  [[xsbt.JarUtils]] is a class that has similar purpose and
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
    def toClassFilePath: ClassFilePath = splitJarReference._2
    def splitJarReference: (File, ClassFilePath) = {
      val Array(jar, cls) = toString.split("!")
      // ClassInJar stores RelClass part with File.separatorChar, however actual paths in zips always use '/'
      val classFilePath = cls.replace('\\', '/')
      (new File(jar), classFilePath)
    }

    /**
     * Wraps the string value inside a [[java.io.File]] object.
     * File is needed to e.g. be compatible with [[xsbti.compile.analysis.ReadStamps]] interface.
     */
    def toFile: File = new File(toString)

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
    def apply(jar: File, cls: ClassFilePath): ClassInJar = {
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
    def fromURL(url: URL, jar: File): ClassInJar = {
      val Array(_, cls) = url.getPath.split("!/")
      apply(jar, cls)
    }

    /** Initialized `ClassInJar` based on its serialized value stored inside a file */
    def fromFile(f: File): ClassInJar = new ClassInJar(f.toString)
  }

  /**
   * Options that have to be specified when running scalac in order
   * for Straight to Jar to work properly.
   *
   * -YdisableFlatCpCaching is needed to disable caching the output jar
   * that changes between compilation runs (incremental compilation cycles).
   * Caching may hide those changes and lead into incorrect results.
   */
  val scalacOptions = Set("-YdisableFlatCpCaching")

  /**
   * Options that have to be specified when running javac in order
   * for Straight to Jar to work properly.
   *
   * -XDuseOptimizedZip=false holds jars open that causes problems
   * with locks on Windows.
   */
  val javacOptions = Set("-XDuseOptimizedZip=false")

  /** Reads current index of a jar file to allow restoring it later with `unstashIndex` */
  def stashIndex(jar: File): IndexBasedZipFsOps.CentralDir = {
    IndexBasedZipFsOps.readCentralDir(jar)
  }

  /** Replaces index in given jar file with specified one */
  def unstashIndex(jar: File, index: IndexBasedZipFsOps.CentralDir): Unit = {
    IndexBasedZipFsOps.writeCentralDir(jar, index)
  }

  /**
   * Adds plain files to specified jar file. See [[sbt.internal.inc.IndexBasedZipOps#includeInArchive]] for details.
   */
  def includeInJar(jar: File, files: Seq[(File, ClassFilePath)]): Unit = {
    IndexBasedZipFsOps.includeInArchive(jar, files)
  }

  /**
   * Merges contents of two jars. See [[sbt.internal.inc.IndexBasedZipOps#mergeArchives]] for details.
   */
  def mergeJars(into: File, from: File): Unit = {
    IndexBasedZipFsOps.mergeArchives(into, from)
  }

  /** Lists class file entries in jar e.g. sbt/internal/inc/JarUtils.class */
  def listClassFiles(jar: File): Seq[String] = {
    IndexBasedZipFsOps.listEntries(jar).filter(_.endsWith(".class"))
  }

  /**
   * Removes specified entries from a jar file.
   */
  def removeFromJar(jarFile: File, classes: Iterable[ClassFilePath]): Unit = {
    if (jarFile.exists()) {
      IndexBasedZipFsOps.removeEntries(jarFile, classes)
    }
  }

  /**
   * Reads all timestamps from given jar file. Returns a function that
   * allows to access them by `ClassInJar` wrapped in `File`.
   */
  def readStamps(jar: File): File => Long = {
    val stamps = new IndexBasedZipFsOps.CachedStamps(jar)
    file =>
      stamps.getStamp(ClassInJar.fromFile(file).toClassFilePath)
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
  def withPreviousJar(output: Output)(compile: /*extra classpath: */ Seq[File] => Unit): Unit = {
    preparePreviousJar(output) match {
      case Some((prevJar, outputJar)) =>
        try {
          compile(Seq(prevJar))
        } catch {
          case e: Exception =>
            IO.move(prevJar, outputJar)
            throw e
        }
        cleanupPreviousJar(prevJar, outputJar)
      case None =>
        compile(Nil)
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
      .filter(_.exists())
      .map { outputJar =>
        val prevJar = createPrevJarPath()
        IO.move(outputJar, prevJar)
        (prevJar, outputJar)
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

  private var tempDir: File = _

  /**
   * Ensures that temporary directory exists.
   *
   * @param temporaryClassesDirectory path to temporary directory for classes.
   *                                  If not specified, a default will be used.
   */
  def setupTempClassesDir(temporaryClassesDirectory: Option[File]): Unit = {
    temporaryClassesDirectory match {
      case Some(dir) =>
        IO.createDirectory(dir)
        tempDir = dir
      case None =>
        tempDir = new File(IO.temporaryDirectory, "zinc_temp_classes_dir")
    }
  }

  private def createPrevJarPath(): File = {
    val prevJarName = s"$prevJarPrefix-${UUID.randomUUID()}.jar"
    tempDir.toPath.resolve(prevJarName).toFile
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
   * Determines if Straight to Jar compilations is enabled
   * by inspecting if compilation output is a jar file
   */
  def isCompilingToJar(output: Output): Boolean = {
    getOutputJar(output).isDefined
  }

  /** Extracts a jar file from the output if it is set to be a single jar. */
  def getOutputJar(output: Output): Option[File] = {
    output match {
      case s: SingleOutput =>
        Some(s.getOutputDirectory).filter(_.getName.endsWith(".jar"))
      case _ => None
    }
  }

  /**
   * As javac does not support compiling directly to jar it is required to
   * change its output to a directory that is temporary, as after compilation
   * the plain classes are put into a zip file and merged with the output jar.
   *
   * This method returns path to this directory based on output jar. The result
   * of this method has to be deterministic as it is called from different places
   * independently.
   */
  def javacTempOutput(outputJar: File): File = {
    val outJarName = outputJar.getName
    val outDirName = outJarName + "-javac-output"
    outputJar.toPath.resolveSibling(outDirName).toFile
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

  private class ValidOutputJarContent(outputJar: File) extends OutputJarContent {
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
      if (outputJar.exists()) {
        content ++= JarUtils.listClassFiles(outputJar).toSet
      }
    }
  }

  /* Methods below are only used for test code. They are not optimized for performance. */
  /** Reads timestamp of given jared class */
  def readModifiedTime(jc: ClassInJar): Long = {
    val (jar, cls) = jc.splitJarReference
    if (jar.exists()) {
      withZipFile(jar) { zip =>
        Option(zip.getEntry(cls)).map(_.getLastModifiedTime.toMillis).getOrElse(0)
      }
    } else 0
  }

  /** Checks if given jared class exists */
  def exists(jc: ClassInJar): Boolean = {
    val (jar, cls) = jc.splitJarReference
    jar.exists() && {
      withZipFile(jar)(zip => zip.getEntry(cls) != null)
    }
  }

  private def withZipFile[A](zip: File)(f: ZipFile => A): A = {
    val file = new ZipFile(zip)
    try f(file)
    finally file.close()
  }
}
