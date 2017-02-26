/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc

import java.io.File

import sbt.io.syntax._
import sbt.io.IO
import sbt.util.Logger
import xsbti.compile.{ IncToolOptions, JavaTools }
import sbt.internal.inc.javac.JavaCompilerArguments
import sbt.internal.util.Tracked.inputChanged
import sbt.internal.util.{ CacheStoreFactory, FileInfo, FilesInfo, HashFileInfo, ModifiedFileInfo, PlainFileInfo }
import sbt.internal.util.CacheImplicits._
import sbt.internal.util.FileInfo.{ exists, hash, lastModified }
import exists._
import sjsonnew._
import LList.:*:
import xsbti.Reporter

object Doc {
  private[this] implicit val IsoInputs = LList.iso(
    { in: Inputs => ("outputDirectory", in.outputDirectory) :*: LNil },
    { in: File :*: LNil => Inputs(Nil, Nil, Nil, in.head, Nil) }
  )

  // def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler): RawCompileLike.Gen =
  //   scaladoc(label, cache, compiler, Seq())
  // def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler, fileInputOptions: Seq[String]): RawCompileLike.Gen =
  //   RawCompileLike.cached(cache, fileInputOptions, RawCompileLike.prepare(label + " Scala API documentation", compiler.doc))
  def cachedJavadoc(label: String, storeFactory: CacheStoreFactory, doc: JavaTools): JavaDoc =
    cached(storeFactory, prepare(label + " Java API documentation", new JavaDoc {
      def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String],
        incToolOptions: IncToolOptions, log: Logger, reporter: Reporter): Unit = {
        doc.javadoc.run(
          (sources filter javaSourcesOnly).toArray,
          JavaCompilerArguments(sources, classpath, Some(outputDirectory), options).toArray,
          incToolOptions, reporter, log
        )
        ()
      }
    }))
  private[sbt] def prepare(description: String, doDoc: JavaDoc): JavaDoc =
    new JavaDoc {
      def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String],
        incToolOptions: IncToolOptions, log: Logger, reporter: Reporter): Unit =
        if (sources.isEmpty) log.info("No sources available, skipping " + description + "...")
        else {
          log.info(description.capitalize + " to " + outputDirectory.absolutePath + "...")
          doDoc.run(sources, classpath, outputDirectory, options, incToolOptions, log, reporter)
          log.info(description.capitalize + " successful.")
        }
    }
  private[sbt] def cached(storeFactory: CacheStoreFactory, doDoc: JavaDoc): JavaDoc =
    new JavaDoc {
      def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String],
        incToolOptions: IncToolOptions, log: Logger, reporter: Reporter): Unit =
        {
          val inputs = Inputs(filesInfoToList(hash(sources.toSet)), filesInfoToList(lastModified(classpath.toSet)), classpath, outputDirectory, options)
          val cachedDoc = inputChanged(storeFactory derive "inputs") { (inChanged, in: Inputs) =>
            inputChanged(storeFactory derive "output") { (outChanged, outputs: List[PlainFileInfo]) =>
              if (inChanged || outChanged) {
                IO.delete(outputDirectory)
                IO.createDirectory(outputDirectory)
                doDoc.run(sources, classpath, outputDirectory, options, incToolOptions, log, reporter)
              } else log.debug("Doc uptodate: " + outputDirectory.getAbsolutePath)
            }
          }
          cachedDoc(inputs)(filesInfoToList(exists(outputDirectory.allPaths.get.toSet)))
        }
    }
  private[this] final case class Inputs(hfi: List[HashFileInfo], mfi: List[ModifiedFileInfo],
    classpaths: List[File], outputDirectory: File, options: List[String])
  private[sbt] def filesInfoToList[A <: FileInfo](info: FilesInfo[A]): List[A] =
    info.files.toList sortBy { x => x.file.getAbsolutePath }
  private[sbt] val javaSourcesOnly: File => Boolean = _.getName.endsWith(".java")

  trait JavaDoc {
    def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String],
      incToolOptions: IncToolOptions, log: Logger, reporter: Reporter): Unit
  }
}
