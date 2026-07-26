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

package sbt.internal.inc.consistent

import org.scalatest.funsuite.AnyFunSuite
import sbt.internal.inc.{ Analysis, FileAnalysisStore, UsedName }
import sbt.io.IO
import xsbti.api.{ ParameterRef, Projection }
import xsbti.compile.AnalysisContents
import xsbti.compile.analysis.ReadWriteMappers
import java.io.*

/**
 * Verifies that the consistent binary format dedups inline while deserializing:
 * strings and `UsedName`s are shared across independently-read analyses via the
 * process-wide interner, and value-equality api tree nodes are shared within one
 * read via the per-read node cache.
 */
class ConsistentAnalysisFormatInternerSuite extends AnyFunSuite {

  /** Serialize a single string, then read it back through a fresh deserializer. */
  private def roundTripString(s: String): String = {
    val out = new ByteArrayOutputStream()
    val ser = SerializerFactory.binary.serializerFor(out)
    ser.string(s)
    ser.end()
    val deser = SerializerFactory.binary.deserializerFor(new ByteArrayInputStream(out.toByteArray))
    deser.string()
  }

  test("strings are canonicalized across independent reads (cross-analysis)") {
    val a = roundTripString(new String("com.example.CrossAnalysis"))
    val b = roundTripString(new String("com.example.CrossAnalysis"))
    assert(a == b)
    assert(a `eq` b) // two separate reads share one canonical instance
  }

  test("api tree nodes are deduped within one read (per-read node cache)") {
    val deser =
      SerializerFactory.binary.deserializerFor(new ByteArrayInputStream(Array.empty[Byte]))
    val t1 = ConsistentAnalysisFormat.internNode(deser, Projection.of(ParameterRef.of("P"), "x"))
    val t2 = ConsistentAnalysisFormat.internNode(deser, Projection.of(ParameterRef.of("P"), "x"))
    assert(t1 `eq` t2)
  }

  private val mappers = ReadWriteMappers.getEmptyMappers()

  private def writeConsistentBinary(contents: AnalysisContents): File = {
    val out = File.createTempFile("interner-node", ".zip")
    out.deleteOnExit()
    if (out.exists()) IO.delete(out)
    ConsistentFileAnalysisStore.binary(out, mappers).set(contents)
    out
  }

  private def readAnalysis(file: File): Analysis =
    ConsistentFileAnalysisStore.binary(file, mappers).unsafeGet().getAnalysis.asInstanceOf[Analysis]

  /** First used name under the alphabetically-first class -- deterministic across reads. */
  private def firstUsedName(a: Analysis): UsedName =
    a.relations.names.toMultiMap.toSeq
      .sortBy(_._1)
      .iterator
      .flatMap(_._2.toSeq.sortBy(_.name))
      .next()

  test("UsedNames are canonicalized across independent reads (cross-analysis)") {
    val d = new File("../../../test-data", "library.zip")
    assert(d.exists())
    val bin = writeConsistentBinary(FileAnalysisStore.text(d).unsafeGet())

    val a = readAnalysis(bin) // two independent deserializations of the same analysis
    val b = readAnalysis(bin)
    val ua = firstUsedName(a)
    val ub = firstUsedName(b)
    assert(ua == ub) // same value (same analysis read twice)
    assert(ua `eq` ub) // shared canonical instance across the two reads
  }

  test("interning preserves apiHash (transparent to change detection)") {
    val d = new File("../../../test-data", "library.zip")
    assert(d.exists())
    val original = FileAnalysisStore.text(d).unsafeGet().getAnalysis.asInstanceOf[Analysis]
    val interned = readAnalysis(writeConsistentBinary(FileAnalysisStore.text(d).unsafeGet()))

    val originalHashes = original.apis.internal.view.mapValues(_.apiHash()).toMap
    val internedHashes = interned.apis.internal.view.mapValues(_.apiHash()).toMap
    assert(originalHashes.nonEmpty)
    assert(originalHashes == internedHashes) // interning changes nothing change-detection observes
  }
}
