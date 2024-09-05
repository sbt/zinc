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

package sbt
package internal
package inc

import java.io._
import java.nio.file.Files
import java.util.Optional
import java.util.zip.{ ZipEntry, ZipInputStream }
import sbt.internal.inc.text.TextAnalysisFormat
import sbt.io.{ IO, Using }
import xsbti.api.Companions
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{ AnalysisContents, AnalysisStore => XAnalysisStore }

import scala.annotation.tailrec
import scala.util.control.Exception.allCatch

object FileAnalysisStore {
  private final val BinExtension = "bin"
  private final val analysisFileName = s"inc_compile.$BinExtension"
  private final val companionsFileName = s"api_companions.$BinExtension"
  private final val defaultTmpDir = new File(System.getProperty("java.io.tmpdir"))

  def text(file: File): XAnalysisStore =
    text(file, TextAnalysisFormat)
  def text(file: File, mappers: ReadWriteMappers): XAnalysisStore =
    text(file, new TextAnalysisFormat(mappers))
  def text(file: File, format: TextAnalysisFormat): XAnalysisStore =
    text(file, format, defaultTmpDir)
  def text(file: File, format: TextAnalysisFormat, tmpDir: File): XAnalysisStore =
    new FileBasedStoreImpl(file, format, tmpDir)

  def binary(file: File): XAnalysisStore =
    consistent.ConsistentFileAnalysisStore.binary(file)
  def binary(file: File, mappers: ReadWriteMappers): XAnalysisStore =
    consistent.ConsistentFileAnalysisStore.binary(file, mappers)

  private final class FileBasedStoreImpl(file: File, format: TextAnalysisFormat, tmpDir: File)
      extends XAnalysisStore {
    val companionsStore = new FileBasedCompanionsMapStore(file, format)

    def set(analysisContents: AnalysisContents): Unit = {
      val analysis = analysisContents.getAnalysis
      val setup = analysisContents.getMiniSetup
      val tmpAnalysisFile = Files.createTempFile(tmpDir.toPath, file.getName, ".tmp").toFile
      if (!file.getParentFile.exists()) file.getParentFile.mkdirs()
      Using.zipOutputStream(new FileOutputStream(tmpAnalysisFile)) { outputStream =>
        val writer = new BufferedWriter(new OutputStreamWriter(outputStream, IO.utf8))
        outputStream.putNextEntry(new ZipEntry(analysisFileName))
        format.write(writer, analysis, setup)
        outputStream.closeEntry()
        if (setup.storeApis()) {
          outputStream.putNextEntry(new ZipEntry(companionsFileName))
          format.writeCompanionMap(writer, analysis match { case a: Analysis => a.apis })
          outputStream.closeEntry()
        }
      }
      IO.move(tmpAnalysisFile, file)
    }

    def get(): Optional[AnalysisContents] = {
      import JavaInterfaceUtil.EnrichOption
      allCatch.opt(unsafeGet()).toOptional
    }

    def unsafeGet(): AnalysisContents =
      Using.zipInputStream(new FileInputStream(file)) { inputStream =>
        lookupEntry(inputStream, analysisFileName)
        val writer = new BufferedReader(new InputStreamReader(inputStream, IO.utf8))
        val (analysis, setup) = format.read(writer, companionsStore)
        AnalysisContents.create(analysis, setup)
      }
  }

  @tailrec
  private def lookupEntry(in: ZipInputStream, name: String): Unit =
    Option(in.getNextEntry) match {
      case Some(entry) if entry.getName == name => ()
      case Some(_)                              => lookupEntry(in, name)
      case None                                 => sys.error(s"$name not found in the zip file")
    }

  private final class FileBasedCompanionsMapStore(file: File, format: TextAnalysisFormat)
      extends CompanionsStore {
    def get(): Option[(Map[String, Companions], Map[String, Companions])] =
      allCatch.opt(getUncaught())
    def getUncaught(): (Map[String, Companions], Map[String, Companions]) =
      Using.zipInputStream(new FileInputStream(file)) { inputStream =>
        lookupEntry(inputStream, companionsFileName)
        val reader = new BufferedReader(new InputStreamReader(inputStream, IO.utf8))
        format.readCompanionMap(reader)
      }
  }
}
