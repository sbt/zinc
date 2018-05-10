/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.binary

import com.google.protobuf.{ CodedInputStream, CodedOutputStream }
import sbt.internal.inc.binary.converters.{ ProtobufReaders, ProtobufWriters }
import sbt.internal.inc.{ Analysis, schema }
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{ CompileAnalysis, MiniSetup }

final class BinaryAnalysisFormat(mappers: ReadWriteMappers) {
  private final val CurrentVersion = schema.Version.V1_1
  private final val protobufWriters = new ProtobufWriters(mappers.getWriteMapper)
  private final val protobufReaders = new ProtobufReaders(mappers.getReadMapper, CurrentVersion)

  def write(writer: CodedOutputStream, analysis0: CompileAnalysis, miniSetup: MiniSetup): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufFile = protobufWriters.toAnalysisFile(analysis, miniSetup, CurrentVersion)
    protobufFile.writeTo(writer)
    writer.flush()
  }

  def writeAPIs(writer: CodedOutputStream,
                analysis0: CompileAnalysis,
                shouldStoreApis: Boolean): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIsFile =
      protobufWriters.toApisFile(analysis.apis, CurrentVersion, shouldStoreApis: Boolean)
    protobufAPIsFile.writeTo(writer)
    writer.flush()
  }

  def read(reader: CodedInputStream): (CompileAnalysis, MiniSetup) = {
    val protobufFile = schema.AnalysisFile.parseFrom(reader)
    val (analysis, miniSetup, _) = protobufReaders.fromAnalysisFile(protobufFile)
    analysis -> miniSetup
  }

  def readAPIs(reader: CodedInputStream,
               analysis0: CompileAnalysis,
               shouldStoreApis: Boolean): CompileAnalysis = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIsFile = schema.APIsFile.parseFrom(reader)
    val (apis, _) = protobufReaders.fromApisFile(protobufAPIsFile, shouldStoreApis)
    analysis.copy(apis = apis)
  }
}
