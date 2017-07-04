package sbt.internal.inc

import com.google.protobuf.{ CodedInputStream, CodedOutputStream }
import sbt.internal.inc.converters.{ ProtobufReaders, ProtobufWriters }
import xsbti.compile.{ CompileAnalysis, MiniSetup }

object BinaryAnalysisFormat {
  private final val CurrentVersion = schema.Version.V1
  def write(writer: CodedOutputStream, analysis0: CompileAnalysis, miniSetup: MiniSetup): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufFile = ProtobufWriters.toAnalysisFile(analysis, miniSetup, CurrentVersion)
    protobufFile.writeTo(writer)
    writer.flush()
  }

  def writeAPIs(writer: CodedOutputStream, analysis0: CompileAnalysis): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIsFile = ProtobufWriters.toApisFile(analysis.apis, CurrentVersion)
    protobufAPIsFile.writeTo(writer)
    writer.flush()
  }

  def read(reader: CodedInputStream): (CompileAnalysis, MiniSetup) = {
    val protobufFile = schema.AnalysisFile.parseFrom(reader)
    val (analysis, miniSetup, _) = ProtobufReaders.fromAnalysisFile(protobufFile)
    analysis -> miniSetup
  }

  def readAPIs(reader: CodedInputStream, analysis0: CompileAnalysis): CompileAnalysis = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIsFile = schema.APIsFile.parseFrom(reader)
    val (apis, _) = ProtobufReaders.fromApisFile(protobufAPIsFile)
    analysis.copy(apis = apis)
  }
}
