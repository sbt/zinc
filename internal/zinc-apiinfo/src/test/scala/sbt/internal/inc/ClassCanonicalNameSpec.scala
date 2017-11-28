/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import org.scalatest._

// All eight combinations of class/object nesting to three levels.
// O-O-O means object { object { object } }
// C-C-C means class { class { class } }
// etc..
// Because of scala/bug#2034 we can't put these all in one package (you get "name clash" errors)
// So instead we'll split them in 4 nice and even packages
package p1 { object x { object y { object z; class z } } }
package p2 { object x { class y { object z; class z } } }
package p3 { class x { object y { object z; class z } } }
package p4 { class x { class y { object z; class z } } }

class ClassCanonicalNameSpec extends FlatSpec with Matchers {
  "ClassToAPI.classCanonicalName" should """return "" for null""" in
    assert(getCustomCanonicalName(null) === "")

  import scala.reflect._
  def check[T: ClassTag](expected: Expected) = checkClass(expected, classTag[T].runtimeClass, "tag")
  def checkRef(expected: Expected, x: AnyRef) = checkClass(expected, x.getClass, "ref")
  def checkClass(expected: Expected, c: Class[_], classSource: String) = {
    import expected._
    it should f"for $nesting%-5s ($classSource) return $canonicalClassName" in assert(
      getCustomCanonicalName(c) === canonicalClassName &&
        getNativeCanonicalName(c) === nativeCanonicalClassName
    )
  }

  def getCustomCanonicalName(c: Class[_]) = strip(ClassToAPI.classCanonicalName(c))
  def getNativeCanonicalName(c: Class[_]) = strip(handleMalformed(c.getCanonicalName))

  // Yes, that's the only way to write these types.
  import scala.language.existentials

  check[p1.x.type](O)
  check[p3.x](C)

  check[p1.x.y.type](OO)
  check[p2.x.y](OC)
  check[c1.y.type forSome { val c1: p3.x }](CO)
  check[p4.x#y](CC)

  check[p1.x.y.z.type](OOO)
  check[p1.x.y.z](OOC)
  check[c2.z.type forSome { val c2: p2.x.y }](OCO)
  check[p2.x.y#z](OCC)
  check[c1.y.z.type forSome { val c1: p3.x }](COO)
  check[c1.y.z forSome { val c1: p3.x }](COC)
  check[c2.z.type forSome { val c2: p4.x#y }](CCO)
  check[p4.x#y#z](CCC)

  // Now again, but calling getClass on an instance instead
  // of using the type to summon a class object. This matches
  // against the same nestings as above.
  checkRef(O, p1.x)
  checkRef(C, new p3.x)

  checkRef(OO, p1.x.y)
  checkRef(OC, new p2.x.y)
  checkRef(CO, { val c1 = new p3.x; c1.y })
  checkRef(CC, { val c1 = new p4.x; new c1.y })

  checkRef(OOO, p1.x.y.z)
  checkRef(OOC, new p1.x.y.z)
  checkRef(OCO, { val c2 = new p2.x.y; c2.z })
  checkRef(OCC, { val c2 = new p2.x.y; new c2.z })
  checkRef(COO, { val c1 = new p3.x; c1.y.z })
  checkRef(COC, { val c1 = new p3.x; new c1.y.z })
  checkRef(CCO, { val c1 = new p4.x; val c2 = new c1.y; c2.z })
  checkRef(CCC, { val c1 = new p4.x; val c2 = new c1.y; new c2.z })

  object O extends Expected("x$")
  object C extends Expected("x")

  object OO extends Expected("x.y$")
  object OC extends Expected("x.y")
  object CO extends Expected("x.y$")
  object CC extends Expected("x.y")

  object OOO extends Expected("x.y$$z$", nativeClassNameIsMalformed = true)
  object OOC extends Expected("x.y$$z", nativeClassNameIsMalformed = true)
  object OCO extends Expected("x.y.z$")
  object OCC extends Expected("x.y.z")
  object COO extends Expected("x.y$$z$", nativeClassNameIsMalformed = true)
  object COC extends Expected("x.y$$z", nativeClassNameIsMalformed = true)
  object CCO extends Expected("x.y.z$")
  object CCC extends Expected("x.y.z")

  class Expected(
      val canonicalClassName: String,
      val nativeClassNameIsMalformed: Boolean = false
  ) {
    val nesting = {
      val n0 = getClass.getSimpleName stripSuffix "$"
      Seq(n0, "-" * (n0.length - 1)).flatMap(_.zipWithIndex).sortBy(_._2).map(_._1).mkString
    }

    val nativeCanonicalClassName =
      if (nativeClassNameIsMalformed) "Malformed class name" else canonicalClassName
  }

  def handleMalformed(s: => String) =
    try s
    catch {
      case e: InternalError if e.getMessage == "Malformed class name" => "Malformed class name"
    }

  // Strip the shared prefix to the class name
  def strip(s: String) = {
    val prefix = "sbt.internal.inc.p"
    if (s startsWith prefix) s stripPrefix prefix drop 2
    else s
  }
}
