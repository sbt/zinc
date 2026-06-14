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

class SignatureParserSpecification extends UnitSpec {
  import SignatureModel._

  private def cls(name: String, args: SigArg*): SigClass = SigClass(name, args.toList, Nil)
  private def inv(t: SigType): SigArg = SigBounded('=', t)
  private val Str = cls("java.lang.String")
  private val Obj = cls("java.lang.Object")

  "SignatureParser" should "parse a parameterized field type" in {
    assert(
      SignatureParser.fieldSignature("Ljava/util/List<Ljava/lang/String;>;")
        == Some(cls("java.util.List", inv(Str)))
    )
  }

  it should "parse type variables, arrays, and primitive-element arrays" in {
    assert(SignatureParser.fieldSignature("TT;") == Some(SigVar("T")))
    assert(SignatureParser.fieldSignature("[Ljava/lang/String;") == Some(SigArray(Str)))
    assert(SignatureParser.fieldSignature("[[I") == Some(SigArray(SigArray(SigPrimitive('I')))))
  }

  it should "parse wildcards (unbounded, extends, super)" in {
    assert(
      SignatureParser.fieldSignature("Ljava/util/List<*>;")
        == Some(cls("java.util.List", SigWildcard))
    )
    assert(
      SignatureParser.fieldSignature("Ljava/util/List<+Ljava/lang/Number;>;")
        == Some(cls("java.util.List", SigBounded('+', cls("java.lang.Number"))))
    )
    assert(
      SignatureParser.fieldSignature("Ljava/util/List<-Ljava/lang/Integer;>;")
        == Some(cls("java.util.List", SigBounded('-', cls("java.lang.Integer"))))
    )
  }

  it should "parse an inner class with type arguments on the outer" in {
    assert(
      SignatureParser.fieldSignature("Lpkg/Outer<Ljava/lang/String;>.Inner;")
        == Some(SigClass("pkg.Outer", List(inv(Str)), List(SigInner("Inner", Nil))))
    )
  }

  it should "parse a class signature with type params, superclass, and interface" in {
    val sig = "<T:Ljava/lang/Object;>Ljava/lang/Object;Ljava/lang/Comparable<TT;>;"
    assert(
      SignatureParser.classSignature(sig) == Some(
        ClassSignature(
          List(SigTypeParam("T", List(Obj))),
          Obj,
          List(cls("java.lang.Comparable", inv(SigVar("T"))))
        )
      )
    )
  }

  it should "parse an interface-only bounded type parameter (the '::' case)" in {
    val sig = "<T::Ljava/lang/Comparable<TT;>;>Ljava/lang/Object;"
    assert(
      SignatureParser.classSignature(sig).map(_.typeParams)
        == Some(List(SigTypeParam("T", List(cls("java.lang.Comparable", inv(SigVar("T")))))))
    )
  }

  it should "parse a generic method signature with params, return, and throws" in {
    val sig = "<T:Ljava/lang/Object;>(TT;I)Ljava/util/List<TT;>;^Ljava/io/IOException;"
    assert(
      SignatureParser.methodSignature(sig) == Some(
        MethodSignature(
          List(SigTypeParam("T", List(Obj))),
          List(SigVar("T"), SigPrimitive('I')),
          cls("java.util.List", inv(SigVar("T"))),
          List(cls("java.io.IOException"))
        )
      )
    )
  }

  it should "parse a void result" in {
    assert(SignatureParser.methodSignature("()V").map(_.result) == Some(SigVoid))
  }

  it should "return None on malformed or truncated input" in {
    assert(SignatureParser.fieldSignature("not-a-signature").isEmpty)
    assert(SignatureParser.fieldSignature("Ljava/util/List<").isEmpty)
    assert(SignatureParser.methodSignature("(I").isEmpty)
  }

  it should "reject trailing input after a complete signature" in {
    assert(SignatureParser.fieldSignature("Ljava/lang/String;xxx").isEmpty)
    assert(SignatureParser.methodSignature("()Vxxx").isEmpty)
  }

  it should "reject empty identifiers and empty type-argument/parameter lists" in {
    assert(SignatureParser.fieldSignature("L;").isEmpty) // empty class name
    assert(SignatureParser.fieldSignature("T;").isEmpty) // empty type-variable name
    assert(SignatureParser.fieldSignature("Ljava/util/List<>;").isEmpty) // empty type args
    assert(SignatureParser.classSignature("<>Ljava/lang/Object;").isEmpty) // empty type params
  }

  it should "parse multiple bounds on a type parameter" in {
    val sig = "<T:Ljava/lang/Object;:Ljava/lang/Comparable<TT;>;>Ljava/lang/Object;"
    assert(
      SignatureParser.classSignature(sig).map(_.typeParams)
        == Some(List(SigTypeParam("T", List(Obj, cls("java.lang.Comparable", inv(SigVar("T")))))))
    )
  }

  it should "parse a type-variable throws and reject an array throws" in {
    val tv = "<T:Ljava/lang/Throwable;>()V^TT;"
    assert(SignatureParser.methodSignature(tv).map(_.throws) == Some(List(SigVar("T"))))
    assert(SignatureParser.methodSignature("()V^[Ljava/lang/Exception;").isEmpty)
  }

  it should "parse type arguments on both the outer and inner of an inner class" in {
    val k = inv(SigVar("K"))
    val v = inv(SigVar("V"))
    assert(
      SignatureParser.fieldSignature("Ljava/util/Map<TK;TV;>.Entry<TK;TV;>;")
        == Some(SigClass("java.util.Map", List(k, v), List(SigInner("Entry", List(k, v)))))
    )
  }
}
