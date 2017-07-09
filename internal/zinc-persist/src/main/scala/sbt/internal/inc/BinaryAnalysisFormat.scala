/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import com.google.protobuf.{ CodedInputStream, CodedOutputStream }
import sbt.inc.ReadWriteMappers
import sbt.internal.inc.converters.{ ProtobufReaders, ProtobufWriters }
import xsbti.compile.{ CompileAnalysis, MiniSetup }

final class BinaryAnalysisFormat(mappers: ReadWriteMappers) {
  private final val CurrentVersion = schema.Version.V1
  private final val protobufWriters = new ProtobufWriters(mappers.getWriteMapper)
  private final val protobufReaders = new ProtobufReaders(mappers.getReadMapper)

  def write(writer: CodedOutputStream, analysis0: CompileAnalysis, miniSetup: MiniSetup): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufFile = protobufWriters.toAnalysisFile(analysis, miniSetup, CurrentVersion)
    protobufFile.writeTo(writer)
    writer.flush()
  }

  def writeAPIs(writer: CodedOutputStream, analysis0: CompileAnalysis): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIsFile = protobufWriters.toApisFile(analysis.apis, CurrentVersion)
    protobufAPIsFile.writeTo(writer)
    writer.flush()
  }

  def read(reader: CodedInputStream): (CompileAnalysis, MiniSetup) = {
    val protobufFile = schema.AnalysisFile.parseFrom(reader)
    val (analysis, miniSetup, _) = protobufReaders.fromAnalysisFile(protobufFile)
    analysis -> miniSetup
  }

  def readAPIs(reader: CodedInputStream, analysis0: CompileAnalysis): CompileAnalysis = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIsFile = schema.APIsFile.parseFrom(reader)
    val (apis, _) = protobufReaders.fromApisFile(protobufAPIsFile)
    analysis.copy(apis = apis)
  }
}
