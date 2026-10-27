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

package sbt
package internal
package inc

import java.nio.{ ByteBuffer, ByteOrder }
import java.nio.channels.FileChannel
import java.nio.file.{ Files, Path }
import java.nio.file.StandardOpenOption.{ CREATE, CREATE_NEW, WRITE }
import java.nio.file.attribute.FileTime
import java.util.{ Arrays, UUID }
import java.util.zip.ZipException
import sbt.internal.inc.zip.ZipCentralDir
import sbt.io.IO
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Writes a jar whose bytes depend only on its entries: sorted by name, with headers that carry no
 * time of their own, and with each entry's compressed data copied as it is, never compressed again.
 */
private[inc] object SortedJar:
  private val LocalSignature = 0x04034b50
  private val CentralSignature = 0x02014b50
  private val EndSignature = 0x06054b50
  private val Zip64EndSignature = 0x06064b50
  private val Zip64LocatorSignature = 0x07064b50
  private val Zip64Tag: Short = 1
  private val Max16 = 0xffff
  private val Max32 = 0xffffffffL
  private val Utf8Flag = 0x800
  private val BufferSize = 1 << 16

  /** 2010-01-01T00:00, the time sbt gives a packaged jar's entries, as a DOS date and time. */
  private val DosTime = ((2010 - 1980) << 25) | (1 << 21) | (1 << 16)

  final class Entry(header: ZipCentralDir.Entry, val jar: Path):
    val name: String = header.getName
    val nameBytes: Array[Byte] = header.getNameBytes
    val flag: Int = header.getFlag & Utf8Flag
    val method: Int = header.getMethod
    val crc: Long = header.getCrc
    val compressedSize: Long = header.getCompressedSize
    val size: Long = header.getSize
    val offset: Long = header.getEntryOffset
    val zip64: Boolean = compressedSize >= Max32 || size >= Max32
    val localHeaderSize: Int = 30 + nameBytes.length + (if zip64 then 20 else 0)
    def end: Long = offset + localHeaderSize + compressedSize
  end Entry

  /** The file entries of `jar`, none if it does not exist. */
  def entries(jar: Path): Vector[Entry] =
    if !Files.exists(jar) then Vector.empty
    else
      ZipCentralDir(jar).getHeaders.asScala.iterator
        .filterNot(_.getName.endsWith("/"))
        .map(Entry(_, jar))
        .toVector

  /**
   * `entries`, with each one whose name and data `existing` holds replaced by that entry, which
   * [[write]] leaves in place: a class compiled again can give the same pickle.
   */
  def reuse(existing: Vector[Entry], entries: Vector[Entry]): Vector[Entry] =
    val byName = existing.iterator.map(e => e.name -> e).toMap
    val candidates = entries.map(e => byName.get(e.name).filter(sameHeader(_, e)))
    if candidates.forall(_.isEmpty) then entries
    else
      Using.Manager { use =>
        val windows = mutable.HashMap.empty[Path, Window]
        def window(p: Path) = windows.getOrElseUpdate(p, Window(use(FileChannel.open(p))))
        entries.lazyZip(candidates).map { (e, old) =>
          old.filter(o => window(o.jar).sameData(o, window(e.jar), e)).getOrElse(e)
        }
      }.get

  /**
   * Leaves `jar`, which holds `before`, holding `after`. It writes in place, as on Windows a jar
   * cannot be replaced while a compiler has it open, and only from the first entry that changes:
   * `keep` first gets a copy of the bytes from there on, and a copy it declines is deleted.
   */
  def write(jar: Path, before: Vector[Entry], after: Vector[Entry], keep: (Path, Long) => Boolean)
      : Unit =
    val old = before.sortWith(byName)
    val updated = after.sortWith(byName)
    val size = if Files.exists(jar) then Files.size(jar) else -1L
    val laidOut = size >= 0 && isLaidOut(jar, size, old)
    val unchanged =
      if laidOut then old.iterator.zip(updated).takeWhile((a, b) => a eq b).size else 0
    if !laidOut || unchanged < old.size || unchanged < updated.size then
      if size < 0 then Files.createDirectories(jar.getParent)
      val modified = Option.when(size >= 0)(Files.getLastModifiedTime(jar))
      val offsets = offsetsOf(updated)
      val from = offsets(unchanged)
      val copy = Option.when(from < size)(copyAside(jar, from, size))
      val kept = copy.exists(keep(_, from))
      val index = centralDirectory(updated, offsets)
      try rewrite(jar, from, updated.drop(unchanged), laidOut, copy, index)
      finally if !kept then copy.foreach(c => IO.delete(c.toFile))
      modified.foreach(advance(jar, _))

  /**
   * Moves the mtime of `jar` past `before`, where an in-place write within the filesystem's time
   * resolution left it: a compiler reuses the index it read of a jar while the mtime holds. The
   * steps reach the 2 s resolution of FAT.
   */
  private[inc] def advance(jar: Path, before: FileTime): Unit =
    val later = Iterator(1L, 1000L, 2000L).map(ms => FileTime.fromMillis(before.toMillis + ms))
    while Files.getLastModifiedTime(jar).compareTo(before) <= 0 && later.hasNext do
      Files.setLastModifiedTime(jar, later.next())

  private[inc] def transfer(in: FileChannel, position: Long, count: Long, out: FileChannel): Unit =
    var done = 0L
    while done < count do
      val n = in.transferTo(position + done, count - done, out)
      if n <= 0 then throw ZipException(s"unexpected end of zip at ${position + done}")
      done += n

  private val byName: (Entry, Entry) => Boolean =
    (a, b) => Arrays.compareUnsigned(a.nameBytes, b.nameBytes) < 0

  private def sameHeader(a: Entry, b: Entry): Boolean =
    a.flag == b.flag && a.method == b.method && a.crc == b.crc &&
      a.compressedSize == b.compressedSize && a.size == b.size &&
      Arrays.equals(a.nameBytes, b.nameBytes)

  private def offsetsOf(entries: Vector[Entry]): Vector[Long] =
    entries.scanLeft(0L)((offset, e) => offset + e.localHeaderSize + e.compressedSize)

  /** Whether `jar` is `entries` laid out by [[write]], the only writer of such an index. */
  private def isLaidOut(jar: Path, size: Long, entries: Vector[Entry]): Boolean =
    val offsets = offsetsOf(entries)
    entries.lazyZip(offsets).forall((e, offset) => e.offset == offset) && {
      val index = centralDirectory(entries, offsets)
      size == offsets.last + index.length &&
      Using.resource(FileChannel.open(jar))(holds(_, offsets.last, index))
    }

  private def holds(in: FileChannel, position: Long, bytes: Array[Byte]): Boolean =
    val buffer = ByteBuffer.allocate(math.min(bytes.length, BufferSize))
    var done = 0
    var same = true
    while same && done < bytes.length do
      buffer.clear().limit(math.min(bytes.length - done, buffer.capacity))
      readFully(in, buffer, position + done)
      same = Arrays.equals(buffer.array, 0, buffer.limit, bytes, done, done + buffer.limit)
      done += buffer.limit
    same

  private def copyAside(jar: Path, from: Long, size: Long): Path =
    val copy = jar.resolveSibling(s"${jar.getFileName}-${UUID.randomUUID()}.tmp")
    try
      Using.resources(FileChannel.open(jar), FileChannel.open(copy, CREATE_NEW, WRITE)) {
        (in, out) => transfer(in, from, size - from, out)
      }
    catch
      case e: Throwable =>
        IO.delete(copy.toFile)
        throw e
    copy

  /**
   * Writes `written` from `from` on, then `index`. Where `jar` was laid out already, each run of
   * its entries keeps its headers and moves as one block; any other entry gets a new header.
   */
  private def rewrite(
      jar: Path,
      from: Long,
      written: Vector[Entry],
      laidOut: Boolean,
      copy: Option[Path],
      index: Array[Byte]
  ): Unit =
    Using.Manager { use =>
      val out = Output(use(FileChannel.open(jar, CREATE, WRITE)), from)
      val copied = copy.map(c => use(FileChannel.open(c)))
      val windows =
        (copied.map(jar -> _) ++
          written.map(_.jar).filter(_ != jar).distinct.map(p => p -> use(FileChannel.open(p))))
          .map((p, in) => p -> Window(in))
          .toMap
      var i = 0
      while i < written.size do
        val e = written(i)
        if laidOut && e.jar == jar then
          def continues(k: Int) = written(k).jar == jar && written(k).offset == written(k - 1).end
          var j = i + 1
          while j < written.size && continues(j) do j += 1
          out.transfer(copied.get, e.offset - from, written(j - 1).end - e.offset)
          i = j
        else
          val base = if e.jar == jar then from else 0L
          val in = windows(e.jar)
          out.putLocalHeader(e)
          in.copy(in.dataOffset(e.offset - base), e.compressedSize, out)
          i += 1
      out.put(index)
      out.finish()
    }.get

  /** Writes through one buffer, so that headers and small entries cost no system call each. */
  private final class Output(channel: FileChannel, from: Long):
    private val buffer = ByteBuffer.allocate(BufferSize).order(ByteOrder.LITTLE_ENDIAN)
    channel.position(from)

    def put(bytes: Array[Byte]): Unit =
      if bytes.length > buffer.remaining then flush()
      if bytes.length > buffer.capacity then writeFully(channel, bytes) else buffer.put(bytes)

    def putLocalHeader(e: Entry): Unit =
      if e.localHeaderSize > buffer.remaining then flush()
      buffer.putInt(LocalSignature).putShort(version(e.zip64, e.method))
      buffer.putShort(e.flag.toShort).putShort(e.method.toShort).putInt(DosTime).putInt(e.crc.toInt)
      if e.zip64 then buffer.putInt(-1).putInt(-1)
      else buffer.putInt(e.compressedSize.toInt).putInt(e.size.toInt)
      buffer.putShort(e.nameBytes.length.toShort).putShort((if e.zip64 then 20 else 0).toShort)
      buffer.put(e.nameBytes)
      if e.zip64 then
        buffer.putShort(Zip64Tag).putShort(16).putLong(e.size).putLong(e.compressedSize)

    def put(bytes: Array[Byte], offset: Int, length: Int): Unit =
      if length > buffer.remaining then flush()
      buffer.put(bytes, offset, length)

    def transfer(in: FileChannel, position: Long, count: Long): Unit =
      flush()
      SortedJar.transfer(in, position, count, channel)

    def finish(): Unit =
      flush()
      channel.truncate(channel.position)

    private def flush(): Unit =
      buffer.flip()
      while buffer.hasRemaining do channel.write(buffer)
      buffer.clear()
  end Output

  /** Reads through one window, so that entries written next to each other cost one read. */
  private final class Window(in: FileChannel):
    private val buffer = ByteBuffer.allocate(BufferSize).order(ByteOrder.LITTLE_ENDIAN).limit(0)
    private var start = 0L

    /** Where the data after the local header at `at` starts: that header may have extra fields. */
    def dataOffset(at: Long): Long =
      val i = load(at, 30)
      if buffer.getInt(i) != LocalSignature then throw ZipException(s"no local header at $at")
      at + 30 + (buffer.getShort(i + 26) & 0xffff) + (buffer.getShort(i + 28) & 0xffff)

    def copy(position: Long, count: Long, out: Output): Unit =
      var done = 0L
      while done < count do
        val n = math.min(count - done, BufferSize.toLong).toInt
        val i = load(position + done, n)
        out.put(buffer.array, i, n)
        done += n

    /** Whether `e`, here, and `o`, in `other`, have the same compressed data. */
    def sameData(e: Entry, other: Window, o: Entry): Boolean =
      val at = dataOffset(e.offset)
      val otherAt = other.dataOffset(o.offset)
      var done = 0L
      var same = true
      while same && done < e.compressedSize do
        val n = math.min(e.compressedSize - done, BufferSize.toLong).toInt
        val i = load(at + done, n)
        val j = other.load(otherAt + done, n)
        same = Arrays.equals(buffer.array, i, i + n, other.buffer.array, j, j + n)
        done += n
      same

    /** Loads `[position, position + count)`, at most a window, and returns where it starts. */
    private def load(position: Long, count: Int): Int =
      if position < start || position + count > start + buffer.limit then
        start = position
        buffer.clear()
        while buffer.position < count do
          if in.read(buffer, start + buffer.position) < 0 then
            throw ZipException(s"unexpected end of zip at ${start + buffer.position}")
        buffer.flip()
      (position - start).toInt
  end Window

  private def readFully(in: FileChannel, buffer: ByteBuffer, position: Long): Unit =
    while buffer.hasRemaining do
      if in.read(buffer, position + buffer.position) < 0 then
        throw ZipException(s"unexpected end of zip at ${position + buffer.position}")

  private def writeFully(out: FileChannel, bytes: Array[Byte]): Unit =
    val buffer = ByteBuffer.wrap(bytes)
    while buffer.hasRemaining do out.write(buffer)

  private def version(zip64: Boolean, method: Int): Short =
    if zip64 then 45 else if method == 0 then 10 else 20

  private def capped(value: Long): Int = if value >= Max32 then -1 else value.toInt

  private def extraLength(e: Entry, offset: Long): Int =
    if !e.zip64 && offset < Max32 then 0
    else 4 + 8 * List(e.size, e.compressedSize, offset).count(_ >= Max32)

  private def putCentralHeader(b: ByteBuffer, e: Entry, offset: Long): Unit =
    val extra = extraLength(e, offset)
    val v = version(extra > 0, e.method)
    b.putInt(CentralSignature).putShort(v).putShort(v)
    b.putShort(e.flag.toShort).putShort(e.method.toShort).putInt(DosTime).putInt(e.crc.toInt)
    b.putInt(capped(e.compressedSize)).putInt(capped(e.size))
    b.putShort(e.nameBytes.length.toShort).putShort(extra.toShort)
    b.putShort(0).putShort(0).putShort(0).putInt(0).putInt(capped(offset))
    b.put(e.nameBytes)
    if extra > 0 then
      b.putShort(Zip64Tag).putShort((extra - 4).toShort)
      List(e.size, e.compressedSize, offset).filter(_ >= Max32).foreach(b.putLong)

  private def centralDirectory(entries: Vector[Entry], offsets: Vector[Long]): Array[Byte] =
    var length = 0L
    entries.lazyZip(offsets).foreach { (e, offset) =>
      length += 46 + e.nameBytes.length + extraLength(e, offset)
    }
    val zip64 = entries.size >= Max16 || offsets.last >= Max32 || length >= Max32
    val b = ByteBuffer
      .allocate(Math.toIntExact(length) + (if zip64 then 76 else 0) + 22)
      .order(ByteOrder.LITTLE_ENDIAN)
    entries.lazyZip(offsets).foreach((e, offset) => putCentralHeader(b, e, offset))
    if zip64 then
      b.putInt(Zip64EndSignature).putLong(44).putShort(45).putShort(45).putInt(0).putInt(0)
      b.putLong(entries.size).putLong(entries.size).putLong(length).putLong(offsets.last)
      b.putInt(Zip64LocatorSignature).putInt(0).putLong(offsets.last + length).putInt(1)
    val capped16 = math.min(entries.size, Max16).toShort
    b.putInt(EndSignature).putShort(0).putShort(0).putShort(capped16).putShort(capped16)
    b.putInt(capped(length)).putInt(capped(offsets.last)).putShort(0)
    b.array
end SortedJar
