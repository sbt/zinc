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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
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
object ConsistentAnalysisFormatInternerSuite extends Properties:
  override def tests: List[Test] = List(
    example("strings are canonicalized across independent reads (cross-analysis)", strings),
    example(
      "text strings are canonicalized across independent reads (cross-analysis)",
      textStrings
    ),
    property("any string is canonicalized across independent reads", propStrings),
    example("api tree nodes are deduped within one read (per-read node cache)", treeNodes),
    example(
      "api tree nodes with colliding value hashes are deduped without cache hash collisions",
      collidingHashes
    ),
    example("UsedNames are canonicalized across independent reads (cross-analysis)", usedNames),
    example("interning preserves apiHash (transparent to change detection)", apiHash),
  )

  /** Serialize a single string, then read it back through a fresh deserializer. */
  private def roundTripString(s: String): String =
    val out = new ByteArrayOutputStream()
    val ser = SerializerFactory.binary.serializerFor(out)
    ser.string(s)
    ser.end()
    val deser = SerializerFactory.binary.deserializerFor(new ByteArrayInputStream(out.toByteArray))
    deser.string()

  private def roundTripTextString(s: String): String =
    val out = new ByteArrayOutputStream()
    val ser = SerializerFactory.text.serializerFor(out)
    ser.string(s)
    ser.end()
    val deser = SerializerFactory.text.deserializerFor(new ByteArrayInputStream(out.toByteArray))
    deser.string()

  private def strings: Result =
    val a = roundTripString(new String("com.example.CrossAnalysis"))
    val b = roundTripString(new String("com.example.CrossAnalysis"))
    Result.all(
      List(
        a ==== b,
        Result.assert(a `eq` b) // two separate reads share one canonical instance
      )
    )

  private def textStrings: Result =
    val a = roundTripTextString(new String("com.example.CrossAnalysis"))
    val b = roundTripTextString(new String("com.example.CrossAnalysis"))
    Result.all(List(a ==== b, Result.assert(a `eq` b)))

  def propStrings: Property =
    for
      s <- Gen.string(Gen.unicode, Range.linear(1, 32)).forAll
      text <- Gen.boolean.forAll
    yield
      val roundTrip = if text then roundTripTextString else roundTripString
      val a = roundTrip(new String(s))
      val b = roundTrip(new String(s))
      Result.all(List(a ==== s, b ==== s, Result.assert(a `eq` b)))

  private def treeNodes: Result =
    val deser =
      SerializerFactory.binary.deserializerFor(new ByteArrayInputStream(Array.empty[Byte]))
    val p1 = ConsistentAnalysisFormat.internNode(deser, ParameterRef.of("P"))
    val p2 = ConsistentAnalysisFormat.internNode(deser, ParameterRef.of("P"))
    val t1 = ConsistentAnalysisFormat.internNode(deser, Projection.of(p1, "x"))
    val t2 = ConsistentAnalysisFormat.internNode(deser, Projection.of(p2, "x"))
    Result.all(List(Result.assert(p1 `eq` p2), Result.assert(t1 `eq` t2)))

  private def collidingHashes: Result =
    val deser =
      SerializerFactory.binary.deserializerFor(new ByteArrayInputStream(Array.empty[Byte]))
    val prefix = ConsistentAnalysisFormat.internNode(deser, ParameterRef.of("P"))
    val bits = 10
    val names = Array.tabulate(1 << bits) { value =>
      val name = new StringBuilder(bits * 2)
      var bit = 0
      while bit < bits do
        name.append(if ((value >>> bit) & 1) == 0 then "Aa" else "BB")
        bit += 1
      name.result()
    }
    val nodes = names.map(Projection.of(prefix, _))
    val hashes = nodes.iterator.map(_.hashCode).toSet.size ==== 1
    val fingerprints = Result.diff(
      nodes.iterator.map(NodeCache.fingerprint).toSet.size,
      nodes.length * 0.95
    )(_ > _)
    val canonical = nodes.map(ConsistentAnalysisFormat.internNode(deser, _))
    Result.all(hashes :: fingerprints :: names.indices.toList.map { index =>
      val duplicate =
        ConsistentAnalysisFormat.internNode(deser, Projection.of(prefix, names(index)))
      Result.assert(duplicate `eq` canonical(index)).log(s"name: ${names(index)}")
    })
  end collidingHashes

  private val mappers = ReadWriteMappers.getEmptyMappers()

  private def writeConsistentBinary(contents: AnalysisContents): File =
    val out = File.createTempFile("interner-node", ".zip")
    out.deleteOnExit()
    if out.exists() then IO.delete(out)
    ConsistentFileAnalysisStore.binary(out, mappers).set(contents)
    out

  private def readAnalysis(file: File): Analysis =
    ConsistentFileAnalysisStore.binary(file, mappers).unsafeGet().getAnalysis.asInstanceOf[Analysis]

  /** First used name under the alphabetically-first class -- deterministic across reads. */
  private def firstUsedName(a: Analysis): UsedName =
    a.relations.names.toMultiMap.toSeq
      .sortBy(_._1)
      .iterator
      .flatMap(_._2.toSeq.sortBy(_.name))
      .next()

  private def usedNames: Result =
    val d = new File("../../../test-data", "library.zip")
    if !d.exists() then Result.failure.log(s"$d does not exist")
    else
      val bin = writeConsistentBinary(FileAnalysisStore.text(d).unsafeGet())

      val a = readAnalysis(bin) // two independent deserializations of the same analysis
      val b = readAnalysis(bin)
      val ua = firstUsedName(a)
      val ub = firstUsedName(b)
      Result.all(
        List(
          ua ==== ub, // same value (same analysis read twice)
          Result.assert(ua `eq` ub) // shared canonical instance across the two reads
        )
      )

  private def apiHash: Result =
    val d = new File("../../../test-data", "library.zip")
    if !d.exists() then Result.failure.log(s"$d does not exist")
    else
      val original = FileAnalysisStore.text(d).unsafeGet().getAnalysis.asInstanceOf[Analysis]
      val interned = readAnalysis(writeConsistentBinary(FileAnalysisStore.text(d).unsafeGet()))

      val originalHashes = original.apis.internal.view.mapValues(_.apiHash()).toMap
      val internedHashes = interned.apis.internal.view.mapValues(_.apiHash()).toMap
      Result.all(
        List(
          Result.assert(originalHashes.nonEmpty),
          originalHashes ==== internedHashes // interning changes nothing change-detection observes
        )
      )
end ConsistentAnalysisFormatInternerSuite
