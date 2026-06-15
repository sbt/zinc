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
package classfile

import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

/**
 * A neutral AST for JVMS §4.7.9.1 generic signatures. Kept independent of `xsbti.api` so the parser
 * stays pure syntax; the mapping to `xsbti.api` types lives in `ClassfileToAPI` (zinc-apiinfo),
 * which reuses `ClassToAPI`'s type helpers. Used by Phase 3 of the classfile-based Java API work
 * (docs/design/classfile-based-java-api.md) to recover the generic types that erased descriptors
 * drop.
 */
private[sbt] object SignatureModel {

  /** A type that can appear as a field type, parameter, return, bound, or type argument. */
  sealed trait SigType

  /** `Lpkg/Outer<args>.Inner<args>;` — `name` is the dotted outer class name; `inners` the suffixes. */
  final case class SigClass(name: String, args: List[SigArg], inners: List[SigInner])
      extends SigType

  /** `TX;` — a reference to a type variable named `X`. */
  final case class SigVar(name: String) extends SigType

  /** `[elem` — an array type. */
  final case class SigArray(elem: SigType) extends SigType

  /** `B C D F I J S Z` — a primitive (the JVM type descriptor character). */
  final case class SigPrimitive(tag: Char) extends SigType

  /** `V` — only valid as a method result. */
  case object SigVoid extends SigType

  /** A `.Inner<args>` suffix of a [[SigClass]]. */
  final case class SigInner(name: String, args: List[SigArg])

  /** A type argument inside `<...>`. */
  sealed trait SigArg
  case object SigWildcard extends SigArg // `*` (unbounded)

  /** A type argument with a variance marker: `=` invariant, `+` `? extends`, `-` `? super`. */
  final case class SigBounded(variance: Char, tpe: SigType) extends SigArg

  /** A declared type parameter with its (class + interface) bounds. */
  final case class SigTypeParam(name: String, bounds: List[SigType])

  final case class ClassSignature(
      typeParams: List[SigTypeParam],
      superclass: SigType,
      interfaces: List[SigType]
  )
  final case class MethodSignature(
      typeParams: List[SigTypeParam],
      params: List[SigType],
      result: SigType,
      throws: List[SigType]
  )
}

/**
 * Recursive-descent parser for JVMS §4.7.9.1 signatures. Entry points are fail-soft (return `None`
 * on malformed input) so callers can degrade gracefully.
 */
