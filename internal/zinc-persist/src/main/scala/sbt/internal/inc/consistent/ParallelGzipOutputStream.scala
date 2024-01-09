package sbt.internal.inc.consistent

import java.io.{ ByteArrayOutputStream, FilterOutputStream, OutputStream }
import java.util.zip.{ CRC32, Deflater, DeflaterOutputStream }

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.collection.mutable

/**
 * Parallel gzip compression. Algorithm based on https://github.com/shevek/parallelgzip
 * with additional optimization and simplification. This is essentially a block-buffered
 * stream but instead of writing a full block to the underlying output, it is passed to a
 * thread pool for compression and the Futures of compressed blocks are collected when
 * flushing.
 */
object ParallelGzipOutputStream {
  private val blockSize = 64 * 1024
  private val compression = Deflater.DEFAULT_COMPRESSION

  private class BufOut(size: Int) extends ByteArrayOutputStream(size) {
    def writeTo(buf: Array[Byte]): Unit = System.arraycopy(this.buf, 0, buf, 0, count)
  }

  private class Worker {
    private[this] val defl = new Deflater(compression, true)
    private[this] val buf = new BufOut(blockSize + (blockSize >> 3))
    private[this] val out = new DeflaterOutputStream(buf, defl, true)
    def compress(b: Block): Unit = {
      defl.reset()
      buf.reset()
      out.write(b.data, 0, b.length)
      out.flush()
      b.length = buf.size
      if (b.length > b.data.length) b.data = new Array[Byte](b.length)
      buf.writeTo(b.data)
    }
  }

  private val localWorker = new ThreadLocal[Worker] {
    override def initialValue = new Worker
  }

  private class Block {
    var data = new Array[Byte](blockSize + (blockSize >> 3))
    var length = 0
  }

  private val header = Array[Byte](0x1f.toByte, 0x8b.toByte, Deflater.DEFLATED, 0, 0, 0, 0, 0, 0, 0)
}

final class ParallelGzipOutputStream(out: OutputStream, ec: ExecutionContext, parallelism: Int)
    extends FilterOutputStream(out) {
  import ParallelGzipOutputStream._

  private final val crc = new CRC32
  private final val queueLimit = parallelism * 3
  // preferred on 2.13: new mutable.ArrayDeque[Future[Block]](queueLimit)
  private final val pending = mutable.Queue.empty[Future[Block]]
  private var current: Block = new Block
  private var free: Block = _
  private var total = 0L

  out.write(header)

  override def write(b: Int): Unit = write(Array[Byte]((b & 0xff).toByte))
  override def write(b: Array[Byte]): Unit = write(b, 0, b.length)

  @tailrec override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    val copy = math.min(len, blockSize - current.length)
    crc.update(b, off, copy)
    total += copy
    System.arraycopy(b, off, current.data, current.length, copy)
    current.length += copy
    if (copy < len) {
      submit()
      write(b, off + copy, len - copy)
    }
  }

  private[this] def submit(): Unit = {
    flushUntil(queueLimit - 1)
    val finalBlock = current
    pending += Future { localWorker.get.compress(finalBlock); finalBlock }(ec)
    if (free != null) {
      current = free
      free = null
    } else current = new Block()
  }

  private def flushUntil(remaining: Int): Unit =
    while (pending.length > remaining || pending.headOption.exists(_.isCompleted)) {
      val b = Await.result(pending.dequeue(), Duration.Inf)
      out.write(b.data, 0, b.length)
      b.length = 0
      free = b
    }

  override def flush(): Unit = {
    if (current.length > 0) submit()
    flushUntil(0)
    super.flush()
  }

  override def close(): Unit = {
    flush()
    val buf = new Array[Byte](10)
    def int(i: Int, off: Int): Unit = {
      buf(off) = ((i & 0xff).toByte)
      buf(off + 1) = (((i >>> 8) & 0xff).toByte)
      buf(off + 2) = (((i >>> 16) & 0xff).toByte)
      buf(off + 3) = (((i >>> 24) & 0xff).toByte)
    }
    buf(0) = 3
    int(crc.getValue.toInt, 2)
    int((total & 0xffffffffL).toInt, 6)
    out.write(buf)
    out.close()
    total = Integer.MIN_VALUE
    free = null
  }
}
