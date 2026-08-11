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

import scala.collection.mutable.ArrayBuffer
import java.util.Collections
import sbt.internal.util.Relation
import sbt.util.{ Level, Logger }
import xsbti.{ UseScope, VirtualFileRef }
import xsbti.api.{ DependencyContext, ExternalDependency, InternalDependency }
import xsbti.compile.{ Changes, IncOptions }

class InvalidationLogSpec extends UnitSpec {
  behavior of "InvalidationLog"

  it should "render non-empty groups as sorted indented lists" in {
    val rendered = InvalidationLog.section(
      "Cycle 2",
      Seq(
        "invalidated classes" -> Seq("example.Z", "example.A"),
        "macro expansion" -> Nil,
        "invalidated sources" -> Seq("/a/very/long/path/to/Example.scala"),
      )
    )

    rendered shouldBe expectedLines(
      """Cycle 2
        |  invalidated classes:
        |    example.A
        |    example.Z
        |  invalidated sources:
        |    /a/very/long/path/to/Example.scala"""
    )
  }

  it should "render an explicitly empty result without an empty heading" in {
    InvalidationLog.section("Cycle outcome", Nil, Some("no further invalidations")) shouldBe
      expectedLines(
        """Cycle outcome
          |  no further invalidations"""
      )
  }

  it should "omit a section that has no groups or empty-result text" in {
    InvalidationLog.section("Current sources", Nil) shouldBe ""
  }

  it should "indent every line of multiline values" in {
    InvalidationLog.section("Cause", Seq("reason" -> Seq("first line\nsecond line"))) shouldBe
      expectedLines(
        """Cause
          |  reason:
          |    first line
          |    second line"""
      )
  }

  it should "hide the default used-name scope but preserve non-default scopes" in {
    InvalidationLog.formatUsedName(UsedName("member", Seq(UseScope.Default))) shouldBe "member"
    InvalidationLog.formatUsedName(
      UsedName("member", Seq(UseScope.Implicit, UseScope.PatMatTarget))
    ) shouldBe "member [Implicit, PatMatTarget]"
  }

  it should "describe API changes without raw implementation wrappers" in {
    val names = ModifiedNames(
      Set(
        UsedName("zeta", Seq(UseScope.Default)),
        UsedName("alpha", Seq(UseScope.Implicit)),
      )
    )

    InvalidationLog.formatApiChange(NamesChange("example.A", names)) shouldBe
      "example.A: modified names alpha [Implicit], zeta"
    InvalidationLog.formatApiChange(APIChangeDueToMacroDefinition("example.Macro")) shouldBe
      "example.Macro: macro definition changed"
    InvalidationLog.formatApiChange(
      APIChangeDueToAnnotationDefinition("example.Annotation")
    ) shouldBe
      "example.Annotation: annotation definition changed"
    InvalidationLog.formatApiChange(TraitPrivateMembersModified("example.Trait")) shouldBe
      "example.Trait: private members changed"
  }

  it should "render relation details deterministically without collection wrappers" in {
    val relations = Relations.empty.copy(
      names = UsedNames.fromMultiMap(
        Map(
          "B" -> Set(UsedName("zeta", Seq(UseScope.Implicit))),
          "A" -> Set(UsedName("alpha", Seq(UseScope.Default))),
        )
      ),
      productClassName = Relation.empty + ("B", "B$") + ("A", "A"),
    )

    relations.toString should include(
      expectedLines(
        """  used names:
          |    A -> alpha
          |    B -> zeta [Implicit]
          |  product class names:
          |    A -> A
          |    B -> B$"""
      )
    )
    relations.toString should not include "UsedName("
    relations.toString should not include "Relation ["
  }

  it should "prefix every emitted line and gate detailed output" in {
    val concise = new RecordingLogger
    val conciseLog = new InvalidationLog(concise, relationsDebug = false)
    var evaluatedDetail = false
    conciseLog.debug("Cycle 1\n  invalidated classes:\n    A")
    conciseLog.detail {
      evaluatedDetail = true
      "Relations:\n  products: none"
    }

    concise.messages shouldBe Seq("[inv] Cycle 1\n[inv]   invalidated classes:\n[inv]     A")
    evaluatedDetail shouldBe false

    val detailed = new RecordingLogger
    val detailedLog = new InvalidationLog(detailed, relationsDebug = true)
    detailedLog.detail("Relations:\n  products: none")

    detailed.messages shouldBe Seq("[inv] Relations:\n[inv]   products: none")
    detailed.messages.mkString should not include "\u001b["
  }

