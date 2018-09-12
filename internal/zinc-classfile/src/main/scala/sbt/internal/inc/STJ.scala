package sbt.internal.inc

import sbt.io.IO
import java.util.zip.ZipFile
import java.io.File
import java.util.UUID

import scala.collection.JavaConverters._

import sbt.io.syntax.URL
import xsbti.compile.{ Output, SingleOutput }

object STJ extends PathFunctions with ForTestCode {

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
        val (jar, cls) = toJarAndRelClass(file.toString)
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
}

sealed trait ForTestCode { this: PathFunctions =>

  def listFiles(jar: File): Seq[String] = {
    if (jar.exists()) {
      withZipFile(jar) { zip =>
        zip.entries().asScala.filterNot(_.isDirectory).map(_.getName).toList
      }
    } else Seq.empty
  }

  def readModifiedTimeFromJar(jc: JaredClass): Long = {
    val (jar, cls) = toJarAndRelClass(jc)
    if (jar.exists()) {
      withZipFile(jar) { zip =>
        Option(zip.getEntry(cls)).map(_.getLastModifiedTime.toMillis).getOrElse(0)
      }
    } else 0
  }

  def existsInJar(s: JaredClass): Boolean = {
    val (jar, cls) = toJarAndRelClass(s)
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

sealed trait PathFunctions {

  type JaredClass = String
  type RelClass = String

  def init(jar: File, cls: RelClass): JaredClass = {
    val relClass = if (File.separatorChar == '/') cls else cls.replace(File.separatorChar, '/')
    s"$jar!$relClass"
  }

  // Converts URL to JaredClass but reuses the jar file extracted at the call site to avoid recalculation.
  def fromJarAndUrl(jar: File, url: URL): JaredClass = {
    val Array(_, cls) = url.getPath.split("!/")
    init(jar, cls)
  }

  def getRelClass(jc: JaredClass): RelClass = {
    toJarAndRelClass(jc)._2
  }

  def getJarFile(jc: JaredClass): File = {
    toJarAndRelClass(jc)._1
  }

  def toJarAndRelClass(jc: JaredClass): (File, RelClass) = {
    val Array(jar, cls) = jc.split("!")
    // JaredClass stores this part with File.separatorChar, however actual paths in zips always use '/'
    val relClass = cls.replace('\\', '/')
    (new File(jar), relClass)
  }

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

  def javacOutputTempDir(outputJar: File): File = {
    val outJarName = outputJar.getName
    val outDirName = outJarName + "-javac-output"
    outputJar.toPath.resolveSibling(outDirName).toFile
  }

}
