package sbt.inc.consistent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ BufferedInputStream, ByteArrayInputStream, ByteArrayOutputStream }
import java.util.zip.GZIPInputStream
import java.nio.file.{ Files, Paths, StandardOpenOption }
import sbt.internal.inc.consistent.ParallelGzipOutputStream
import sbt.io.IO
import sbt.io.Using

import java.util.Arrays
import collection.parallel.CollectionConverters.*
import scala.util.Random
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

class ParallelGzipOutputStreamSpecification extends AnyFlatSpec with Matchers {
  val defaultSize: Int = 64 * 1024
  val sizes: Seq[Int] = Seq(
    0,
    1,
    3,
    32,
    127,
    1025,
    defaultSize - 1,
    defaultSize,
    defaultSize + 1,
    defaultSize * 8 - 1,
    defaultSize * 8,
    defaultSize * 8 + 1
  )
  val numberOfGzipStreams: Seq[Int] = Seq(1, 2, 4, 8, 15)
  val parallelisms: Seq[Int] = 1 to 17

  def decompress(data: Array[Byte]): Array[Byte] = {
    Using.gzipInputStream(new ByteArrayInputStream(data))(IO.readBytes)
  }

  def compress(data: Array[Byte], parallelism: Int, testSetup: String): Array[Byte] = {
    val bout = new ByteArrayOutputStream()
    val gout = new ParallelGzipOutputStream(bout, parallelism)
    try {
      gout.write(data)
    } catch {
      case e: Exception =>
        handleFailure(Array[Byte](), data, testSetup, "Compression Failed", Some(e))
    } finally {
      gout.close()
    }
    bout.toByteArray
  }

  def writeToFile(data: Array[Byte], fileName: String): Unit = {
    val outputDir = Paths.get("../../../test-gzip-output")
    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir)
    }
    val path = outputDir.resolve(fileName)
    Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  // Need this in windows to produce valid windows filename
  def sanitizedFilename(fileName: String): String = {
    fileName.replaceAll("[^a-zA-Z0-9-_.]", "_")
  }
  
  def handleFailure(
      compressed: Array[Byte],
      data: Array[Byte],
      testSetup: String,
      errorCause: String,
      errorOpt: Option[Exception] = None,
  ): Unit = {
    val compressedFileName = sanitizedFilename(s"compressed_$testSetup.gz")
    val dataFileName = sanitizedFilename(s"data_$testSetup.bin")
    writeToFile(compressed, compressedFileName)
    writeToFile(data, dataFileName)

    errorOpt match {
      case Some(error) =>
        fail(s"$errorCause. See $compressedFileName and $dataFileName", error)
      case _ => fail(s"$errorCause. See $compressedFileName and $dataFileName")
    }

  }

  def verifyRoundTrip(data: Array[Byte], parallelism: Int, testSetup: String): Unit = {
    val compressed = compress(data, parallelism, testSetup)
    try {
      val decompressed = decompress(compressed)
      if (!Arrays.equals(data, decompressed)) {
        handleFailure(compressed, data, testSetup, "Compression and decompression mismatch.")
      }
    } catch {
      case e: Exception =>
        handleFailure(
          compressed,
          data,
          testSetup,
          "Decompression failed",
          Some(e),
        )
    }
  }

  def randomArray(size: Int): Array[Byte] = {
    val rnd = new Random(0L)
    val data = new Array[Byte](size)
    rnd.nextBytes(data)
    data
  }

  it should "compress and decompress data correctly" in {
    for {
      parallelism <- parallelisms
      size <- sizes
    } {
      val data = randomArray(size)
      verifyRoundTrip(data, parallelism, s"parallelism = $parallelism, size = $size")
    }
  }

  it should "handle highly redundant data correctly" in {
    for {
      parallelism <- parallelisms
      size <- sizes
    } {
      val data = Array.fill(size)(0.toByte)
      verifyRoundTrip(data, parallelism, s"parallelism = $parallelism, size = $size, redundant")
    }
  }

  it should "handle large data sizes" in {
    val largeData = randomArray(64 * 1024 * 1024) // 64 MB
    for (parallelism <- parallelisms) {
      verifyRoundTrip(largeData, parallelism, s"parallelism = $parallelism, large data size")
    }
  }

  it should "handle very large parallelism" in {
    val data = randomArray(defaultSize * 16)
    val maxNumberOfThreads = 200
    verifyRoundTrip(data, maxNumberOfThreads, s"parallelism = $maxNumberOfThreads, large data")
  }

  it should "handle multiple ParallelGzipOutputStream concurrently" in {
    for {
      numberOfGzipStream <- numberOfGzipStreams
      parallelism <- parallelisms
      size <- sizes
    } {
      val verifications = Future.traverse(1 to numberOfGzipStream)(numberOfGzipStream =>
        Future {
          val data = randomArray(size)
          verifyRoundTrip(
            data,
            parallelism,
            s"numberOfStreams: $numberOfGzipStream, parallelism = $parallelism, size = $size, multiple"
          )
        }
      )
      Await.result(verifications, 60.seconds)
    }
  }

  it should "handle multiple ParallelGzipOutputStream with varying config concurrently" in {
    val verifications = Future.traverse(for {
      parallelism <- parallelisms.take(10)
      size <- sizes
    } yield (parallelism, size)) { case (parallelism, size) =>
      Future {
        val data = randomArray(size)
        verifyRoundTrip(
          data,
          parallelism,
          s"parallelism = $parallelism, size = $size, varying"
        )
      }
    }
    Await.result(verifications, 60.seconds)
  }
}
