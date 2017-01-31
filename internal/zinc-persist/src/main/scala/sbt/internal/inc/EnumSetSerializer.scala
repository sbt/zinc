package sbt.internal.inc

import java.util

import scala.reflect.ClassTag

case class EnumSetSerializer[E <: Enum[E]: ClassTag](allValues: Array[E]) {
  assert(allValues.size <= 6, s"EnumSetSerializer can only support up to 6 values (but got $allValues).")

  private val enumClass = implicitly[ClassTag[E]].runtimeClass.asInstanceOf[Class[E]]

  private val masks = allValues.zipWithIndex.map {
    case (v, i) =>
      v -> (1 << i)
  }

  private val OffsetInASCI = '!'.toInt

  def write(set: util.EnumSet[E]): Char = {
    var flags = 0
    for ((v, mask) <- masks if set.contains(v)) flags |= mask
    (flags + OffsetInASCI).toChar
  }

  def read(c: Char): util.EnumSet[E] = {
    val set = util.EnumSet.noneOf(enumClass)
    val bits = c.toInt - OffsetInASCI
    for ((v, mask) <- masks if (bits & mask) != 0) set.add(v)
    set
  }
}
