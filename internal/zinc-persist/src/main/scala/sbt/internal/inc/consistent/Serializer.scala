package sbt.internal.inc.consistent

import java.io.{
  BufferedReader,
  BufferedWriter,
  EOFException,
  InputStream,
  InputStreamReader,
  OutputStream,
  OutputStreamWriter,
  Writer
}
import java.nio.charset.StandardCharsets
import java.util.Optional

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.collection.Factory

/** Structural serialization for text and binary formats. */
abstract class Serializer {
  private final val dedupMap: mutable.Map[AnyRef, Int] = mutable.Map.empty

  def startBlock(name: String): Unit
  def startArray(name: String, length: Int): Unit // use length = -1 for null
  def endBlock(): Unit
  def endArray(): Unit
  def string(s: String): Unit
  def bool(b: Boolean): Unit
  def int(i: Int): Unit
  def byte(b: Byte): Unit
  def long(l: Long): Unit
  def end(): Unit

  @inline final def dedup[T >: Null <: AnyRef](o: T)(id: T => Int)(writeBody: => Unit): Unit = {
    if (o == null) int(-1)
    else {
      val nextId = -2 - dedupMap.size
      val idx = dedupMap.getOrElseUpdate(o, nextId)
      if (idx == nextId) {
        int(id(o))
        writeBody
      } else int(idx)
    }
  }
  @inline final def writeArray[T](
      name: String,
      a: Array[T],
      perEntry: Int = 1
  )(f: T => Unit): Unit = {
    if (a == null) startArray(name, -1)
    else {
      startArray(name, a.length * perEntry)
      a.foreach(f)
    }
    endArray()
  }
  @inline final def writeStringArray(name: String, a: Array[String]): Unit =
    writeArray(name, a)(string)

  @inline final def writeColl[T](
      name: String,
      a: Iterable[T],
      perEntry: Int = 1
  )(f: T => Unit): Unit = {
    if (a == null) startArray(name, -1)
    else {
      startArray(name, a.size * perEntry)
      a.iterator.foreach(f)
    }
    endArray()
  }

  @inline final def writeStringColl(name: String, a: Iterable[String]): Unit =
    writeColl(name, a)(string)

  @inline final def writeBlock(name: String)(f: => Unit): Unit = {
    startBlock(name)
    f
    endBlock()
  }
  @inline final def writeSortedStringMap[V](
      name: String,
      map: scala.collection.Iterable[(String, V)],
      perEntry: Int = 1
  )(f: V => Unit): Unit = {
    if (map == null) {
      startArray(name, -1)
      endArray()
    } else {
      val a = map.toArray
      a.sortInPlaceBy(_._1)
      writeArray(name, a, perEntry + 1) { kv =>
        string(kv._1)
        f(kv._2)
      }
    }
  }
  @inline final def writeOptionalString(o: Optional[String]): Unit = string(o.orElse(null))
}

/** Derialization for text and binary formats produced by Serializer. */
abstract class Deserializer {
  private final val dedupBuffer: ArrayBuffer[AnyRef] = ArrayBuffer.empty

  def startBlock(): Unit
  def startArray(): Int
  def endBlock(): Unit
  def endArray(): Unit
  def string(): String
  def bool(): Boolean
  def int(): Int
  def byte(): Byte
  def long(): Long
  def end(): Unit

  @inline final def dedup[T >: Null <: AnyRef](readBody: Int => T): T = int() match {
    case -1 => null
    case id if id >= 0 =>
      val o = readBody(id)
      dedupBuffer += o
      o
    case idx =>
      dedupBuffer(-2 - idx).asInstanceOf[T]
  }

