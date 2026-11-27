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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*

object SignatureParserSpecification extends Properties:
  import SignatureModel.*

  override def tests: List[Test] = List(
    example("SignatureParser should parse a parameterized field type", parameterizedField),
    example(
      "parse type variables, arrays, and primitive-element arrays",
      typeVariablesAndArrays
    ),
    example("parse wildcards (unbounded, extends, super)", wildcards),
    example("parse an inner class with type arguments on the outer", innerClassOuterArgs),
    example(
      "parse a class signature with type params, superclass, and interface",
      classSignature
    ),
    example(
      "parse an interface-only bounded type parameter (the '::' case)",
      interfaceOnlyBound
    ),
    example(
      "parse a generic method signature with params, return, and throws",
      genericMethod
    ),
    example("parse a void result", voidResult),
    example("return None on malformed or truncated input", malformed),
    example("reject trailing input after a complete signature", trailingInput),
    example(
      "reject empty identifiers and empty type-argument/parameter lists",
      emptyIdentifiers
    ),
    example("parse multiple bounds on a type parameter", multipleBounds),
    example("parse a type-variable throws and reject an array throws", throwsClauses),
    example(
      "parse type arguments on both the outer and inner of an inner class",
      innerClassBothArgs
    ),
  )

  private def cls(name: String, args: SigArg*): SigClass = SigClass(name, args.toList, Nil)
  private def inv(t: SigType): SigArg = SigBounded('=', t)
  private val Str = cls("java.lang.String")
  private val Obj = cls("java.lang.Object")

  private def parameterizedField: Result =
    SignatureParser.fieldSignature("Ljava/util/List<Ljava/lang/String;>;") ====
      Some(cls("java.util.List", inv(Str)))

  private def typeVariablesAndArrays: Result =
    Result.all(
      List(
        SignatureParser.fieldSignature("TT;") ==== Some(SigVar("T")),
        SignatureParser.fieldSignature("[Ljava/lang/String;") ==== Some(SigArray(Str)),
        SignatureParser.fieldSignature("[[I") ==== Some(SigArray(SigArray(SigPrimitive('I')))),
      )
    )

  private def wildcards: Result =
    Result.all(
      List(
        SignatureParser.fieldSignature("Ljava/util/List<*>;") ====
          Some(cls("java.util.List", SigWildcard)),
        SignatureParser.fieldSignature("Ljava/util/List<+Ljava/lang/Number;>;") ====
          Some(cls("java.util.List", SigBounded('+', cls("java.lang.Number")))),
        SignatureParser.fieldSignature("Ljava/util/List<-Ljava/lang/Integer;>;") ====
          Some(cls("java.util.List", SigBounded('-', cls("java.lang.Integer")))),
      )
    )

  private def innerClassOuterArgs: Result =
    SignatureParser.fieldSignature("Lpkg/Outer<Ljava/lang/String;>.Inner;") ====
      Some(SigClass("pkg.Outer", List(inv(Str)), List(SigInner("Inner", Nil))))

  private def classSignature: Result =
    val sig = "<T:Ljava/lang/Object;>Ljava/lang/Object;Ljava/lang/Comparable<TT;>;"
    SignatureParser.classSignature(sig) ==== Some(
      ClassSignature(
        List(SigTypeParam("T", List(Obj))),
        Obj,
        List(cls("java.lang.Comparable", inv(SigVar("T"))))
      )
    )

  private def interfaceOnlyBound: Result =
    val sig = "<T::Ljava/lang/Comparable<TT;>;>Ljava/lang/Object;"
    SignatureParser.classSignature(sig).map(_.typeParams) ====
      Some(List(SigTypeParam("T", List(cls("java.lang.Comparable", inv(SigVar("T")))))))

  private def genericMethod: Result =
    val sig = "<T:Ljava/lang/Object;>(TT;I)Ljava/util/List<TT;>;^Ljava/io/IOException;"
    SignatureParser.methodSignature(sig) ==== Some(
      MethodSignature(
        List(SigTypeParam("T", List(Obj))),
        List(SigVar("T"), SigPrimitive('I')),
        cls("java.util.List", inv(SigVar("T"))),
        List(cls("java.io.IOException"))
      )
    )

  private def voidResult: Result =
    SignatureParser.methodSignature("()V").map(_.result) ==== Some(SigVoid)

  private def malformed: Result =
    Result.all(
      List(
        SignatureParser.fieldSignature("not-a-signature") ==== None,
        SignatureParser.fieldSignature("Ljava/util/List<") ==== None,
        SignatureParser.methodSignature("(I") ==== None,
      )
    )

  private def trailingInput: Result =
    Result.all(
      List(
        SignatureParser.fieldSignature("Ljava/lang/String;xxx") ==== None,
        SignatureParser.methodSignature("()Vxxx") ==== None,
      )
    )

  private def emptyIdentifiers: Result =
    Result.all(
      List(
        (SignatureParser.fieldSignature("L;") ==== None).log("empty class name"),
        (SignatureParser.fieldSignature("T;") ==== None).log("empty type-variable name"),
        (SignatureParser.fieldSignature("Ljava/util/List<>;") ==== None).log("empty type args"),
        (SignatureParser.classSignature("<>Ljava/lang/Object;") ==== None)
          .log("empty type params"),
      )
    )

  private def multipleBounds: Result =
    val sig = "<T:Ljava/lang/Object;:Ljava/lang/Comparable<TT;>;>Ljava/lang/Object;"
    SignatureParser.classSignature(sig).map(_.typeParams) ====
      Some(List(SigTypeParam("T", List(Obj, cls("java.lang.Comparable", inv(SigVar("T")))))))

  private def throwsClauses: Result =
    val tv = "<T:Ljava/lang/Throwable;>()V^TT;"
    Result.all(
      List(
        SignatureParser.methodSignature(tv).map(_.throws) ==== Some(List(SigVar("T"))),
        SignatureParser.methodSignature("()V^[Ljava/lang/Exception;") ==== None,
      )
    )

  private def innerClassBothArgs: Result =
    val k = inv(SigVar("K"))
    val v = inv(SigVar("V"))
    SignatureParser.fieldSignature("Ljava/util/Map<TK;TV;>.Entry<TK;TV;>;") ====
      Some(SigClass("java.util.Map", List(k, v), List(SigInner("Entry", List(k, v)))))
end SignatureParserSpecification
