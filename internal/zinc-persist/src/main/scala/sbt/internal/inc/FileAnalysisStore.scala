/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io._
import java.util.zip.{ ZipEntry, ZipInputStream }

import com.google.protobuf.{ CodedInputStream, CodedOutputStream }
import sbt.inc.ReadWriteMappers
import sbt.io.{ IO, Using }
import xsbti.compile.{ CompileAnalysis, MiniSetup }
import xsbti.api.Companions

import scala.util.control.Exception.allCatch

object FileAnalysisStore {
  private final val analysisFileName = "inc_compile.txt"
  private final val companionsFileName = "api_companions.txt"

  def apply(analysisFile: File): AnalysisStore = binary(analysisFile)
  def apply(analysisFile: File, mappers: ReadWriteMappers): AnalysisStore =
    binary(analysisFile, mappers)

  private[inc] def binary(analysisFile: File): AnalysisStore =
    new BinaryFileStore(analysisFile, ReadWriteMappers.getEmptyMappers())
  private[inc] def binary(analysisFile: File, mappers: ReadWriteMappers): AnalysisStore =
    new BinaryFileStore(analysisFile, mappers)

  private final class BinaryFileStore(file: File, readWriteMappers: ReadWriteMappers)
      extends AnalysisStore {

    private final val format = new BinaryAnalysisFormat(readWriteMappers)
    private final val TmpEnding = ".tmp"

    /**
     * Get `CompileAnalysis` and `MiniSetup` instances for current `Analysis`.
     */
    override def get: Option[(CompileAnalysis, MiniSetup)] = {
      val nestedRead = allCatch.opt {
        Using.zipInputStream(new FileInputStream(file)) { inputStream =>
          lookupEntry(inputStream, analysisFileName)
          val reader = CodedInputStream.newInstance(inputStream)
          val (analysis, miniSetup) = format.read(reader)
          val analysisWithAPIs = allCatch.opt {
            lookupEntry(inputStream, companionsFileName)
            format.readAPIs(reader, analysis)
          }
          analysisWithAPIs.map(analysis => analysis -> miniSetup)
        }
      }
      nestedRead.flatten
    }

    /**
     * Write the zipped analysis contents into a temporary file before
     * overwriting the old analysis file and avoiding data race conditions.
     *
     * See https://github.com/sbt/zinc/issues/220 for more details.
     */
    override def set(analysis: CompileAnalysis, setup: MiniSetup): Unit = {
      val tmpAnalysisFile = File.createTempFile(file.getName, TmpEnding)
      if (!file.getParentFile.exists())
        file.getParentFile.mkdirs()

      val outputStream = new FileOutputStream(tmpAnalysisFile)
      Using.zipOutputStream(outputStream) { outputStream =>
        val protobufWriter = CodedOutputStream.newInstance(outputStream)
        outputStream.putNextEntry(new ZipEntry(analysisFileName))
        format.write(protobufWriter, analysis, setup)
        outputStream.closeEntry()

        if (setup.storeApis()) {
          outputStream.putNextEntry(new ZipEntry(companionsFileName))
          format.writeAPIs(protobufWriter, analysis)
          outputStream.closeEntry()
        }
      }
      IO.move(tmpAnalysisFile, file)
    }
  }

  private def lookupEntry(in: ZipInputStream, name: String): Unit = {
    Option(in.getNextEntry) match {
      case Some(entry) if entry.getName == name => ()
      case Some(_)                              => lookupEntry(in, name)
      case None                                 => sys.error(s"$name not found in the zip file")
    }
  }
}