  it should "keep concise debug rendering lazy when debug output is discarded" in {
    val invalidationLog = new InvalidationLog(new DiscardingLogger, relationsDebug = false)
    var evaluated = false

    invalidationLog.debug {
      evaluated = true
      InvalidationLog.section("Cycle", Seq("classes" -> Seq("A")))
    }

    evaluated shouldBe false
  }

  it should "describe each cycle outcome" in {
    InvalidationLog.cycleOutcome(2, Set("B", "A"), continue = true, isFullCompilation = false)
      .shouldBe(
        expectedLines(
          """Cycle 2 outcome
            |  next-cycle invalidations:
            |    A
            |    B"""
        )
      )
    InvalidationLog.cycleOutcome(2, Set("A"), continue = false, isFullCompilation = false)
      .shouldBe(
        expectedLines(
          """Cycle 2 outcome
            |  invalidations not scheduled for another cycle:
            |    A"""
        )
      )
    InvalidationLog.cycleOutcome(2, Set.empty, continue = false, isFullCompilation = false)
      .shouldBe(
        expectedLines(
          """Cycle 2 outcome
            |  no further invalidations"""
        )
      )
    InvalidationLog.cycleOutcome(1, Set.empty, continue = false, isFullCompilation = true)
      .shouldBe(
        expectedLines(
          """Cycle 1 outcome
            |  full compilation complete"""
        )
      )
  }

  it should "log representative internal invalidation reasons through the formatter" in {
    val logger = new RecordingLogger
    val incremental = new TestIncremental(logger)
    val relations = Relations.empty
      .addInternalSrcDeps(
        VirtualFileRef.of("Example.scala"),
        Seq(
          InternalDependency.of("B", "A", DependencyContext.DependencyByInheritance),
          InternalDependency.of("C", "B", DependencyContext.DependencyByInheritance),
          InternalDependency.of("D", "B", DependencyContext.LocalDependencyByInheritance),
          InternalDependency.of("E", "B", DependencyContext.DependencyByMemberRef),
          InternalDependency.of("F", "A", DependencyContext.DependencyByMacroExpansion),
        )
      )
      .addUsedNames(
        UsedNames.fromMultiMap(Map("E" -> Set(UsedName("changed", Seq(UseScope.Default)))))
      )

    incremental.invalidateInternal(
      relations,
      NamesChange("A", ModifiedNames(Set(UsedName("changed", Seq(UseScope.Default)))))
    ) shouldBe Set("A", "B", "C", "D", "E", "F")

    logger.messages.mkString("\n") should include(
      expectedLines(
        """[inv] API change: A: modified names changed
          |[inv]   transitive inheritance:
          |[inv]     A
          |[inv]     B
          |[inv]     C
          |[inv]   local inheritance:
          |[inv]     D
          |[inv]   member reference:
          |[inv]     E
          |[inv]   macro expansion:
          |[inv]     F"""
      )
    )
    logger.messages.mkString should not include "Set("
    logger.messages.mkString should not include "UsedName("
  }

  it should "log representative external and macro invalidations through the formatter" in {
    val logger = new RecordingLogger
    val incremental = new TestIncremental(logger)
    val source = VirtualFileRef.of("Example.scala")
    val relations = Relations.empty
      .addInternalSrcDeps(
        source,
        Seq(
          InternalDependency.of("C", "B", DependencyContext.DependencyByInheritance),
          InternalDependency.of("E", "B", DependencyContext.DependencyByMemberRef),
        )
      )
      .addExternalDeps(
        source,
        Seq(
          ExternalDependency.of("B", "External", null, DependencyContext.DependencyByInheritance),
          ExternalDependency.of(
            "D",
            "External",
            null,
            DependencyContext.LocalDependencyByInheritance
          ),
          ExternalDependency.of("G", "External", null, DependencyContext.DependencyByMemberRef),
          ExternalDependency.of(
            "H",
            "External",
            null,
            DependencyContext.DependencyByMacroExpansion
          ),
        )
      )
      .addUsedNames(
        UsedNames.fromMultiMap(
          Map(
            "E" -> Set(UsedName("changed", Seq(UseScope.Default))),
            "G" -> Set(UsedName("changed", Seq(UseScope.Default))),
          )
        )
      )

    incremental.invalidateExternal(
      relations,
      NamesChange("External", ModifiedNames(Set(UsedName("changed", Seq(UseScope.Default)))))
    ) shouldBe Set("B", "C", "D", "E", "G", "H")
    incremental.invalidateExternal(relations, APIChangeDueToMacroDefinition("External")) shouldBe
      Set("B", "C", "D", "E", "G", "H")

    val messages = logger.messages.mkString("\n")
    messages should include("[inv] External API change: External: modified names changed")
    messages should include("[inv] External API change: External: macro definition changed")
    messages should include("[inv]   transitive inheritance:")
    messages should include("[inv]   local inheritance:")
    messages should include("[inv]   member reference:")
    messages should include("[inv]   macro expansion:")
  }

