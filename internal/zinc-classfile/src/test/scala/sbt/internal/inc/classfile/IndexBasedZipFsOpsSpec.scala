package sbt.internal.inc
package classfile

import java.nio.file._
import java.nio.file.spi.FileSystemProvider
import scala.collection.JavaConverters._

class IndexBasedZipFsOpsSpec extends UnitSpec {
  private val XL = 0xffff // minimum size to be zip64, which I'm calling "XL"
  private val L = XL - 1 // last size to be standard zip, which I'm calling "L"
  private val tmpDir = Files.createTempDirectory("zinc-zipmergetest")
  private lazy val zipFsProvider =
    FileSystemProvider.installedProviders().stream().filter(_.getScheme == "jar").findAny().get()

  it should "create XS jars" in assertSize(createJar(2), 2)
  it should "merge  XS jars" in assertMerge(2, 1)
  it should "shrink XS jars" in assertShrink(3, 2)

  it should "create  L jars" in assertSize(createJar(L), L)
  it should "merge   L jars" in assertMerge(L, 1) //      breach threshold
  it should "shrink  L jars" in assertShrink(L + 1, L) // breach threshold back

  it should "create XL jars" in assertSize(createJar(XL), XL)
  it should "merge  XL jars" in assertMerge(XL, 1)
  it should "shrink XL jars" in assertShrink(XL + 1, XL)

  private def assertMerge(size1: Int, size2: Int) = {
    val a = createJar(size1)
    val b = createJar(size2, "b", size1)
    safely(IndexBasedZipFsOps.mergeArchives(a, b))
    assertSize(a, size1 + size2)
  }

  private def assertShrink(size1: Int, size2: Int) = {
    val a = createJar(size1)
    val files = for (i <- size2 until size1) yield classFileName(i)
    safely(IndexBasedZipFsOps.removeEntries(a.toFile, files))
    assertSize(a, size2)
  }

  private def assertSize(p: Path, size: Int) = {
    val cen = safely(IndexBasedZipFsOps.readCentralDir(p.toFile))
    assert(cen.getHeaders.size() == size)
    Files.delete(p)
  }

  private def createJar(n: Int, name: String = "a", from: Int = 0) = {
    val out = tmpDir.resolve(s"$name.jar")
    val zipfs = zipFsProvider.newFileSystem(out, Map("create" -> "true").asJava)
    val root = zipfs.getRootDirectories.iterator().next()
    for (i <- from until (from + n)) {
      val empty = root.resolve(classFileName(i))
      Files.write(empty, Array.emptyByteArray)
    }
    zipfs.close()
    out
  }

  private def classFileName(i: Int) = f"C$i%032d.class"

  private def safely[A](op: => A) =
    try op
    catch { case ex: java.util.zip.ZipError => throw new ZipException(ex) }
}

// Avoid java.util.zip.ZipError, which is a VirtualMachineError!!?!
final class ZipException(val cause: Throwable) extends Exception
    with scala.util.control.NoStackTrace {
  override def toString: String = cause.toString
  override def getCause: Throwable = cause.getCause
  override def getMessage: String = cause.getMessage
  override def getStackTrace: Array[StackTraceElement] = cause.getStackTrace
  override def printStackTrace(s: java.io.PrintStream): Unit = cause.printStackTrace(s)
  override def printStackTrace(s: java.io.PrintWriter): Unit = cause.printStackTrace(s)
}
