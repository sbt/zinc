package sbt
package inc

import java.io.{ File, PrintWriter }

import sbt.io.syntax._
import sbt.io.IO
import sbt.util.Logger
import xsbti.Reporter
import xsbti.compile.JavaTools
import sbt.internal.inc.javac.JavaCompilerArguments
import sbt.internal.util.Tracked.{ inputChangedWithJson, outputChangedWithJson }
import sbt.internal.util.{ FileInfo, FilesInfo, HNil, HashFileInfo, ModifiedFileInfo, PlainFileInfo }
import sbt.internal.util.FilesInfo.{ exists, hash, lastModified }
import sbt.serialization._
import xsbti.compile.ExternalHooks.ClassFileManager

object Doc {
  // def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler): RawCompileLike.Gen =
  //   scaladoc(label, cache, compiler, Seq())
  // def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler, fileInputOptions: Seq[String]): RawCompileLike.Gen =
  //   RawCompileLike.cached(cache, fileInputOptions, RawCompileLike.prepare(label + " Scala API documentation", compiler.doc))
  def cachedJavadoc(label: String, cache: File, doc: JavaTools): JavaDoc =
    cached(cache, prepare(label + " Java API documentation", new JavaDoc {
      def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String],
        classFileManager: ClassFileManager, log: Logger, reporter: Reporter): Unit = {
        doc.javadoc.run(
          (sources filter javaSourcesOnly).toArray,
          JavaCompilerArguments(sources, classpath, Some(outputDirectory), options).toArray,
          classFileManager, reporter, log
        )
        ()
      }
    }))
  private[sbt] def prepare(description: String, doDoc: JavaDoc): JavaDoc =
    new JavaDoc {
      def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String],
        classFileManager: ClassFileManager, log: Logger, reporter: Reporter): Unit =
        if (sources.isEmpty) log.info("No sources available, skipping " + description + "...")
        else {
          log.info(description.capitalize + " to " + outputDirectory.absolutePath + "...")
          doDoc.run(sources, classpath, outputDirectory, options, null, log, reporter)
          log.info(description.capitalize + " successful.")
        }
    }
  private[sbt] def cached(cache: File, doDoc: JavaDoc): JavaDoc =
    new JavaDoc {
      def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String],
        classFileManager: ClassFileManager, log: Logger, reporter: Reporter): Unit =
        {
          val inputs = Inputs(filesInfoToList(hash(sources.toSet)), filesInfoToList(lastModified(classpath.toSet)), classpath, outputDirectory, options)
          val cachedDoc = inputChangedWithJson(cache / "inputs") { (inChanged, in: Inputs) =>
            outputChangedWithJson(cache / "output") { (outChanged, outputs: List[PlainFileInfo]) =>
              if (inChanged || outChanged) {
                IO.delete(outputDirectory)
                IO.createDirectory(outputDirectory)
                doDoc.run(sources, classpath, outputDirectory, options, null, log, reporter)
              } else log.debug("Doc uptodate: " + outputDirectory.getAbsolutePath)
            }
          }
          cachedDoc(inputs)(() => filesInfoToList(exists(outputDirectory.allPaths.get.toSet)))
        }
    }
  private[this] final case class Inputs(hfi: List[HashFileInfo], mfi: List[ModifiedFileInfo],
    classpaths: List[File], outputDirectory: File, options: List[String])
  private[this] object Inputs {
    implicit val pickler: Pickler[Inputs] with Unpickler[Inputs] = PicklerUnpickler.generate[Inputs]
  }
  private[sbt] def filesInfoToList[A <: FileInfo](info: FilesInfo[A]): List[A] =
    info.files.toList sortBy { x => x.file.getAbsolutePath }
  private[sbt] val javaSourcesOnly: File => Boolean = _.getName.endsWith(".java")

  trait JavaDoc {
    def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String],
      classFileManager: ClassFileManager, log: Logger, reporter: Reporter): Unit
  }
}
