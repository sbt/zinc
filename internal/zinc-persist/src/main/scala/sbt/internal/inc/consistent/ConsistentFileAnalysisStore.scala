// Based on zinc's FileAnalysisStore:
package sbt.internal.inc.consistent

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

import org.xerial.snappy.{ SnappyInputStream, SnappyOutputStream }

import java.io.{ File, FileInputStream, FileOutputStream }
import java.util.Optional
import sbt.io.{ IO, Using }
import xsbti.compile.{ AnalysisContents, AnalysisStore => XAnalysisStore }

import scala.util.control.Exception.allCatch
import xsbti.compile.analysis.ReadWriteMappers

import scala.concurrent.ExecutionContext

object ConsistentFileAnalysisStore {
  def text(
      file: File,
      mappers: ReadWriteMappers,
      sort: Boolean = true,
      ec: ExecutionContext = ExecutionContext.global,
      parallelism: Int = Runtime.getRuntime.availableProcessors()
  ): XAnalysisStore =
    new AStore(
      file,
      new ConsistentAnalysisFormat(mappers, sort),
      SerializerFactory.text,
      ec,
      parallelism
    )

  def binary(file: File): XAnalysisStore =
    binary(
      file,
      mappers = ReadWriteMappers.getEmptyMappers(),
      sort = true,
    )

  def binary(
      file: File,
      mappers: ReadWriteMappers
  ): XAnalysisStore =
    binary(
      file,
      mappers,
      sort = true,
    )

  def binary(
      file: File,
      mappers: ReadWriteMappers,
      sort: Boolean,
      ec: ExecutionContext = ExecutionContext.global,
      parallelism: Int = Runtime.getRuntime.availableProcessors()
  ): XAnalysisStore =
    new AStore(
      file,
      new ConsistentAnalysisFormat(mappers, sort),
      SerializerFactory.binary,
      ec,
      parallelism
    )

  private final class AStore[S <: Serializer, D <: Deserializer](
      file: File,
      format: ConsistentAnalysisFormat,
      sf: SerializerFactory[S, D],
      ec: ExecutionContext = ExecutionContext.global,
      parallelism: Int = Runtime.getRuntime.availableProcessors()
  ) extends XAnalysisStore {

    def set(analysisContents: AnalysisContents): Unit = {
      val analysis = analysisContents.getAnalysis
      val setup = analysisContents.getMiniSetup
      val tmpAnalysisFile = File.createTempFile(file.getName, ".tmp")
      if (!file.getParentFile.exists()) file.getParentFile.mkdirs()
      val fout = new FileOutputStream(tmpAnalysisFile)
      try {
        val gout = new SnappyOutputStream(fout)
        val ser = sf.serializerFor(gout)
        format.write(ser, analysis, setup)
        gout.close()
      } finally fout.close
      IO.move(tmpAnalysisFile, file)
    }

    def get(): Optional[AnalysisContents] = {
      import sbt.internal.inc.JavaInterfaceUtil.EnrichOption
      allCatch.opt(unsafeGet()).toOptional
    }

    def unsafeGet(): AnalysisContents = {
      Using.resource(new SnappyInputStream(_: FileInputStream))(
        new FileInputStream(file)
      ) { in =>
        val deser = sf.deserializerFor(in)
        val (analysis, setup) = format.read(deser)
        AnalysisContents.create(analysis, setup)
      }
    }
  }
}
