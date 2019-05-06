/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package naha

object ClientWithImplicitUsed {
  def add(nr: Any)(implicit no: ImplicitWrapper[_]) = nr.toString + no.a.toString

  val vals: Seq[Any] = Seq(NormalDependecy.implicitMember, NormalDependecy.standardMember)

  import WithImplicits._
  vals.map(add)
}