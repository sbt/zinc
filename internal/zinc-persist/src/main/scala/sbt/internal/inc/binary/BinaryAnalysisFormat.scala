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

package sbt.internal.inc.binary

import sbt.internal.shaded.com.google.protobuf.{ CodedInputStream, CodedOutputStream }
import sbt.internal.inc.binary.converters.{ ProtobufReaders, ProtobufWriters }
import sbt.internal.inc.{ Analysis, Schema }
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{ CompileAnalysis, MiniSetup }

final class BinaryAnalysisFormat(mappers: ReadWriteMappers) {
  private final val CurrentVersion = Schema.Version.V1_1
  private final val protobufWriters = new ProtobufWriters(mappers.getWriteMapper)
  private final val protobufReaders = new ProtobufReaders(mappers.getReadMapper, CurrentVersion)

  def write(writer: CodedOutputStream, analysis0: CompileAnalysis, miniSetup: MiniSetup): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufFile = protobufWriters.toAnalysisFile(analysis, miniSetup, CurrentVersion)
    protobufFile.writeTo(writer)
    writer.flush()
  }

  def writeAPIs(
      writer: CodedOutputStream,
      analysis0: CompileAnalysis,
      shouldStoreApis: Boolean
  ): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIsFile =
      protobufWriters.toApisFile(analysis.apis, CurrentVersion, shouldStoreApis: Boolean)
    protobufAPIsFile.writeTo(writer)
    writer.flush()
  }

  def read(reader: CodedInputStream): (CompileAnalysis, MiniSetup) = {
    val protobufFile = Schema.AnalysisFile.parseFrom(reader)
    val (analysis, miniSetup, _) = protobufReaders.fromAnalysisFile(protobufFile)
    analysis -> miniSetup
  }

  def readAPIs(
      reader: CodedInputStream,
      analysis0: CompileAnalysis,
      shouldStoreApis: Boolean
  ): CompileAnalysis = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIsFile = Schema.APIsFile.parseFrom(reader)
    val (apis, _) = protobufReaders.fromApisFile(protobufAPIsFile, shouldStoreApis)
    analysis.copy(apis = apis)
  }
}
