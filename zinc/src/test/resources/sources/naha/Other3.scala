/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package naha

object Other3 {
  def implicitMember = "implicitMemberValue"
  def standardMember = "standardMemberValue"

  Other2.otherSealed match {
    case OtherSealed2 => 1
    case _            => 2
  }

}
