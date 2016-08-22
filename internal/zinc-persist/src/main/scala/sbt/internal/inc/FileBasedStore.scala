/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package internal
package inc

import java.io._
import java.util.zip.ZipEntry
import sbt.io.{ IO, Using }
import xsbti.compile.{ CompileAnalysis, MiniSetup }

object FileBasedStore {
  def apply(file: File): AnalysisStore = new AnalysisStore {

    private val entryFileName = "cache.txt"

    def set(analysis: CompileAnalysis, setup: MiniSetup): Unit = {
      if (!file.getParentFile.exists()) file.getParentFile.mkdirs()
      Using.zipOutputStream(new FileOutputStream(file)) {
        outputStream =>
          val reader = new BufferedWriter(new OutputStreamWriter(outputStream, IO.utf8))
          outputStream.putNextEntry(new ZipEntry(entryFileName))
          TextAnalysisFormat.write(reader, analysis, setup)
          outputStream.closeEntry()
      }
    }

    def get(): Option[(CompileAnalysis, MiniSetup)] =
      try {
        Some(getUncaught())
      } catch {
        case _: IOException =>
          readOldFormat()
        case _: Exception => None
      }

    def getUncaught(): (CompileAnalysis, MiniSetup) =
      Using.zipInputStream(new FileInputStream(file)) {
        inputStream =>
          inputStream.getNextEntry()
          val writer = new BufferedReader(new InputStreamReader(inputStream, IO.utf8))
          TextAnalysisFormat.read(writer)
      }

    def readOldFormat(): Option[(CompileAnalysis, MiniSetup)] = try
      Some(Using.fileReader(IO.utf8)(file) { reader => TextAnalysisFormat.read(reader) })
    catch {
      case _: Throwable => None
    }

  }
}
