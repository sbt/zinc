/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package naha

object ClientWithImplicitUsed {
  def add(nr: Any)(implicit no: ImplicitWrapper[_]) = nr.toString + no.a.toString

  val vals: Seq[Any] = Seq(NormalDependecy.implicitMember, NormalDependecy.standardMember)

  import WithImplicits._
  vals.map(add)
}