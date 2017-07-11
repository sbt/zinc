/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.text

import java.util

import scala.reflect.ClassTag

case class EnumSetSerializer[E <: Enum[E]: ClassTag](allValues: Array[E]) {
  assert(allValues.size <= 6,
         s"EnumSetSerializer can only support up to 6 values (but got $allValues).")

  private val enumClass = implicitly[ClassTag[E]].runtimeClass.asInstanceOf[Class[E]]

  private val masks = allValues.zipWithIndex.map {
    case (v, i) =>
      v -> (1 << i)
  }

  private val OffsetInASCII = 33 // byte value of '!'

  def serialize(set: util.EnumSet[E]): Char = {
    var flags = 0
    for ((v, mask) <- masks if set.contains(v)) flags |= mask
    (flags + OffsetInASCII).toChar
  }

  def deserialize(c: Char): util.EnumSet[E] = {
    val set = util.EnumSet.noneOf(enumClass)
    val bits = c.toInt - OffsetInASCII
    for ((v, mask) <- masks if (bits & mask) != 0) set.add(v)
    set
  }
}
