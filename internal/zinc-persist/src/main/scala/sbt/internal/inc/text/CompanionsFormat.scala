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

package sbt.internal.inc.text

import sbinary._
import sbt.internal.inc.consistent.{
  BinaryDeserializer,
  BinarySerializer,
  ConsistentAnalysisFormat
}
import xsbti.api._
import xsbti.compile.analysis.ReadWriteMappers

object CompanionsFormat extends Format[Companions] {
  private val structural =
    new ConsistentAnalysisFormat(ReadWriteMappers.getEmptyMappers(), reproducible = false)

  def reads(in: Input): Companions =
    structural.readCompanions(new BinaryDeserializer(new InputWrapperStream(in)))

  def writes(out: Output, src: Companions): Unit = {
    val serializer = new BinarySerializer(new OutputWrapperStream(out))
    structural.writeCompanions(serializer, src)
    serializer.end()
  }
}