private[sbt] object SignatureParser {
  import SignatureModel._

  def fieldSignature(s: String): Option[SigType] =
    tryParse(s)(c => parseReferenceType(c))

  def classSignature(s: String): Option[ClassSignature] =
    tryParse(s) { c =>
      val typeParams = if (c.peek == '<') parseTypeParams(c) else Nil
      val superclass = parseClassType(c)
      val interfaces = ListBuffer.empty[SigType]
      while (!c.atEnd) interfaces += parseClassType(c)
      ClassSignature(typeParams, superclass, interfaces.toList)
    }

  def methodSignature(s: String): Option[MethodSignature] =
    tryParse(s) { c =>
      val typeParams = if (c.peek == '<') parseTypeParams(c) else Nil
      c.expect('(')
      val params = ListBuffer.empty[SigType]
      while (c.peek != ')') params += parseJavaType(c)
      c.expect(')')
      val result = if (c.peek == 'V') { c.next(); SigVoid }
      else parseJavaType(c)
      val throws = ListBuffer.empty[SigType]
      while (!c.atEnd && c.peek == '^') { c.next(); throws += parseThrowsSignature(c) }
      MethodSignature(typeParams, params.toList, result, throws.toList)
    }

  private def tryParse[T](s: String)(p: Cursor => T): Option[T] =
    try {
      val c = new Cursor(s)
      val result = p(c)
      if (!c.atEnd) sys.error(s"trailing input in $s") // must consume the whole signature
      Some(result)
    } catch { case NonFatal(_) => None } // malformed signatures -> None; fatal errors propagate

  // JavaTypeSignature: ReferenceTypeSignature | BaseType
  private def parseJavaType(c: Cursor): SigType =
    c.peek match {
      case 'B' | 'C' | 'D' | 'F' | 'I' | 'J' | 'S' | 'Z' => SigPrimitive(c.next())
      case _                                             => parseReferenceType(c)
    }

  // ReferenceTypeSignature: ClassTypeSignature | TypeVariableSignature | ArrayTypeSignature
  private def parseReferenceType(c: Cursor): SigType =
    c.peek match {
      case 'L'   => parseClassType(c)
      case 'T'   => parseTypeVar(c)
      case '['   => c.next(); SigArray(parseJavaType(c))
      case other => sys.error(s"unexpected signature char '$other'")
    }

  // TypeVariableSignature: T Identifier ;
  private def parseTypeVar(c: Cursor): SigVar = {
    c.expect('T')
    val name = c.readIdentifier()
    c.expect(';')
    SigVar(name)
  }

  // ThrowsSignature: ^ (ClassTypeSignature | TypeVariableSignature) — no arrays/primitives.
  private def parseThrowsSignature(c: Cursor): SigType =
    c.peek match {
      case 'L'   => parseClassType(c)
      case 'T'   => parseTypeVar(c)
      case other => sys.error(s"invalid throws signature char '$other'")
    }

  // ClassTypeSignature: L [pkg/] Identifier [<args>] {. Identifier [<args>]} ;
  private def parseClassType(c: Cursor): SigClass = {
    c.expect('L')
    var name = c.readIdentifier()
    while (c.peek == '/') { c.next(); name = name + "." + c.readIdentifier() }
    val args = if (c.peek == '<') parseTypeArgs(c) else Nil
    val inners = ListBuffer.empty[SigInner]
    while (c.peek == '.') {
      c.next()
      val innerName = c.readIdentifier()
      val innerArgs = if (c.peek == '<') parseTypeArgs(c) else Nil
      inners += SigInner(innerName, innerArgs)
    }
    c.expect(';')
    SigClass(name, args, inners.toList)
  }

  // TypeArguments: < TypeArgument+ >
  private def parseTypeArgs(c: Cursor): List[SigArg] = {
    c.expect('<')
    val args = ListBuffer.empty[SigArg]
    while (c.peek != '>') args += parseTypeArg(c)
    c.expect('>')
    if (args.isEmpty) sys.error("empty type arguments") // JVMS requires at least one
    args.toList
  }

  // TypeArgument: [+|-] ReferenceTypeSignature | *
  private def parseTypeArg(c: Cursor): SigArg =
    c.peek match {
      case '*'             => c.next(); SigWildcard
      case v @ ('+' | '-') => c.next(); SigBounded(v, parseReferenceType(c))
      case _               => SigBounded('=', parseReferenceType(c))
    }

  // TypeParameters: < (Identifier ClassBound InterfaceBound*)+ >
  private def parseTypeParams(c: Cursor): List[SigTypeParam] = {
    c.expect('<')
    val params = ListBuffer.empty[SigTypeParam]
    while (c.peek != '>') {
      val name = c.readIdentifier()
      val bounds = ListBuffer.empty[SigType]
      c.expect(':') // ClassBound colon; the class bound itself may be absent
      if (c.peek != ':' && c.peek != '>') bounds += parseReferenceType(c)
      while (c.peek == ':') { c.next(); bounds += parseReferenceType(c) } // InterfaceBound*
      params += SigTypeParam(name, bounds.toList)
    }
    c.expect('>')
    if (params.isEmpty) sys.error("empty type parameters") // JVMS requires at least one
    params.toList
  }

  /** A delimiter character that cannot appear in a signature identifier (JVMS §4.7.9.1). */
  private def isDelimiter(ch: Char): Boolean = ch match {
    case '<' | '>' | '.' | ':' | ';' | '/' | '[' => true
    case _                                       => false
  }

  private final class Cursor(s: String) {
    private var idx = 0
    def atEnd: Boolean = idx >= s.length
    def peek: Char = s.charAt(idx)
    def next(): Char = { val ch = s.charAt(idx); idx += 1; ch }
    def expect(ch: Char): Unit = {
      if (atEnd || peek != ch) sys.error(s"expected '$ch' at $idx in $s")
      idx += 1
    }
    def readIdentifier(): String = {
      val start = idx
      while (!atEnd && !isDelimiter(peek)) idx += 1
      if (idx == start) sys.error(s"empty identifier at $idx in $s") // identifiers are non-empty
      s.substring(start, idx)
    }
  }
}