  @inline final def readArray[T: ClassTag](perEntry: Int = 1)(f: => T): Array[T] = {
    startArray() match {
      case -1 =>
        endArray()
        null
      case rawLen =>
        val len = rawLen / perEntry
        val a = new Array[T](len)
        var i = 0
        while (i < a.length) { a(i) = f; i += 1 }
        endArray()
        a
    }
  }
  @inline final def readStringArray(): Array[String] = readArray[String]()(string())
  @inline final def readColl[T, C >: Null](
      factory: Factory[T, C],
      perEntry: Int = 1
  )(f: => T): C = {
    startArray() match {
      case -1 =>
        endArray()
        null
      case rawLen =>
        val b = factory.newBuilder
        val len = rawLen / perEntry
        b.sizeHint(len)
        var i = 0
        while (i < len) { b += f; i += 1 }
        endArray()
        b.result()
    }
  }
  @inline final def readBlock[T](f: => T): T = {
    startBlock()
    val r = f
    endBlock()
    r
  }
  @inline final def readStringSeq(): Seq[String] =
    readColl[String, Vector[String]](Vector)(string())
  @inline final def readOptionalString(): Optional[String] = string() match {
    case null => Optional.empty[String]
    case s    => Optional.of(s)
  }
}

class TextSerializer(out: Writer) extends Serializer {
  private[this] final class Block(val array: Boolean, val expected: Int, var actual: Int)
  private[this] var indent = 0
  private[this] var stack: List[Block] = Nil
  private[this] def printIndent(): Unit = (0 until indent * 2).foreach(_ => out.write(' '))
  private[this] def count(): Unit =
    if (stack.nonEmpty) stack.head.actual += 1
  private[this] def println(s: String): Unit = {
    out.write(s)
    out.write('\n')
  }
  def startBlock(name: String): Unit = {
    count()
    printIndent()
    println(name + " {")
    stack = new Block(false, 0, 0) :: stack
    indent += 1
  }
  def startArray(name: String, length: Int): Unit = {
    count()
    printIndent()
    println(name + " [")
    stack = new Block(true, length max 0, 0) :: stack
    indent += 1
    printIndent()
    println(length.toString)
  }
  def endBlock(): Unit = {
    assert(stack.nonEmpty)
    val b = stack.head
    stack = stack.tail
    assert(!b.array)
    indent -= 1
    printIndent()
    println("}")
  }
  def endArray(): Unit = {
    assert(stack.nonEmpty)
    val b = stack.head
    stack = stack.tail
    assert(b.array)
    assert(b.expected == b.actual, s"Expected to write ${b.expected} values, wrote ${b.actual}")
    indent -= 1
    printIndent()
    println("]")
  }
  def string(s: String): Unit = {
    count()
    printIndent()
    if (s == null) out.write("\\0\n")
    else {
      s.foreach {
        case '\n' => out.write("\\n")
        case '\r' => out.write("\\r")
        case '\\' => out.write("\\\\")
        case c    => out.write(c.toInt)
      }
      out.write('\n')
    }
  }
  def bool(b: Boolean): Unit = long(if (b) 1 else 0)
  def int(i: Int): Unit = long(i.toLong)
  def byte(b: Byte): Unit = long(b.toLong)
  def long(l: Long): Unit = {
    count()
    printIndent()
    println(l.toString)
  }
  def end(): Unit = {
    out.flush()
    assert(stack.isEmpty && indent == 0)
  }
}

class TextDeserializer(in: BufferedReader) extends Deserializer {
  private[this] final class Block(val array: Boolean, val expected: Int, var actual: Int)
  private[this] var indent = 0
  private[this] var stack: List[Block] = Nil
  private[this] def raw(): String = in.readLine().drop(indent * 2)
  private[this] def count(): Unit =
    if (stack.nonEmpty) stack.head.actual += 1
  def startBlock(): Unit = {
    count()
    val r = raw()
    if (!r.endsWith(" {")) new IllegalStateException("Expected block header")
    indent += 1
    stack = new Block(false, 0, 0) :: stack
  }
  def startArray(): Int = {
    count()
    val r = raw()
    assert(r.endsWith(" ["), "Expected array header")
    indent += 1
    val length = raw().toInt
    stack = new Block(true, length max 0, 0) :: stack
    length
  }
  def endBlock(): Unit = {
    assert(stack.nonEmpty)
    val b = stack.head
    stack = stack.tail
    assert(!b.array)
    indent -= 1
    val r = raw()
    assert(r == "}")
  }
  def endArray(): Unit = {
    assert(stack.nonEmpty)
    val b = stack.head
    stack = stack.tail
    assert(b.array)
    assert(b.expected == b.actual, s"Expected to read ${b.expected} values, read ${b.actual}")
    indent -= 1
    val r = raw()
    assert(r == "]")
  }
  def string(): String = {
    count()
    val s = raw()
    var i = 0
    val b = new StringBuilder
    while (i < s.length) {
      s.charAt(i) match {
        case '\\' =>
          i += 1
          s.charAt(i) match {
            case '0'  => return null
            case 'n'  => b.append('\n')
            case 'r'  => b.append('\r')
            case '\\' => b.append('\\')
          }
        case c => b.append(c)
      }
      i += 1
    }
    b.result()
  }
  def bool(): Boolean = long() == 1L
  def int(): Int = long().toInt
  def byte(): Byte = long().toByte
  def long(): Long = {
    count()
    raw().toLong
  }
  def end(): Unit = assert(stack.isEmpty && indent == 0)
}

