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
import sbt.internal.inc.consistent.*
import java.io.*

object ConsistentAnalysisFormatSuite extends Properties:
  override def tests: List[Test] = List(
    example("TextSerializer", textSerializer),
    example("BinarySerializer", binarySerializer),
    property("TextSerializer round-trips any values", propRoundTrip(text)),
    property("BinarySerializer round-trips any values", propRoundTrip(binary)),
  )

  def writeTo(out: Serializer): Unit =
    out.int(0)
    out.int(Int.MinValue)
    out.int(Int.MaxValue)
    out.long(0)
    out.long(Long.MinValue)
    out.long(Long.MaxValue)
    out.byte(0)
    out.byte(Byte.MinValue)
    out.byte(Byte.MaxValue)
    out.bool(false)
    out.bool(true)
    out.string(null)
    out.string("abc")
    out.string("ab\r\n\\c")
    out.writeBlock("block") {
      out.int(42)
      out.int(43)
    }
    out.writeColl("c1", null)(out.int)
    out.writeColl("c2", Nil)(out.int)
    out.writeColl("c3", Seq(1, 2, 3))(out.int)
    out.writeColl("c4", Seq(1, 2, 3), 2) { i => out.int(i); out.int(i * 2) }
    out.end()
  end writeTo

  def readFrom(in: Deserializer): Result =
    val i1, i2, i3 = in.int()
    val l1, l2, l3 = in.long()
    val b1, b2, b3 = in.byte()
    val p1, p2 = in.bool()
    val s1, s2, s3 = in.string()
    val (i4, i5) = in.readBlock((in.int(), in.int()))
    val c1, c2, c3 = in.readColl[Int, Seq[Int]](Seq)(in.int())
    val c4 = in.readColl[(Int, Int), Seq[(Int, Int)]](Seq, 2)((in.int(), in.int()))
    Result.all(
      List(
        i1 ==== 0,
        i2 ==== Int.MinValue,
        i3 ==== Int.MaxValue,
        l1 ==== 0L,
        l2 ==== Long.MinValue,
        l3 ==== Long.MaxValue,
        b1 ==== 0.toByte,
        b2 ==== Byte.MinValue,
        b3 ==== Byte.MaxValue,
        p1 ==== false,
        p2 ==== true,
        s1 ==== null,
        s2 ==== "abc",
        s3 ==== "ab\r\n\\c",
        i4 ==== 42,
        i5 ==== 43,
        c1 ==== null,
        Result.assert(c2.isEmpty),
        c3 ==== Seq(1, 2, 3),
        c4 ==== Seq((1, 2), (2, 4), (3, 6)),
      )
    )
  end readFrom

  private def textSerializer: Result =
    val out = new StringWriter()
    writeTo(new TextSerializer(out))
    readFrom(new TextDeserializer(new BufferedReader(new StringReader(out.toString))))

  private def binarySerializer: Result =
    val out = new ByteArrayOutputStream()
    writeTo(SerializerFactory.binary.serializerFor(out))
    readFrom(SerializerFactory.binary.deserializerFor(new ByteArrayInputStream(out.toByteArray)))

  private enum Value:
    case IntValue(value: Int)
    case LongValue(value: Long)
    case ByteValue(value: Byte)
    case BoolValue(value: Boolean)
    case StringValue(value: String)
    case IntColl(values: List[Int])

  import Value.*

  private def write(out: Serializer, value: Value): Unit = value match
    case IntValue(v)    => out.int(v)
    case LongValue(v)   => out.long(v)
    case ByteValue(v)   => out.byte(v)
    case BoolValue(v)   => out.bool(v)
    case StringValue(v) => out.string(v)
    case IntColl(vs)    => out.writeColl("c", vs)(out.int)

  private def read(in: Deserializer, like: Value): Value = like match
    case IntValue(_)    => IntValue(in.int())
    case LongValue(_)   => LongValue(in.long())
    case ByteValue(_)   => ByteValue(in.byte())
    case BoolValue(_)   => BoolValue(in.bool())
    case StringValue(_) => StringValue(in.string())
    case IntColl(_)     => IntColl(in.readColl[Int, List[Int]](List)(in.int()))

  private val genString: Gen[String] =
    Gen.frequency1(
      1 -> Gen.constant(null),
      9 -> Gen.string(
        Gen.frequency1(
          6 -> Gen.unicode,
          1 -> Gen.element1('\r', '\n', '\\', '"', ' ', '\t', ':')
        ),
        Range.linear(0, 32)
      ),
    )

  private val genValue: Gen[Value] =
    Gen.choice1(
      Gen.int(Range.linearFrom(0, Int.MinValue, Int.MaxValue)).map(IntValue(_)),
      Gen.long(Range.linearFrom(0L, Long.MinValue, Long.MaxValue)).map(LongValue(_)),
      Gen.byte(Range.linearFrom(0.toByte, Byte.MinValue, Byte.MaxValue)).map(ByteValue(_)),
      Gen.boolean.map(BoolValue(_)),
      genString.map(StringValue(_)),
      Gen.list(Gen.int(Range.linear(-1000, 1000)), Range.linear(0, 5)).map(IntColl(_)),
    )

  private trait Format:
    def roundTrip(write: Serializer => Unit, read: Deserializer => List[Value]): List[Value]

  private val text: Format = (write, read) =>
    val out = new StringWriter()
    write(new TextSerializer(out))
    read(new TextDeserializer(new BufferedReader(new StringReader(out.toString))))

  private val binary: Format = (write, read) =>
    val out = new ByteArrayOutputStream()
    write(SerializerFactory.binary.serializerFor(out))
    read(SerializerFactory.binary.deserializerFor(new ByteArrayInputStream(out.toByteArray)))

  private def propRoundTrip(format: Format): Property =
    for values <- Gen.list(genValue, Range.linear(0, 20)).forAll
    yield
      def writeAll(out: Serializer): Unit =
        values.foreach(write(out, _))
        out.end()
      format.roundTrip(writeAll, in => values.map(read(in, _))) ==== values
end ConsistentAnalysisFormatSuite
