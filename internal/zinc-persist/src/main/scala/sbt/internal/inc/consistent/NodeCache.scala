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

package sbt.internal.inc.consistent

import xsbti.api.Annotated
import xsbti.api.Annotation
import xsbti.api.AnnotationArgument
import xsbti.api.Constant
import xsbti.api.Existential
import xsbti.api.ParameterRef
import xsbti.api.Parameterized
import xsbti.api.Polymorphic
import xsbti.api.Projection
import xsbti.api.Singleton
import xsbti.api.TypeParameter

private[consistent] final class NodeCache {
  private var hashes = new Array[Long](16)
  private var values = new Array[AnyRef](16)
  private var entries = 0

  def intern[A <: AnyRef](value: A): A = {
    val hash = NodeCache.fingerprint(value)
    var index = NodeCache.index(hash, values.length)
    var existing = values(index)
    while (existing != null && (hashes(index) != hash || !value.equals(existing))) {
      index = (index + 1) & (values.length - 1)
      existing = values(index)
    }
    if (existing == null) {
      if (entries * 2 >= values.length) {
        grow()
        index = NodeCache.index(hash, values.length)
        while (values(index) != null) index = (index + 1) & (values.length - 1)
      }
      hashes(index) = hash
      values(index) = value
      entries += 1
      value
    } else existing.asInstanceOf[A]
  }

  private def grow(): Unit = {
    val oldHashes = hashes
    val oldValues = values
    hashes = new Array[Long](oldHashes.length * 2)
    values = new Array[AnyRef](oldValues.length * 2)
    var oldIndex = 0
    while (oldIndex < oldValues.length) {
      val value = oldValues(oldIndex)
      if (value != null) {
        val hash = oldHashes(oldIndex)
        var index = NodeCache.index(hash, values.length)
        while (values(index) != null) index = (index + 1) & (values.length - 1)
        hashes(index) = hash
        values(index) = value
      }
      oldIndex += 1
    }
  }
}

private[consistent] object NodeCache {
  private final val ParameterRefTag = 1L
  private final val ParameterizedTag = 2L
  private final val PolymorphicTag = 3L
  private final val ConstantTag = 4L
  private final val ExistentialTag = 5L
  private final val SingletonTag = 6L
  private final val ProjectionTag = 7L
  private final val AnnotatedTag = 8L
  private final val TypeParameterTag = 9L
  private final val AnnotationTag = 10L

  // FNV-1a 64-bit offset basis and prime.
  private final val Fnv1a64OffsetBasis = -3750763034362895579L
  private final val Fnv1a64Prime = 1099511628211L

  // MurmurHash3 fmix64 multipliers.
  private final val MurmurHash3Fmix64Multiplier1 = -49064778989728563L
  private final val MurmurHash3Fmix64Multiplier2 = -4265267296055464877L

  private def mix(hash: Long, value: Long): Long = (hash ^ value) * Fnv1a64Prime

  private def mixReference(hash: Long, value: AnyRef): Long =
    mix(hash, if (value == null) 0L else Integer.toUnsignedLong(System.identityHashCode(value)))

  private def mixReferences(hash: Long, entries: Array[? <: AnyRef]): Long = {
    if (entries == null) mix(hash, -1L)
    else {
      var result = mix(hash, entries.length.toLong)
      var index = 0
      while (index < entries.length) {
        result = mixReference(result, entries(index))
        index += 1
      }
      result
    }
  }

  private def mixArguments(hash: Long, entries: Array[AnnotationArgument]): Long = {
    if (entries == null) mix(hash, -1L)
    else {
      var result = mix(hash, entries.length.toLong)
      var index = 0
      while (index < entries.length) {
        val argument = entries(index)
        result = mixReference(result, argument.name())
        result = mixReference(result, argument.value())
        index += 1
      }
      result
    }
  }

  private[consistent] def fingerprint(value: AnyRef): Long = value match {
    case node: ParameterRef => mixReference(mix(Fnv1a64OffsetBasis, ParameterRefTag), node.id())
    case node: Parameterized =>
      mixReferences(
        mixReference(mix(Fnv1a64OffsetBasis, ParameterizedTag), node.baseType()),
        node.typeArguments()
      )
    case node: Polymorphic =>
      mixReferences(
        mixReference(mix(Fnv1a64OffsetBasis, PolymorphicTag), node.baseType()),
        node.parameters()
      )
    case node: Constant =>
      mixReference(
        mixReference(mix(Fnv1a64OffsetBasis, ConstantTag), node.baseType()),
        node.value()
      )
    case node: Existential =>
      mixReferences(
        mixReference(mix(Fnv1a64OffsetBasis, ExistentialTag), node.baseType()),
        node.clause()
      )
    case node: Singleton => mixReference(mix(Fnv1a64OffsetBasis, SingletonTag), node.path())
    case node: Projection =>
      mixReference(
        mixReference(mix(Fnv1a64OffsetBasis, ProjectionTag), node.prefix()),
        node.id()
      )
    case node: Annotated =>
      mixReferences(
        mixReference(mix(Fnv1a64OffsetBasis, AnnotatedTag), node.baseType()),
        node.annotations()
      )
    case node: TypeParameter =>
      val withId = mixReference(mix(Fnv1a64OffsetBasis, TypeParameterTag), node.id())
      val withAnnotations = mixReferences(withId, node.annotations())
      val withParameters = mixReferences(withAnnotations, node.typeParameters())
      val withVariance = mix(withParameters, node.variance().ordinal().toLong)
      mixReference(mixReference(withVariance, node.lowerBound()), node.upperBound())
    case node: Annotation =>
      mixArguments(
        mixReference(mix(Fnv1a64OffsetBasis, AnnotationTag), node.base()),
        node.arguments()
      )
    case node => mix(Fnv1a64OffsetBasis, Integer.toUnsignedLong(node.hashCode()))
  }

  private def index(hash: Long, length: Int): Int = {
    var mixed = hash
    mixed = (mixed ^ (mixed >>> 33)) * MurmurHash3Fmix64Multiplier1
    mixed = (mixed ^ (mixed >>> 33)) * MurmurHash3Fmix64Multiplier2
    ((mixed ^ (mixed >>> 33)).toInt & Int.MaxValue) & (length - 1)
  }
}
