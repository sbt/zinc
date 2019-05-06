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

package sbt.internal.inc

import java.io.File

case class SimpleOutputGroup(getSourceDirectory: File, getOutputDirectory: File)
    extends xsbti.compile.OutputGroup {
  override def toString = s"OutputGroup($getSourceDirectory -> $getOutputDirectory)"
}
