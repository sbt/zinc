/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.cached

import java.io.File

import sbt.internal.inc.{ AnalysisMappers, ContextAwareMapper, Mapper, Stamp }
import xsbti.compile.MiniSetup

trait VerficationResults
object NoVerification extends VerficationResults

/**
 * Class used to verify data that will be exported using Exportable cache.
 * It is intended to create warnings if cache is not correctly mapped.
 */
abstract class CacheVerifier {

  def verifingMappers(from: AnalysisMappers): AnalysisMappers = new AnalysisMappers {
    override val outputDirMapper: Mapper[File] = analyzed("outputDirMapper", from.outputDirMapper)
    override val sourceDirMapper: Mapper[File] = analyzed("sourceDirMapper", from.sourceDirMapper)
    override val scalacOptions: Mapper[String] = analyzed("scalacOptions", from.scalacOptions)
    override val javacOptions: Mapper[String] = analyzed("javacOptions", from.javacOptions)
    override val sourceMapper: Mapper[File] = analyzed("sourceMapper", from.sourceMapper)
    override val productMapper: Mapper[File] = analyzed("productMapper", from.productMapper)
    override val binaryMapper: Mapper[File] = analyzed("binaryMapper", from.binaryMapper)
    override val binaryStampMapper: ContextAwareMapper[File, Stamp] =
      analyzed("binaryStampMapper", from.binaryStampMapper)
    override val productStampMapper: ContextAwareMapper[File, Stamp] =
      analyzed("productStampMapper", from.productStampMapper)
    override val sourceStampMapper: ContextAwareMapper[File, Stamp] =
      analyzed("sourceStampMapper", from.sourceStampMapper)
    override val classpathMapper: Mapper[File] = analyzed("classpathMapper", from.classpathMapper)

    override def mapOptionsFromCache(fromCache: MiniSetup): MiniSetup = fromCache
  }

  def results: VerficationResults

  protected def analyzeValue(category: String, serializedValue: String, deserializedValue: Any): Unit

  private def analyzed[T](category: String, original: Mapper[T]): Mapper[T] = {
    def write(v: T): String = {
      val result = original.write(v)
      analyzeValue(category, result, v)
      result
    }
    Mapper(original.read, write)
  }

  private def analyzed[C, T](category: String, original: ContextAwareMapper[C, T]): ContextAwareMapper[C, T] = {
    def write(c: C, v: T): String = {
      val result = original.write(c, v)
      analyzeValue(category, result, v)
      result
    }

    ContextAwareMapper(original.read, write)
  }
}

object NoopVerifier extends CacheVerifier {
  override protected def analyzeValue(category: String, serializedValue: String, deserializedValue: Any): Unit = ()

  override def verifingMappers(from: AnalysisMappers): AnalysisMappers = from
  override def results: VerficationResults = NoVerification
}