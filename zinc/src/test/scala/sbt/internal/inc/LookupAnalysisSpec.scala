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

package sbt.internal.inc

class LookupAnalysisSpec extends UnitSpec {
  import LookupAnalysisFixture.*

  "Analysis lookup" should "handle empty classpaths and absent or empty analyses" in {
    for (entries <- Vector(Vector.empty, Vector(None), Vector(Some(Analysis.empty)))) {
      new Fixture(entries).newLookup().lookupAnalysis("missing.C") shouldBe None
    }
  }

  it should "match the ordered oracle for first, middle, last and missing binary names" in {
    val entries = Vector.tabulate(3)(i => Some(analysis(i, List(s"source$i.C" -> s"binary$i.C"))))
    val fixture = new Fixture(entries)
    val lookup = fixture.newLookup()
    for (name <- List("binary0.C", "binary1.C", "binary2.C", "missing.C", "source0.C")) {
      val expected = fixture.analyses.find(_.relations.productClassName._2s.contains(name))
      val actual = lookup.lookupAnalysis(name)
      actual.isDefined shouldBe expected.isDefined
      actual.foreach(a => (a eq expected.get) shouldBe true)
    }
  }

  it should "select the first definition and its API when classpath order changes" in {
    val first = analysis(1, List("source.First" -> "duplicate.C"))
    val second = analysis(2, List("source.Second" -> "duplicate.C"))
    for (ordered <- Vector(Vector(first, second), Vector(second, first))) {
      val lookup = new Fixture(ordered.map(Some(_))).newLookup()
      (lookup.lookupAnalysis("duplicate.C").get eq ordered.head) shouldBe true
      lookup.lookupAnalyzedClass("duplicate.C", None).get.apiHash shouldBe
        ordered.head.apis.internal.values.head.apiHash
    }
  }

  it should "use binary names including nested classes and modules" in {
    val a = analysis(1, List("p.Outer.Inner" -> "p.Outer$Inner", "p.Module" -> "p.Module$"))
    val lookup = new Fixture(Vector(Some(a))).newLookup()
    for (name <- List("p.Outer$Inner", "p.Module$")) {
      (lookup.lookupAnalysis(name).get eq a) shouldBe true
      lookup.lookupAnalyzedClass(name, None).get.apiHash shouldBe 1
    }
    lookup.lookupAnalysis("p.Outer.Inner") shouldBe None
  }

  it should "retain the first definition even when its API is absent" in {
    val first = analysis(1, List("source.First" -> "duplicate.C"), withApi = false)
    val second = analysis(2, List("source.Second" -> "duplicate.C"))
    val lookup = new Fixture(Vector(Some(first), Some(second))).newLookup()
    (lookup.lookupAnalysis("duplicate.C").get eq first) shouldBe true
    lookup.lookupAnalyzedClass("duplicate.C", None) shouldBe None
  }

  it should "load each classpath position lazily once including repeated entries" in {
    val counters = new Counters
    val a = analysis(1, List("p.C" -> "p.C"))
    val fixture = new Fixture(Vector(Some(a), None), Vector(0, 1, 0), Some(counters))
    val lookup = fixture.newLookup()
    counters.loads.get shouldBe 0L
    lookup.analyses.size shouldBe 2
    counters.loads.get shouldBe 3L
    counters.visits.get shouldBe 0L
    (lookup.analyses.head eq lookup.analyses.last) shouldBe true
    lookup.lookupAnalysis("p.C").isDefined shouldBe true
    lookup.lookupAnalysis("missing.C") shouldBe None
    counters.loads.get shouldBe 3L
    counters.defines.get shouldBe 0L
  }
}
