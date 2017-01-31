/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc

object SourceFiles {
  val Good = "Good.scala"
  val Foo = "Foo.scala"
  object Naha {
    val ClientWithImplicitUsed = "ClientWithImplicitUsed.scala"
    val ClientWithImplicitNotUsed = "ClientWithImplicitNotUsed.scala"
    val ClientWithoutImplicit = "ClientWithoutImplicit.scala"
    val ClientWithoutAnythingUsed = "ClientWithoutAnythingUsed.scala"

    val NormalDependecy = "NormalDependecy.scala"
    val WithImplicits = "WithImplicits.scala"
    val Other = "Other.scala"
    val Other2 = "Other2.scala"
    val Other3 = "Other3.scala"

    val all = Seq(
      ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit, ClientWithoutAnythingUsed,
      NormalDependecy, WithImplicits, Other, Other2, Other3
    )
  }
}
