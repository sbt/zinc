package sbt.internal.inc

import com.google.protobuf.{ CodedInputStream, CodedOutputStream }
import sbt.internal.inc.converters.{ ProtobufReaders, ProtobufWriters }
import xsbti.compile.{ CompileAnalysis, MiniSetup }

object BinaryAnalysisFormat {
  def write(writer: CodedOutputStream, analysis0: CompileAnalysis, miniSetup: MiniSetup): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufFile = ProtobufWriters.toAnalysisFile(analysis, miniSetup)
    protobufFile.writeTo(writer)
    writer.flush()
  }

  def writeAPIs(writer: CodedOutputStream, analysis0: CompileAnalysis): Unit = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIs = ProtobufWriters.toApis(analysis.apis)
    protobufAPIs.writeTo(writer)
    writer.flush()
  }

  def read(reader: CodedInputStream): (CompileAnalysis, MiniSetup) = {
    val protobufFile = schema.AnalysisFile.parseFrom(reader)
    val (analysis, miniSetup) = ProtobufReaders.fromAnalysisFile(protobufFile)
    analysis -> miniSetup
  }

  def readAPIs(reader: CodedInputStream, analysis0: CompileAnalysis): CompileAnalysis = {
    val analysis = analysis0 match { case analysis: Analysis => analysis }
    val protobufAPIs = schema.APIs.parseFrom(reader)
    val apis = ProtobufReaders.fromApis(protobufAPIs)
    analysis.copy(apis = apis)
  }
}