  it should "log class-file collisions and brute-force traversal through the formatter" in {
    val logger = new RecordingLogger
    val incremental = new TestIncremental(logger, relationsDebug = true)
    val sourceA = VirtualFileRef.of("/src/A.scala")
    val sourceB = VirtualFileRef.of("/src/B.scala")
    val product = VirtualFileRef.of("/classes/Shared.class")
    val collisionRelations = Relations.empty
      .addProducts(sourceA, Seq(product))
      .addProducts(sourceB, Seq(product))
      .addClasses(sourceA, Seq("A" -> "Shared"))
      .addClasses(sourceB, Seq("B" -> "Shared"))

    incremental.invalidateAfterInternalCompilation(
      Analysis.empty.copy(relations = collisionRelations),
      new APIChanges(Nil),
      Set.empty,
      invalidateTransitively = false,
      _ => true
    ) shouldBe Set("A", "B")

    val inheritanceRelations = Relations.empty.addInternalSrcDeps(
      sourceA,
      Seq(InternalDependency.of("B", "A", DependencyContext.DependencyByInheritance))
    )
    incremental.invalidateAfterInternalCompilation(
      Analysis.empty.copy(relations = inheritanceRelations),
      new APIChanges(
        Seq(NamesChange("A", ModifiedNames(Set(UsedName("changed", Seq(UseScope.Default))))))
      ),
      Set("A"),
      invalidateTransitively = true,
      _ => true
    ) shouldBe Set("A", "B")

    val messages = logger.messages.mkString("\n")
    messages should include("[inv] Generated class-file collision")
    messages should include("[inv] Brute-force transitive invalidation")
    logger.messages.foreach(_ should startWith("[inv] "))
  }

  it should "log full-compilation and no-change initial states" in {
    val logger = new RecordingLogger
    val incremental = new TestIncremental(logger)
    val noChanges = InitialChanges(emptyChanges, Set.empty, Set.empty, new APIChanges(Nil))

    incremental.invalidateInitial(Relations.empty, noChanges) shouldBe (Set.empty, Set.empty)

    val source = VirtualFileRef.of("/src/A.scala")
    val existingRelations = Relations.empty.addProducts(
      source,
      Seq(VirtualFileRef.of("/classes/A.class"))
    )
    incremental.invalidateInitial(existingRelations, noChanges) shouldBe (Set.empty, Set.empty)

    logger.messages should contain theSameElementsInOrderAs Seq(
      "[inv] Initial changes\n[inv]   full compilation",
      "[inv] Initial changes\n[inv]   no changes",
    )
  }

  /** Triple-quoted literals inherit the checkout's line endings (CRLF on Windows CI). */
  private def expectedLines(s: String): String = s.stripMargin.replace("\r\n", "\n")

  private final class TestIncremental(logger: Logger, relationsDebug: Boolean = false)
      extends IncrementalNameHashingCommon(
        logger,
        IncOptions.of().withRelationsDebug(relationsDebug),
        RunProfiler.empty
      ) {
    def invalidateInternal(relations: Relations, change: APIChange): Set[String] =
      invalidateClassesInternally(relations, change, _ => true)

    def invalidateExternal(relations: Relations, change: APIChange): Set[String] =
      invalidateClassesExternally(relations, change, _ => true)
  }

  private val emptyChanges = new Changes[VirtualFileRef] {
    override def getAdded = Collections.emptySet[VirtualFileRef]
    override def getRemoved = Collections.emptySet[VirtualFileRef]
    override def getChanged = Collections.emptySet[VirtualFileRef]
    override def getUnmodified = Collections.emptySet[VirtualFileRef]
    override def isEmpty: java.lang.Boolean = java.lang.Boolean.TRUE
  }

  private final class RecordingLogger extends Logger {
    val messages: ArrayBuffer[String] = ArrayBuffer.empty

    override def trace(t: => Throwable): Unit = ()
    override def success(message: => String): Unit = ()
    override def log(level: Level.Value, message: => String): Unit =
      if (level == Level.Debug || level == Level.Info) messages += message
  }

  private final class DiscardingLogger extends Logger {
    override def trace(t: => Throwable): Unit = ()
    override def success(message: => String): Unit = ()
    override def log(level: Level.Value, message: => String): Unit = ()
  }
}
