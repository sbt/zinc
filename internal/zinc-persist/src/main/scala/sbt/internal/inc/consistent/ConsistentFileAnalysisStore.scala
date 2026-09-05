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

// Based on zinc's FileAnalysisStore:
package sbt.internal.inc.consistent

import sbt.io.{ IO, Using }
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{ AnalysisContents, AnalysisStore => XAnalysisStore }

import java.io.{ File, FileInputStream, FileOutputStream }
import java.util.Optional
import scala.util.control.Exception.allCatch

object ConsistentFileAnalysisStore {
  def text(
      file: File,
      mappers: ReadWriteMappers,
      reproducible: Boolean = true,
      parallelism: Int = Runtime.getRuntime.availableProcessors()
  ): XAnalysisStore =
    new AStore(
      file,
      new ConsistentAnalysisFormat(mappers, reproducible),
      SerializerFactory.text,
      parallelism
    )

  def binary(file: File): XAnalysisStore =
    binary(
      file,
      mappers = ReadWriteMappers.getEmptyMappers(),
      reproducible = true,
    )

  def binary(
      file: File,
      mappers: ReadWriteMappers
  ): XAnalysisStore =
    binary(
      file,
      mappers,
      reproducible = true,
    )

  def binary(
      file: File,
      mappers: ReadWriteMappers,
      reproducible: Boolean,
      parallelism: Int = Runtime.getRuntime.availableProcessors()
  ): XAnalysisStore =
    new AStore(
      file,
      new ConsistentAnalysisFormat(mappers, reproducible),
      SerializerFactory.binary,
      parallelism
    )

  private final class AStore[S <: Serializer, D <: Deserializer](
      file: File,
      format: ConsistentAnalysisFormat,
      sf: SerializerFactory[S, D],
      parallelism: Int = Runtime.getRuntime.availableProcessors()
  ) extends XAnalysisStore {

    private def moveWithRetry(source: File, target: File): Unit = {
      val retries = 5
      val delayMs = 100L
      var lastException: Exception = null
      var i = 0
      while (i < retries) {
        try {
          IO.move(source, target)
          return // Success, exit
        } catch {
          case e: java.io.FileNotFoundException
              if e.getMessage != null && e.getMessage.contains("Access is denied") =>
            lastException = e
            if (i < retries - 1) {
              Thread.sleep(delayMs)
            }
          case e: Exception =>
            throw e // Re-throw other exceptions immediately
        }
        i += 1
      }
      if (lastException != null) throw lastException
    }

    def set(analysisContents: AnalysisContents): Unit = {
      val analysis = analysisContents.getAnalysis
      val setup = analysisContents.getMiniSetup
      val tmpAnalysisFile = File.createTempFile(file.getName, ".tmp")
      if (!file.getParentFile.exists()) file.getParentFile.mkdirs()
      val fout = new FileOutputStream(tmpAnalysisFile)
      try {
        val gout = new ParallelGzipOutputStream(fout, parallelism)
        val ser = sf.serializerFor(gout)
        format.write(ser, analysis, setup)
        gout.close()
      } finally fout.close
      moveWithRetry(tmpAnalysisFile, file) // Changed from IO.move(tmpAnalysisFile, file)
    }

    def get(): Optional[AnalysisContents] = {
      import scala.jdk.OptionConverters._
      allCatch.opt(unsafeGet()).toJava
    }

    def unsafeGet(): AnalysisContents =
      Using.gzipInputStream(new FileInputStream(file)) { in =>
        val deser = sf.deserializerFor(in)
        val (analysis, setup) = format.read(deser)
        AnalysisContents.create(analysis, setup)
      }
  }
}
