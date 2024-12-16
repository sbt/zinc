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

package sbt.inc

object SourceFiles {
  val Good = "Good.scala"
  val Foo = "Foo.scala"
  val NestedJavaClasses = "NestedJavaClasses.java"

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
      ClientWithImplicitUsed,
      ClientWithImplicitNotUsed,
      ClientWithoutImplicit,
      ClientWithoutAnythingUsed,
      NormalDependecy,
      WithImplicits,
      Other,
      Other2,
      Other3
    )
  }
}
