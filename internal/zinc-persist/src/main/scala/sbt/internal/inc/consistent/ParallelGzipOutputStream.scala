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

// Original code by Stefan Zeiger (see https://github.com/szeiger/zinc/blob/1d296b2fbeaae1cf14e4c00db0bbc2203f9783a4/internal/zinc-persist/src/main/scala/sbt/internal/inc/consistent/NewParallelGzipOutputStream.scala)
// Modified by Rex Kerr to use Java threads directly rather than Future
package sbt.internal.inc.consistent
import java.io.{ ByteArrayOutputStream, FilterOutputStream, OutputStream }
import java.util.zip.{ CRC32, Deflater, DeflaterOutputStream }

import java.util.concurrent.{ SynchronousQueue, ArrayBlockingQueue, LinkedTransferQueue }

import scala.annotation.tailrec

/**
 * Parallel gzip compression. Algorithm based on https://github.com/shevek/parallelgzip
 * with additional optimization and simplification. This is essentially a block-buffered
 * stream but instead of writing a full block to the underlying output, it is passed to a
 * thread pool for compression and the compressed blocks are collected when flushing.
 */
object ParallelGzipOutputStream {
  private val blockSize = 64 * 1024
  private val compression = Deflater.DEFAULT_COMPRESSION

  // Holds an input buffer to load data and an output buffer to write
  // the compressed data into.  Compressing clears the input buffer.
  // Compressed data can be retrieved with `output.writeTo(OutputStream)`.
  private final class Block(var index: Int) {
    val input = new Array[Byte](blockSize)
    var inputN = 0
    val output = new ByteArrayOutputStream(blockSize + (blockSize >> 3))
    val deflater = new Deflater(compression, true)
    val dos = new DeflaterOutputStream(output, deflater, true)

    def compress(): Unit = {
      deflater.reset()
      output.reset()
      dos.write(input, 0, inputN)
      dos.flush()
      inputN = 0
    }
  }

  // Waits for data to appear in a SynchronousQueue.
  // When it does, compress it and pass it along.  Also put self in a pool of workers awaiting more work.
  // If data does not appear but a `None` appears instead, cease running (and do not add self to work queue).
  private final class Worker(
      val workers: ArrayBlockingQueue[Worker],
      val compressed: LinkedTransferQueue[Either[Int, Block]]
  ) extends Thread {
    val work = new SynchronousQueue[Option[Block]]

    @tailrec
    def loop(): Unit = {
      work.take() match {
        case Some(block) =>
          block.compress()
          compressed.put(Right(block))
          workers.put(this)
          loop()
        case _ =>
      }
    }

    override def run(): Unit = {
      loop()
    }
  }

  // Waits for data to appear in a LinkedTransferQueue.
  // When it does, place it into a sorted tree and, if the data is in order, write it out.
  // Once the data has been written, place it into a cache for completed buffers.
  // If data does not appear but an integer appears instead, set a mark to quit once
  // that many blocks have been written.
  private final class Scribe(out: OutputStream, val completed: LinkedTransferQueue[Block])
      extends Thread {
    val work = new LinkedTransferQueue[Either[Int, Block]]
    private val tree = new collection.mutable.TreeMap[Int, Block]
    private var next = 0
    private var stopAt = Int.MaxValue

    @tailrec
    def loop(): Unit = {
      work.take() match {
        case Right(block) =>
          tree(block.index) = block
        case Left(limit) =>
          stopAt = limit
      }
      while (tree.nonEmpty && tree.head._2.index == next) {
        val block = tree.remove(next).get
        block.output.writeTo(out)
        completed.put(block)
        next += 1
      }
      if (next < stopAt) loop()
    }

    override def run(): Unit = {
      loop()
    }
  }

  private val header = Array[Byte](0x1f.toByte, 0x8b.toByte, Deflater.DEFLATED, 0, 0, 0, 0, 0, 0, 0)
}

/**
 * Implements a parallel chunked compression algorithm (using minimum of two extra threads).
 * Note that the methods in this class are not themselves threadsafe; this class
 * has "interior concurrency" (c.f. interior mutability).  In particular, writing
 * concurrent with or after a close operation is not defined.
 */
final class ParallelGzipOutputStream(out: OutputStream, parallelism: Int)
    extends FilterOutputStream(out) {
  import ParallelGzipOutputStream._

  private val crc = new CRC32
  private var totalBlocks = 0
  private var totalCount = 0L

  private val bufferLimit = parallelism * 3
  private var bufferCount = 1
  private var current = new Block(0)

  private val workerCount = math.max(1, parallelism - 1)
  private val workers = new ArrayBlockingQueue[Worker](workerCount)
  private val buffers = new LinkedTransferQueue[Block]()

  out.write(header)
  private val scribe = new Scribe(out, buffers)
  scribe.start()

  while (workers.remainingCapacity() > 0) {
    val w = new Worker(workers, scribe.work)
    workers.put(w)
    w.start()
  }

  override def write(b: Int): Unit = write(Array[Byte]((b & 0xff).toByte))
  override def write(b: Array[Byte]): Unit = write(b, 0, b.length)

  @tailrec
  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    val copy = math.min(len, blockSize - current.inputN)
    crc.update(b, off, copy)
    totalCount += copy
    System.arraycopy(b, off, current.input, current.inputN, copy)
    current.inputN += copy
    if (copy < len) {
      submit()
      write(b, off + copy, len - copy)
    }
  }

  private def submit(): Unit = {
    val w = workers.take()
    w.work.put(Some(current))
    totalBlocks += 1
    current = buffers.poll()
    if (current eq null) {
      if (bufferCount < bufferLimit) {
        current = new Block(totalBlocks)
        bufferCount += 1
      } else {
        current = buffers.take()
      }
    }
    current.index = totalBlocks
  }

  private def flushImpl(shutdown: Boolean): Unit = {
    val fetched = new Array[Block](bufferCount - 1)
    var n = 0
    // If we have all the buffers, all pending work is done.
    while (n < fetched.length) {
      fetched(n) = buffers.take()
      n += 1
    }
    if (shutdown) {
      // Send stop signal to workers and scribe
      n = workerCount
      while (n > 0) {
        workers.take().work.put(None)
        n -= 1
      }
      scribe.work.put(Left(totalBlocks))
    } else {
      // Put all the buffers back so we can keep accepting data.
      n = 0
      while (n < fetched.length) {
        buffers.put(fetched(n))
        n += 1
      }
    }
  }

  /**
   * Blocks until all pending data is written.  Note that this is a poor use of a parallel data writing class.
   * It is preferable to write all data and then close the stream.  Note also that a flushed stream will not
   * have the trailing CRC checksum and therefore will not be a valid compressed file, so there is little point
   * flushing early.
   */
  override def flush(): Unit = {
    if (current.inputN > 0) submit()
    flushImpl(false)
    super.flush()
  }

  override def close(): Unit = {
    if (current.inputN > 0) submit()
    flushImpl(true)

    val buf = new Array[Byte](10)
    val bb = java.nio.ByteBuffer.wrap(buf)
    bb.order(java.nio.ByteOrder.LITTLE_ENDIAN)
    bb.putShort(3)
    bb.putInt(crc.getValue.toInt)
    bb.putInt((totalCount & 0xffffffffL).toInt)
    out.write(buf)

    out.close()
  }
}
