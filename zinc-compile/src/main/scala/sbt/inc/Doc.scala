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
import sbt.internal.inc.{ CompileOutput, PlainVirtualFile, PlainVirtualFileConverter }
import sbt.internal.inc.javac.JavaCompilerArguments
import sbt.util.Tracked.inputChanged
import sbt.util.{ CacheStoreFactory, FileInfo, FilesInfo, ModifiedFileInfo, PlainFileInfo }
import sbt.util.CacheImplicits._
import sbt.io.IO
import sjsonnew._
import xsbti.Reporter

object Doc {
  private[this] implicit val IsoInputs = LList.iso(
    (in: Inputs) => ("getOutputDirectory", in.outputDirectory.toFile) :*: LNil,
    (in: File :*: LNil) => Inputs(Nil, Nil, Nil, in.head.toPath, Nil)
  )

  def cachedJavadoc(label: String, storeFactory: CacheStoreFactory, doc: JavaTools): JavaDoc =
    cached(
      storeFactory,
      prepare(
        s"$label Java API documentation",
        (sources, classpath, _, outputDirectory, options, incToolOptions, log, reporter) => {
          val success = doc.javadoc.run(
            sources.filter(javaSourcesOnly).toArray,
            JavaCompilerArguments(Nil, classpath, options).toArray,
            CompileOutput(outputDirectory),
            incToolOptions,
            reporter,
            log
          )
          if (!success)
            throw new JavadocGenerationFailed()
        }
      )
    )

  private[sbt] def prepare(description: String, doDoc: JavaDoc): JavaDoc =
    (sources, cp, converter, outputDirectory, options, incToolOptions, log, reporter) => {
      if (sources.isEmpty)
        log.info(s"No sources available, skipping $description...")
      else {
        log.info(s"${description.capitalize} to ${outputDirectory.toAbsolutePath}...")
        doDoc.run(sources, cp, converter, outputDirectory, options, incToolOptions, log, reporter)
        log.info(s"${description.capitalize} successful.")
      }
    }

  private[sbt] def cached(storeFactory: CacheStoreFactory, doDoc: JavaDoc): JavaDoc =
    (sources, classpath, converter, outDir, options, incToolOptions, log, reporter) => {
      def go() = {
        if (Files.exists(outDir))
          IO.delete(outDir.toFile)
        Files.createDirectories(outDir)
        doDoc.run(sources, classpath, converter, outDir, options, incToolOptions, log, reporter)
      }
      val srcs = sources.map(x => VHashFileInfo(x, x.contentHash))
      val cp0 = classpath.map(converter.toPath(_).toFile)
      val cp = filesInfoToList(FileInfo.lastModified(cp0.toSet))
      val inputs = Inputs(srcs, cp, classpath, outDir, options)
      val outputs = filesInfoToList(FileInfo.exists(outDir.toFile.allPaths.get().toSet))
      inputChanged(storeFactory.make("inputs")) { (inChanged, _: Inputs) =>
        inputChanged(storeFactory.make("output")) { (outChanged, _: List[PlainFileInfo]) =>
          if (inChanged || outChanged) go()
          else log.debug(s"Doc uptodate: ${outDir.toAbsolutePath}")
        }
      }.apply(inputs)(outputs)
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
    info.files.toList.sortBy(x => x.file.getAbsolutePath)

  private[sbt] val javaSourcesOnly: VirtualFile => Boolean = _.id.endsWith(".java")

  class JavadocGenerationFailed extends Exception

  trait JavaDoc {

    /** @throws JavadocGenerationFailed when generating javadoc fails */
    @deprecated("Use variant that takes VirtualFiles", "1.4.0")
    def run(
        sources: List[File],
        classpath: List[File],
        outputDirectory: File,
        options: List[String],
        incToolOptions: IncToolOptions,
        log: Logger,
        reporter: Reporter
    ): Unit = {
      run(
        sources.map(s => PlainVirtualFile(s.toPath)),
        classpath.map(s => PlainVirtualFile(s.toPath)),
        PlainVirtualFileConverter.converter,
        outputDirectory.toPath,
        options,
        incToolOptions,
        log,
        reporter
      )
    }

    /** @throws JavadocGenerationFailed when generating javadoc fails */
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
