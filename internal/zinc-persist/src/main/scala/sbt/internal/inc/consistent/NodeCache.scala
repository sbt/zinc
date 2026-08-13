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
  private final val Offset = -3750763034362895579L
  private final val Prime = 1099511628211L

  private def mix(hash: Long, value: Long): Long = (hash ^ value) * Prime

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
    case node: ParameterRef => mixReference(mix(Offset, 1L), node.id())
    case node: Parameterized =>
      mixReferences(mixReference(mix(Offset, 2L), node.baseType()), node.typeArguments())
    case node: Polymorphic =>
      mixReferences(mixReference(mix(Offset, 3L), node.baseType()), node.parameters())
    case node: Constant =>
      mixReference(mixReference(mix(Offset, 4L), node.baseType()), node.value())
    case node: Existential =>
      mixReferences(mixReference(mix(Offset, 5L), node.baseType()), node.clause())
    case node: Singleton => mixReference(mix(Offset, 6L), node.path())
    case node: Projection =>
      mixReference(mixReference(mix(Offset, 7L), node.prefix()), node.id())
    case node: Annotated =>
      mixReferences(mixReference(mix(Offset, 8L), node.baseType()), node.annotations())
    case node: TypeParameter =>
      val withId = mixReference(mix(Offset, 9L), node.id())
      val withAnnotations = mixReferences(withId, node.annotations())
      val withParameters = mixReferences(withAnnotations, node.typeParameters())
      val withVariance = mix(withParameters, node.variance().ordinal().toLong)
      mixReference(mixReference(withVariance, node.lowerBound()), node.upperBound())
    case node: Annotation =>
      mixArguments(mixReference(mix(Offset, 10L), node.base()), node.arguments())
    case node => mix(Offset, Integer.toUnsignedLong(node.hashCode()))
  }

  private def index(hash: Long, length: Int): Int = {
    var mixed = hash
    mixed = (mixed ^ (mixed >>> 33)) * -49064778989728563L
    mixed = (mixed ^ (mixed >>> 33)) * -4265267296055464877L
    ((mixed ^ (mixed >>> 33)).toInt & Int.MaxValue) & (length - 1)
  }
}
