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

package sbt.inc.consistent

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.nio.file.{ Files, Paths, StandardOpenOption }
import java.util.Arrays
import sbt.internal.inc.consistent.ParallelGzipOutputStream
import sbt.io.IO
import sbt.io.Using
import scala.util.Random
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

object ParallelGzipOutputStreamSpecification extends Properties:
  override def tests: List[Test] = List(
    property("compress and decompress data correctly", propRoundTrip).withTests(300),
    example("handle large data sizes", largeData),
    example("handle very large parallelism", veryLargeParallelism),
    property(
      "handle multiple ParallelGzipOutputStream concurrently",
      propConcurrentStreams
    ).withTests(50),
    example(
      "handle multiple ParallelGzipOutputStream with varying config concurrently",
      varyingConfigs
    ),
  )

  private val defaultSize: Int = 64 * 1024

  /** The sizes around the block boundaries of the stream, where off-by-one errors would hide. */
  private val boundarySizes: List[Int] = List(
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

  private def decompress(data: Array[Byte]): Array[Byte] =
    Using.gzipInputStream(new ByteArrayInputStream(data))(IO.readBytes)

  private def compress(data: Array[Byte], parallelism: Int): Array[Byte] =
    val bout = new ByteArrayOutputStream()
    val gout = new ParallelGzipOutputStream(bout, parallelism)
    try gout.write(data)
    finally gout.close()
    bout.toByteArray

  private def writeToFile(data: Array[Byte], fileName: String): Unit =
    val outputDir = Paths.get("../../../test-gzip-output")
    if !Files.exists(outputDir) then Files.createDirectories(outputDir)
    val path = outputDir.resolve(fileName)
    Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

  // Need this in windows to produce valid windows filename
  private def sanitizedFilename(fileName: String): String =
    fileName.replaceAll("[^a-zA-Z0-9-_.]", "_")

  private def handleFailure(
      compressed: Array[Byte],
      data: Array[Byte],
      testSetup: String,
      errorCause: String,
      errorOpt: Option[Exception] = None,
  ): Result =
    val compressedFileName = sanitizedFilename(s"compressed_$testSetup.gz")
    val dataFileName = sanitizedFilename(s"data_$testSetup.bin")
    writeToFile(compressed, compressedFileName)
    writeToFile(data, dataFileName)
    val failure = Result.failure.log(s"$errorCause. See $compressedFileName and $dataFileName")
    errorOpt.fold(failure)(e => failure.log(hedgehog.core.Error(e)))

  private def roundTrip(data: Array[Byte], parallelism: Int): Result =
    val testSetup = s"parallelism = $parallelism, size = ${data.length}"
    try
      val compressed = compress(data, parallelism)
      try
        if Arrays.equals(data, decompress(compressed)) then Result.success
        else handleFailure(compressed, data, testSetup, "Compression and decompression mismatch.")
      catch
        case e: Exception =>
          handleFailure(compressed, data, testSetup, "Decompression failed", Some(e))
    catch
      case e: Exception =>
        handleFailure(Array[Byte](), data, testSetup, "Compression Failed", Some(e))

  private def randomArray(size: Int, seed: Long = 0L): Array[Byte] =
    val data = new Array[Byte](size)
    new Random(seed).nextBytes(data)
    data

  private val genSize: Gen[Int] =
    Gen.frequency1(
      1 -> Gen.elementUnsafe(boundarySizes),
      1 -> Gen.int(Range.linear(0, defaultSize * 9)),
    )

  /** Generates how to build the data rather than the data, so that the logs stay small. */
  private case class DataSpec(size: Int, redundant: Boolean, seed: Long):
    def build: Array[Byte] = if redundant then new Array[Byte](size) else randomArray(size, seed)

  private val genData: Gen[DataSpec] =
    for
      size <- genSize
      redundant <- Gen.boolean
      seed <- Gen.long(Range.constant(Long.MinValue, Long.MaxValue))
    yield DataSpec(size, redundant, seed)

  private val genParallelism: Gen[Int] = Gen.int(Range.linear(1, 17))

  def propRoundTrip: Property =
    for
      data <- genData.forAll
      parallelism <- genParallelism.forAll
    yield roundTrip(data.build, parallelism)

  private def largeData: Result =
    val data = randomArray(64 * 1024 * 1024) // 64 MB
    Result.all((1 to 17).toList.map(roundTrip(data, _)))

  private def veryLargeParallelism: Result =
    roundTrip(randomArray(defaultSize * 16), 200)

  def propConcurrentStreams: Property =
    for
      streams <- Gen.element1(1, 2, 4, 8, 15).forAll
      parallelism <- genParallelism.forAll
      size <- genSize.forAll
    yield
      val data = randomArray(size)
      val verifications =
        Future.traverse((1 to streams).toList)(_ => Future(roundTrip(data, parallelism)))
      Result.all(Await.result(verifications, 60.seconds))

  private def varyingConfigs: Result =
    val configs =
      for
        parallelism <- (1 to 10).toList
        size <- boundarySizes
      yield (parallelism, size)
    val verifications = Future.traverse(configs) { (parallelism, size) =>
      Future(roundTrip(randomArray(size), parallelism))
    }
    Result.all(Await.result(verifications, 60.seconds))
end ParallelGzipOutputStreamSpecification