class BinarySerializer(_out: OutputStream) extends Serializer {
  private[this] val stringsMap: mutable.Map[String, Int] = mutable.Map.empty
  private[this] val buffer: Array[Byte] = new Array(65536)
  private[this] var pos: Int = 0
  // Ensure that at least `count` bytes can be written to the buffer starting at `pos`
  @inline private[this] def ensure(count: Int): Unit =
    if (pos + count > buffer.length) flush()
  // Flush unconditionally, ensuring `pos` = 0
  @inline private[this] def flush(): Unit = {
    if (pos > 0) _out.write(buffer, 0, pos)
    pos = 0
  }
  @inline private[this] def unsafeWriteByte(b: Byte): Unit = {
    buffer(pos) = b
    pos += 1
  }
  def startBlock(name: String): Unit = ()
  def endBlock(): Unit = ()
  def startArray(name: String, length: Int): Unit = int(length)
  def endArray(): Unit = ()
  def string(s: String): Unit = {
    if (s == null) int(-1)
    else if (s.isEmpty) int(0)
    else {
      val nextString = -2 - stringsMap.size
      val idx = stringsMap.getOrElseUpdate(s, nextString)
      if (idx == nextString) {
        val bytes = s.getBytes(StandardCharsets.UTF_8)
        val len = bytes.length
        int(len)
        if (len <= buffer.length) {
          ensure(len)
          System.arraycopy(bytes, 0, buffer, pos, len)
          pos += len
        } else {
          flush()
          _out.write(bytes)
        }
      } else int(idx)
    }
  }
  def bool(b: Boolean): Unit = byte(if (b) 1 else 0)
  def int(i: Int): Unit = {
    ensure(4)
    unsafeWriteByte(((i >>> 24) & 0xff).toByte)
    unsafeWriteByte(((i >>> 16) & 0xff).toByte)
    unsafeWriteByte(((i >>> 8) & 0xff).toByte)
    unsafeWriteByte((i & 0xff).toByte)
  }
  def byte(b: Byte): Unit = { ensure(1); unsafeWriteByte(b) }
  def long(l: Long): Unit = {
    ensure(8)
    unsafeWriteByte(((l >>> 56).toInt & 0xff).toByte)
    unsafeWriteByte(((l >>> 48).toInt & 0xff).toByte)
    unsafeWriteByte(((l >>> 40).toInt & 0xff).toByte)
    unsafeWriteByte(((l >>> 32).toInt & 0xff).toByte)
    unsafeWriteByte(((l >>> 24).toInt & 0xff).toByte)
    unsafeWriteByte(((l >>> 16).toInt & 0xff).toByte)
    unsafeWriteByte(((l >>> 8).toInt & 0xff).toByte)
    unsafeWriteByte((l.toInt & 0xff).toByte)
  }
  def end(): Unit = { flush(); _out.flush() }
}

