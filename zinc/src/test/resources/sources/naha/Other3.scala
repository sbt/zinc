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

package naha

object Other3 {
  def implicitMember = "implicitMemberValue"
  def standardMember = "standardMemberValue"

  Other2.otherSealed match {
    case OtherSealed2 => 1
    case _            => 2
  }

}
