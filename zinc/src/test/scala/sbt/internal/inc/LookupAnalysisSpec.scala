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

import java.util.Optional
import java.util.concurrent.{ Callable, CountDownLatch, Executors, TimeUnit }
import xsbti.VirtualFileRef
import xsbti.api.AnalyzedClass
import xsbti.compile.{ DefaultExternalHooks, IncOptions }

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

  it should "answer warm queries without revisiting analyses or loading classpath entries" in {
    for (size <- Vector(2, 2000)) {
      val counters = new Counters
      val entries = Vector.tabulate(size)(i => Some(analysis(i, List(s"p$i.C" -> s"p$i.C"))))
      val lookup = new Fixture(entries, counters = Some(counters)).newLookup()
      lookup.lookupAnalysis("initial.miss") shouldBe None
      counters.visits.set(0L)
      for (i <- 0 until 100) {
        lookup.lookupAnalysis(s"p${i % size}.C").isDefined shouldBe true
        lookup.lookupAnalysis("repeated.miss") shouldBe None
        lookup.lookupAnalysis(s"unseen.Missing$i") shouldBe None
      }
      counters.loads.get shouldBe size.toLong
      counters.visits.get shouldBe 0L
    }
  }

  it should "respect external answers and fall back only for missing provenance" in {
    val a = analysis(1, List("p.C" -> "p.C"))
    val externalApi = APIs.emptyAnalyzedClass.withApiHash(99).withProvenance("external")
    for (answer <- Vector(Some(externalApi), None, Some(externalApi.withProvenance("")))) {
      val counters = new Counters
      val hook = new NoopExternalLookup {
        override def lookupAnalyzedClass(
            name: String,
            file: Option[VirtualFileRef]
        ): Option[AnalyzedClass] = answer
      }
      val hooks = new DefaultExternalHooks(Optional.of(hook), Optional.empty())
      val fixture = new Fixture(
        Vector(Some(a)),
        counters = Some(counters),
        options = IncOptions.of().withExternalHooks(hooks)
      )
      val result = fixture.newLookup().lookupAnalyzedClass("p.C", None)
      if (answer.exists(_.provenance.isEmpty)) {
        result.get.apiHash shouldBe 1
        counters.loads.get shouldBe 1L
      } else {
        result shouldBe answer
        counters.loads.get shouldBe 0L
        counters.visits.get shouldBe 0L
      }
    }
  }

  it should "honor analyses supplied by a subclass without loading the provider" in {
    val first = analysis(1, List("first.Source" -> "p.C"))
    val second = analysis(2, List("second.Source" -> "p.C"))
    val counters = new Counters
    val fixture = new Fixture(Vector(Some(Analysis.empty)), counters = Some(counters))
    val lookup = new LookupImpl(fixture.config, None) {
      override lazy val analyses: Vector[Analysis] = Vector(second, first)
    }
    (lookup.lookupAnalysis("p.C").get eq second) shouldBe true
    counters.loads.get shouldBe 0L
  }

  it should "observe added, removed and reordered definitions in fresh instances" in {
    val old = analysis(1, List("p.Old" -> "p.Old", "p.C" -> "p.C"))
    val other = analysis(2, List("p.C" -> "p.C"))
    val before = new Fixture(Vector(Some(old), Some(other))).newLookup()
    before.lookupAnalysis("p.New") shouldBe None
    (before.lookupAnalysis("p.C").get eq old) shouldBe true
    before.lookupAnalysis("p.Old").isDefined shouldBe true
    val added = analysis(3, List("p.New" -> "p.New", "p.C" -> "p.C"))
    val after = new Fixture(Vector(Some(other), Some(added))).newLookup()
    (after.lookupAnalysis("p.C").get eq other) shouldBe true
    (after.lookupAnalysis("p.New").get eq added) shouldBe true
    after.lookupAnalysis("p.Old") shouldBe None
    before.lookupAnalysis("p.New") shouldBe None
  }

  it should "publish one complete index to concurrent first callers" in {
    val counters = new Counters
    val size = 200
    val entries = Vector.tabulate(size)(i => Some(analysis(i, List(s"p$i.C" -> s"p$i.C"))))
    val fixture = new Fixture(entries, counters = Some(counters))
    val lookup = fixture.newLookup()
    val ready = new CountDownLatch(8)
    val start = new CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(8)
    try {
      val futures = Vector.tabulate(8) { reader =>
        pool.submit(new Callable[Unit] {
          def call(): Unit = {
            ready.countDown()
            assert(start.await(10, TimeUnit.SECONDS), "concurrent start timed out")
            for (offset <- 0 until size) {
              val i = (offset + reader) % size
              (lookup.lookupAnalysis(s"p$i.C").get eq fixture.analyses(i)) shouldBe true
              lookup.lookupAnalysis(s"reader$reader.Missing$offset") shouldBe None
            }
          }
        })
      }
      assert(ready.await(10, TimeUnit.SECONDS), "readers did not become ready")
      start.countDown()
      futures.foreach(_.get(30, TimeUnit.SECONDS))
      counters.loads.get shouldBe size.toLong
      counters.visits.get shouldBe size.toLong
    } finally {
      start.countDown()
      pool.shutdownNow()
      assert(pool.awaitTermination(10, TimeUnit.SECONDS), "reader pool did not stop")
    }
  }
}
