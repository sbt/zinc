/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package naha

object Other {
  def implicitMember = "implicitMemberValue"
  def standardMember = "standardMemberValue"
}

trait MarkerTrait

sealed class OtherSealed
object OtherSealed1 extends OtherSealed
object OtherSealed2 extends OtherSealed