class BinaryDeserializer(_in: InputStream) extends Deserializer {
  private[this] val strings: ArrayBuffer[String] = ArrayBuffer.empty
  private[this] val buffer: Array[Byte] = new Array(8192)
  private[this] var pos: Int = buffer.length
  private[this] var bufLen: Int = buffer.length
  @inline @tailrec private[this] def readAllUnderlying(
      a: Array[Byte],
      off: Int,
      len: Int,
      accum: Int = 0
  ): Int = {
    val read = _in.read(a, off, len)
    if (read == -1 && accum == 0) -1
    else if (read == -1) accum
    else if (read == len) accum + read
    else readAllUnderlying(a, off + read, len - read, accum + read)
  }
  // Ensure that there are at least `count` bytes to read in the buffer starting at `pos`
  @inline private[this] def ensure(count: Int): Unit = {
    if (pos + count > bufLen) {
      if (pos + count > buffer.length || pos >= buffer.length / 2) moveToLeft()
      while ({
        val read = _in.read(buffer, bufLen, buffer.length - bufLen)
        if (read <= 0) throw new EOFException()
        bufLen += read
        bufLen - pos < count
      }) {}
    }
  }
  // Move the data in the buffer so that `pos` = 0
  @inline private[this] def moveToLeft(): Unit = {
    val rem = bufLen - pos
    if (rem > 0 && pos > 0) System.arraycopy(buffer, pos, buffer, 0, rem)
    pos = 0
    bufLen = rem
  }
  @inline private[this] def unsafeReadByte(): Byte = {
    val b = buffer(pos)
    pos += 1
    b
  }
  @inline private[this] def readInto(a: Array[Byte]): Int = {
    var off = 0
    var len = a.length
    if (pos < bufLen) {
      val toCopy = len min (bufLen - pos)
      System.arraycopy(buffer, pos, a, 0, toCopy)
      len -= toCopy
      off += toCopy
      pos += toCopy
    }
    if (len > 0) {
      if (len >= buffer.length) off += readAllUnderlying(a, off, len)
      else {
        bufLen = readAllUnderlying(buffer, 0, buffer.length)
        val toRead = len min bufLen
        System.arraycopy(buffer, 0, a, off, toRead)
        pos = toRead
        off += toRead
      }
    }
    off
  }

  def startBlock(): Unit = ()
  def endBlock(): Unit = ()
  def startArray(): Int = int()
  def endArray(): Unit = ()
  def string(): String = int() match {
    case -1 => null
    case 0  => ""
    case len if len > 0 =>
      val s = if (len <= buffer.length) {
        ensure(len)
        val s = new String(buffer, pos, len, StandardCharsets.UTF_8)
        pos += len
        s
      } else {
        val a = new Array[Byte](len)
        val read = readInto(a)
        assert(read == len)
        new String(a, StandardCharsets.UTF_8)
      }
      strings += s
      s
    case idx =>
      strings(-2 - idx)
  }
  def bool(): Boolean = byte() != 0
  def int(): Int = {
    ensure(4)
    val i1, i2, i3, i4 = unsafeReadByte() & 0xff
    (i1 << 24) | (i2 << 16) | (i3 << 8) | i4
  }
  def byte(): Byte = {
    ensure(1)
    unsafeReadByte()
  }
  def long(): Long = {
    ensure(8)
    val i1, i2, i3, i4, i5, i6, i7, i8 = unsafeReadByte() & 0xffL
    (i1 << 56) | (i2 << 48) | (i3 << 40) | (i4 << 32) | (i5 << 24) | (i6 << 16) | (i7 << 8) | i8
  }
  def end(): Unit = ()
}

trait SerializerFactory[S <: Serializer, D <: Deserializer] {
  def serializerFor(out: OutputStream): S
  def deserializerFor(in: InputStream): D
}

object SerializerFactory {

  /** Simple human-readable text format, not self-describing, not optimized for performance. Has
   * checks for structural correctness in serializer and deserializer. */
  val text: SerializerFactory[TextSerializer, TextDeserializer] =
    new SerializerFactory[TextSerializer, TextDeserializer] {
      def serializerFor(out: OutputStream): TextSerializer =
        new TextSerializer(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)))
      def deserializerFor(in: InputStream): TextDeserializer =
        new TextDeserializer(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
    }

  /** Optimized binary format with string deduplication across multiple outputs. */
  val binary: SerializerFactory[BinarySerializer, BinaryDeserializer] =
    new SerializerFactory[BinarySerializer, BinaryDeserializer] {
      def serializerFor(out: OutputStream): BinarySerializer = new BinarySerializer(out)
      def deserializerFor(in: InputStream): BinaryDeserializer = new BinaryDeserializer(in)
    }
}
