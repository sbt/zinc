package sbt.internal.inc

import sbt.io.IO
import java.util.zip.ZipFile
import java.io.File
import java.util.UUID

import scala.collection.JavaConverters._

import sbt.io.syntax.URL
import xsbti.compile.{ Output, SingleOutput }

/** STJ stands for Straight to Jar compilation.
 *
 *  This is a utility object that provides a set of functions
 *  that are used to implement this feature.
 *
 *  [[xsbt.STJ]] is a class that has similar purpose and
 *  duplicates some of the code, as it is difficult to share it.
 */
object STJ {

  type RelClass = String

  /** `JaredClass` is an identifier for a class located inside a jar.
   * For plain class files it is enough to simply use the actual file
   * system path. A class in jar is identified as a path to a jar
   * and path to the class within that jar. Those two values
   * are held in one string separated by `!`. Slashes in both
   * paths are consistent with `File.separatorChar` as the actual
   * string is usually kept in `File` object.
   *
   * As an example given a jar file "C:\develop\zinc\target\output.jar"
   * and relative path to the class "sbt/internal/inc/Compile.class"
   * The resulting identifier would be:
   * "C:\develop\zinc\target\output.jar!sbt\internal\inc\Compile.class"
   */
  class JaredClass(override val toString: String) extends AnyVal {

    def relClass: RelClass = toJarAndRelClass._2

    def toJarAndRelClass: (File, RelClass) = {
      val Array(jar, cls) = toString.split("!")
      // JaredClass stores RelClass part with File.separatorChar, however actual paths in zips always use '/'
      val relClass = cls.replace('\\', '/')
      (new File(jar), relClass)
    }

    def toFile: File = new File(toString)

  }

  object JaredClass {

    def apply(jar: File, cls: RelClass): JaredClass = {
      val relClass = if (File.separatorChar == '/') cls else cls.replace('/', File.separatorChar)
      new JaredClass(s"$jar!$relClass")
    }

    // Converts URL to JaredClass but reuses the jar file extracted at the call site to avoid recomputing.
    def fromURL(url: URL, jar: File): JaredClass = {
      val Array(_, cls) = url.getPath.split("!/")
      apply(jar, cls)
    }

    def fromFile(f: File): JaredClass = new JaredClass(f.toString)
  }

  val scalacOptions = Set("-YdisableFlatCpCaching")
  val javacOptions = Set("-XDuseOptimizedZip=false")

  def stashIndex(jar: File): IndexBasedZipFsOps.CentralDir = {
    IndexBasedZipFsOps.readCentralDir(jar)
  }

  def unstashIndex(jar: File, index: IndexBasedZipFsOps.CentralDir): Unit = {
    IndexBasedZipFsOps.writeCentralDir(jar, index)
  }

  def includeInJar(jar: File, files: Seq[(File, RelClass)]): Unit = {
    IndexBasedZipFsOps.includeInJar(jar, files)
  }

  // puts all files in `from` (overriding the original files in case of conflicts)
  // into `to`, removing `from`. In other words it merges `from` into `into`.
  def mergeJars(into: File, from: File): Unit = {
    IndexBasedZipFsOps.mergeArchives(into, from)
  }

  def createCachedStampReader(): File => Long = {
    val reader = new IndexBasedZipFsOps.CachedStampReader
    file: File =>
      if (isJar(file)) {
        val (jar, cls) = JaredClass.fromFile(file).toJarAndRelClass
        reader.readStamp(jar, cls)
      } else {
        IO.getModifiedTimeOrZero(file)
      }
  }

  def removeFromJar(jarFile: File, classes: Iterable[RelClass]): Unit = {
    if (jarFile.exists()) {
      IndexBasedZipFsOps.removeEntries(jarFile, classes)
    }
  }

  def withPreviousJar[A](output: Output)(compile: /*extra classpath: */ Seq[File] => A): A = {
    getOutputJar(output)
      .filter(_.exists())
      .map { outputJar =>
        val prevJar = createPrevJarPath()
        IO.move(outputJar, prevJar)

        val result = try {
          compile(Seq(prevJar))
        } catch {
          case e: Exception =>
            IO.move(prevJar, outputJar)
            throw e
        }

        if (outputJar.exists()) {
          STJ.mergeJars(into = prevJar, from = outputJar)
        }
        IO.move(prevJar, outputJar)
        result
      }
      .getOrElse {
        compile(Nil)
      }
  }

  private def createPrevJarPath(): File = {
    val tempDir =
      sys.props.get("zinc.compile-to-jar.tmp-dir").map(new File(_)).getOrElse(IO.temporaryDirectory)
    val prevJarName = s"$prevJarPrefix-${UUID.randomUUID()}.jar"
    tempDir.toPath.resolve(prevJarName).toFile
  }

  val prevJarPrefix: String = "prev-jar"

  def isJar(file: File): Boolean = {
    file.toString.split("!") match {
      case Array(jar, _) => jar.endsWith(".jar")
      case _             => false
    }
  }

  def isEnabled(output: Output): Boolean = {
    getOutputJar(output).isDefined
  }

  def getOutputJar(output: Output): Option[File] = {
    output match {
      case s: SingleOutput =>
        Some(s.getOutputDirectory).filter(_.getName.endsWith(".jar"))
      case _ => None
    }
  }

  def javacTempOutput(outputJar: File): File = {
    val outJarName = outputJar.getName
    val outDirName = outJarName + "-javac-output"
    outputJar.toPath.resolveSibling(outDirName).toFile
  }

  // for test code only
  def listFiles(jar: File): Seq[String] = {
    if (jar.exists()) {
      withZipFile(jar) { zip =>
        zip.entries().asScala.filterNot(_.isDirectory).map(_.getName).toList
      }
    } else Seq.empty
  }

  def readModifiedTimeFromJar(jc: JaredClass): Long = {
    val (jar, cls) = jc.toJarAndRelClass
    if (jar.exists()) {
      withZipFile(jar) { zip =>
        Option(zip.getEntry(cls)).map(_.getLastModifiedTime.toMillis).getOrElse(0)
      }
    } else 0
  }

  def existsInJar(jc: JaredClass): Boolean = {
    val (jar, cls) = jc.toJarAndRelClass
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
