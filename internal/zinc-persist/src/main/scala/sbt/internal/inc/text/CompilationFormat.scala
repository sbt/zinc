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

import sbinary.{ Format, Input, Output => SbinaryOutput }
import sbt.internal.inc.{ Compilation, CompileOutput }
import sbt.internal.inc.consistent.{
  BinaryDeserializer,
  BinarySerializer,
  Deserializer,
  Serializer
}
import xsbti.compile.{ Output, OutputGroup }

import java.nio.file.Paths

object CompilationFormat extends Format[Compilation] {
  def reads(in: Input): Compilation = {
    val deserializer = new BinaryDeserializer(new InputWrapperStream(in))
    val startTime = deserializer.long()
    Compilation(startTime, readOutput(deserializer))
  }

  def writes(out: SbinaryOutput, src: Compilation): Unit = {
    val serializer = new BinarySerializer(new OutputWrapperStream(out))
    serializer.long(src.getStartTime)
    writeOutput(serializer, src.getOutput)
    serializer.end()
  }

  private def writeOutput(out: Serializer, output: Output): Unit = {
    val groups = output.getMultipleOutput
    val single = output.getSingleOutputAsPath
    if (groups.isPresent) {
      out.byte(1)
      out.writeArray("outputGroups", groups.get, 2) { g =>
        out.string(g.getSourceDirectoryAsPath.toString)
        out.string(g.getOutputDirectoryAsPath.toString)
      }
    } else if (single.isPresent) {
      out.byte(0)
      out.string(single.get.toString)
    } else out.byte(2)
  }

  private def readOutput(in: Deserializer): Output = in.byte() match {
    case 0 => CompileOutput(Paths.get(in.string()))
    case 1 =>
      val groups = in.readArray[OutputGroup](2) {
        CompileOutput.outputGroup(Paths.get(in.string()), Paths.get(in.string()))
      }
      CompileOutput(groups)
    case 2     => CompileOutput.empty
    case other => throw new java.io.IOException(s"Unexpected output tag: $other")
  }
}
