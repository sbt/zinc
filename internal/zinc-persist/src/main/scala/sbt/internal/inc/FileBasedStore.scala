/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package internal
package inc

import java.io._
import java.util.zip.{ ZipInputStream, ZipEntry }
import sbt.io.{ IO, Using }
import xsbti.compile.{ CompileAnalysis, MiniSetup }
import xsbti.api.Companions
import scala.util.control.Exception.allCatch

object FileBasedStore {
  private val analysisFileName = "inc_compile.txt"
  private val companionsFileName = "api_companions.txt"

  def apply(file: File): AnalysisStore = new FileBasedStoreImpl(file)

  private final class FileBasedStoreImpl(file: File) extends AnalysisStore {
    val companionsStore = new FileBasedCompanionsMapStore(file)

    def set(analysis: CompileAnalysis, setup: MiniSetup): Unit = {
      if (!file.getParentFile.exists()) file.getParentFile.mkdirs()
      Using.zipOutputStream(new FileOutputStream(file)) {
        outputStream =>
          val writer = new BufferedWriter(new OutputStreamWriter(outputStream, IO.utf8))
          outputStream.putNextEntry(new ZipEntry(analysisFileName))
          TextAnalysisFormat.write(writer, analysis, setup)
          outputStream.closeEntry()
          if (setup.storeApis()) {
            outputStream.putNextEntry(new ZipEntry(companionsFileName))
            TextAnalysisFormat.writeCompanionMap(writer, analysis match { case a: Analysis => a.apis })
            outputStream.closeEntry()
          }
      }
    }

    def get(): Option[(CompileAnalysis, MiniSetup)] =
      allCatch.opt(getUncaught())

    def getUncaught(): (CompileAnalysis, MiniSetup) =
      Using.zipInputStream(new FileInputStream(file)) {
        inputStream =>
          lookupEntry(inputStream, analysisFileName)
          val writer = new BufferedReader(new InputStreamReader(inputStream, IO.utf8))
          TextAnalysisFormat.read(writer, companionsStore)
      }
  }

  private def lookupEntry(in: ZipInputStream, name: String): Unit =
    Option(in.getNextEntry) match {
      case Some(entry) if entry.getName == name => ()
      case Some(entry)                          => lookupEntry(in, name)
      case None                                 => sys.error(s"$name not found in the zip file")
    }

  private final class FileBasedCompanionsMapStore(file: File) extends CompanionsStore {
    def get(): Option[(Map[String, Companions], Map[String, Companions])] =
      allCatch.opt(getUncaught())
    def getUncaught(): (Map[String, Companions], Map[String, Companions]) =
      Using.zipInputStream(new FileInputStream(file)) {
        inputStream =>
          lookupEntry(inputStream, companionsFileName)
          val reader = new BufferedReader(new InputStreamReader(inputStream, IO.utf8))
          TextAnalysisFormat.readCompanionMap(reader)
      }
  }
}
