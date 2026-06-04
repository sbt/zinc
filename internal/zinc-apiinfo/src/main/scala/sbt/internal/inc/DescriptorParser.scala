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

import xsbti.api

/**
 * Parses JVM field and method descriptors into xsbti.api.Type values without
 * loading the referenced classes, avoiding ClassNotFoundException when
 * transitive dependencies are absent from the analysis classpath.
 *
 * Descriptor grammar (JVMS §4.3):
 *   FieldDescriptor  ::= FieldType
 *   MethodDescriptor ::= '(' FieldType* ')' ReturnDescriptor
 *   FieldType        ::= BaseType | ObjectType | ArrayType
 *   BaseType         ::= 'B'|'C'|'D'|'F'|'I'|'J'|'S'|'Z'
 *   ObjectType       ::= 'L' ClassName ';'
 *   ArrayType        ::= '[' ComponentType
 *   ReturnDescriptor ::= FieldType | 'V'
 *
 * Generic signatures (the Signature attribute) are not parsed; erased types
 * from the descriptor are used instead. This is sufficient for incremental
 * compilation: binary-incompatible changes to generic signatures still change
 * the erased descriptor and trigger recompilation.
 */
private[inc] object DescriptorParser {

  private val Empty: api.Type = api.EmptyType.of()
  private val ThisRef: api.PathComponent = api.This.of()
  private val ArrayRef: api.Type = reference("scala.Array")

  private val primitives: Map[Char, api.Type] = Map(
    'B' -> reference("scala.Byte"),
    'C' -> reference("scala.Char"),
    'D' -> reference("scala.Double"),
    'F' -> reference("scala.Float"),
    'I' -> reference("scala.Int"),
    'J' -> reference("scala.Long"),
    'S' -> reference("scala.Short"),
    'Z' -> reference("scala.Boolean"),
  )

  /** Parse a field descriptor such as {@code Ljava/lang/String;} or {@code I} or {@code [B}. */
  def fieldType(descriptor: String): api.Type =
    parseType(descriptor, 0)._1

  /**
   * Parse a method descriptor such as {@code ([Ljava/lang/String;)V}.
   * @return (parameter types, return type); return type is EmptyType for void.
   */
  def methodTypes(descriptor: String): (Array[api.Type], api.Type) = {
    val closeParen = descriptor.indexOf(')')
    if (!descriptor.startsWith("(") || closeParen < 0)
      throw new IllegalArgumentException(s"Not a valid method descriptor: $descriptor")
    val params = parseParamTypes(descriptor, 1, closeParen)
    val retDesc = descriptor.substring(closeParen + 1)
    val ret = if (retDesc == "V") Empty else parseType(retDesc, 0)._1
    (params, ret)
  }

  private def parseParamTypes(desc: String, from: Int, until: Int): Array[api.Type] = {
    val buf = new scala.collection.mutable.ArrayBuffer[api.Type]()
    var i = from
    while (i < until) {
      val (tpe, next) = parseType(desc, i)
      buf += tpe
      i = next
    }
    buf.toArray
  }

  private def parseType(desc: String, i: Int): (api.Type, Int) =
    desc(i) match {
      case c if primitives.contains(c) => (primitives(c), i + 1)
      case 'V'                         => (Empty, i + 1)
      case '[' =>
        val (component, next) = parseType(desc, i + 1)
        (api.Parameterized.of(ArrayRef, Array(component)), next)
      case 'L' =>
        val end = desc.indexOf(';', i + 1)
        if (end < 0) throw new IllegalArgumentException(s"Unterminated object type in: $desc")
        val className = desc.substring(i + 1, end).replace('/', '.')
        (reference(className), end + 1)
      case c =>
        throw new IllegalArgumentException(s"Unknown descriptor char '$c' at $i in: $desc")
    }

  private[inc] def reference(dotted: String): api.Type = {
    val lastDot = dotted.lastIndexOf('.')
    if (lastDot < 0)
      api.Projection.of(Empty, dotted)
    else
      api.Projection.of(
        api.Singleton.of(pathFromString(dotted.substring(0, lastDot))),
        dotted.substring(lastDot + 1)
      )
  }

  private def pathFromString(pkg: String): api.Path = {
    val parts: Array[api.PathComponent] =
      pkg.split("\\.").map(api.Id.of(_).asInstanceOf[api.PathComponent]) :+ ThisRef
    api.Path.of(parts)
  }
}
