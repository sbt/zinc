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

package sbt.inc

import java.io.File
import java.nio.file.{ Files, Path }

import sbt.io.syntax._
import sbt.util.Logger
import xsbti.{ FileConverter, VirtualFile }
import xsbti.compile.{ IncToolOptions, JavaTools }
import sbt.internal.inc.CompileOutput
import sbt.internal.inc.javac.JavaCompilerArguments
import sbt.util.Tracked.inputChanged
import sbt.util.{ CacheStoreFactory, FileInfo, FilesInfo, ModifiedFileInfo, PlainFileInfo }
import sbt.util.CacheImplicits._
import sbt.util.FileInfo.{ exists, lastModified }
import exists._
import sjsonnew._
import xsbti.Reporter

object Doc {
  private[this] implicit val IsoInputs = LList.iso(
    { in: Inputs =>
      ("getOutputDirectory", in.outputDirectory.toFile) :*: LNil
    }, { in: File :*: LNil =>
      Inputs(Nil, Nil, Nil, in.head.toPath, Nil)
    }
  )

  // def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler): RawCompileLike.Gen =
  //   scaladoc(label, cache, compiler, Seq())
  // def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler, fileInputOptions: Seq[String]): RawCompileLike.Gen =
  //   RawCompileLike.cached(cache, fileInputOptions, RawCompileLike.prepare(label + " Scala API documentation", compiler.doc))
  def cachedJavadoc(label: String, storeFactory: CacheStoreFactory, doc: JavaTools): JavaDoc =
    cached(
      storeFactory,
      prepare(
        label + " Java API documentation",
        new JavaDoc {
          def run(
              sources: List[VirtualFile],
              classpath: List[VirtualFile],
              converter: FileConverter,
              outputDirectory: Path,
              options: List[String],
              incToolOptions: IncToolOptions,
              log: Logger,
              reporter: Reporter
          ): Unit = {
            val success = doc.javadoc.run(
              (sources filter javaSourcesOnly).toArray,
              JavaCompilerArguments(Nil, classpath, options).toArray,
              CompileOutput(outputDirectory),
              incToolOptions,
              reporter,
              log
            )
            if (success) ()
            else throw new JavadocGenerationFailed()
          }
        }
      )
    )
  private[sbt] def prepare(description: String, doDoc: JavaDoc): JavaDoc =
    new JavaDoc {
      def run(
          sources: List[VirtualFile],
          classpath: List[VirtualFile],
          converter: FileConverter,
          outputDirectory: Path,
          options: List[String],
          incToolOptions: IncToolOptions,
          log: Logger,
          reporter: Reporter
      ): Unit =
        if (sources.isEmpty) log.info("No sources available, skipping " + description + "...")
        else {
          log.info(
            description.capitalize + " to " + outputDirectory.toAbsolutePath.toString + "..."
          )
          doDoc.run(
            sources,
            classpath,
            converter,
            outputDirectory,
            options,
            incToolOptions,
            log,
            reporter
          )
          log.info(description.capitalize + " successful.")
        }
    }
  private[sbt] def cached(storeFactory: CacheStoreFactory, doDoc: JavaDoc): JavaDoc =
    new JavaDoc {
      def run(
          sources: List[VirtualFile],
          classpath: List[VirtualFile],
          converter: FileConverter,
          outputDirectory: Path,
          options: List[String],
          incToolOptions: IncToolOptions,
          log: Logger,
          reporter: Reporter
      ): Unit = {
        val cp = classpath.map(converter.toPath).map(_.toFile)
        val inputs = Inputs(
          sources map { x =>
            VHashFileInfo(x, x.contentHash)
          },
          filesInfoToList(lastModified(cp.toSet)),
          classpath,
          outputDirectory,
          options
        )
        val cachedDoc = inputChanged(storeFactory make "inputs") { (inChanged, in: Inputs) =>
          inputChanged(storeFactory make "output") { (outChanged, outputs: List[PlainFileInfo]) =>
            if (inChanged || outChanged) {
              if (Files.exists(outputDirectory)) {
                sbt.io.IO.delete(outputDirectory.toFile)
              }
              Files.createDirectories(outputDirectory)
              doDoc.run(
                sources,
                classpath,
                converter,
                outputDirectory,
                options,
                incToolOptions,
                log,
                reporter
              )
            } else log.debug("Doc uptodate: " + outputDirectory.toAbsolutePath.toString)
          }
        }
        cachedDoc(inputs)(filesInfoToList(exists(outputDirectory.toFile.allPaths.get.toSet)))
      }
    }

  private[this] final case class VHashFileInfo(file: VirtualFile, contentHash: Long)

  private[this] final case class Inputs(
      hfi: List[VHashFileInfo],
      mfi: List[ModifiedFileInfo],
      classpaths: List[VirtualFile],
      outputDirectory: Path,
      options: List[String]
  )
  private[sbt] def filesInfoToList[A <: FileInfo](info: FilesInfo[A]): List[A] =
    info.files.toList sortBy { x =>
      x.file.getAbsolutePath
    }
  private[sbt] val javaSourcesOnly: VirtualFile => Boolean = _.name.endsWith(".java")

  class JavadocGenerationFailed extends Exception

  trait JavaDoc {

    /**
     * @throws JavadocGenerationFailed when generating javadoc fails
     */
    def run(
        sources: List[VirtualFile],
        classpath: List[VirtualFile],
        converter: FileConverter,
        outputDirectory: Path,
        options: List[String],
        incToolOptions: IncToolOptions,
        log: Logger,
        reporter: Reporter
    ): Unit
  }
}
