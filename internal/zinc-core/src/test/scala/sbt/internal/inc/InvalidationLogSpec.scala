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

import hedgehog.{ sbt as _, * }
import hedgehog.predef.traverse
import hedgehog.runner.*
import scala.collection.mutable.ArrayBuffer
import java.util.Collections
import sbt.internal.util.Relation
import sbt.util.{ Level, Logger }
import xsbti.{ UseScope, VirtualFileRef }
import xsbti.api.{ DependencyContext, ExternalDependency, InternalDependency }
import xsbti.compile.{ Changes, IncOptions }
import HedgehogSupport.{ contains, notContains, shuffle }

object InvalidationLogSpec extends Properties:
  override def tests: List[Test] = List(
    example("render non-empty groups as sorted indented lists", sortedGroups),
    example("render an explicitly empty result without an empty heading", explicitlyEmpty),
    example("omit a section that has no groups or empty-result text", omitSection),
    example("indent every line of multiline values", multilineValues),
    property("render the same section for any order of group values", propValueOrder),
    property("render one line per heading and per value line", propSectionLines),
    example("hide the default used-name scope but preserve non-default scopes", usedNameScopes),
    property("format any used name with its sorted non-default scopes", propFormatUsedName),
    example("describe API changes without raw implementation wrappers", apiChanges),
    example("render relation details deterministically without collection wrappers", relations),
    example("prefix every emitted line and gate detailed output", prefixAndGate),
    property("prefix every line of any debug message", propPrefixLines),
    example("keep concise debug rendering lazy when debug output is discarded", lazyDebug),
    example("describe each cycle outcome", cycleOutcomes),
    example("log representative internal invalidation reasons through the formatter", internal),
    example("log representative external and macro invalidations through the formatter", external),
    example(
      "log class-file collisions and brute-force traversal through the formatter",
      collisions
    ),
    example("log full-compilation and no-change initial states", initialStates),
  )

  private def sortedGroups: Result =
    val rendered = InvalidationLog.section(
      "Cycle 2",
      Seq(
        "invalidated classes" -> Seq("example.Z", "example.A"),
        "macro expansion" -> Nil,
        "invalidated sources" -> Seq("/a/very/long/path/to/Example.scala"),
      )
    )
    rendered ==== expectedLines(
      """Cycle 2
        |  invalidated classes:
        |    example.A
        |    example.Z
        |  invalidated sources:
        |    /a/very/long/path/to/Example.scala"""
    )

  private def explicitlyEmpty: Result =
    InvalidationLog.section("Cycle outcome", Nil, Some("no further invalidations")) ====
      expectedLines(
        """Cycle outcome
          |  no further invalidations"""
      )

  private def omitSection: Result =
    InvalidationLog.section("Current sources", Nil) ==== ""

  private def multilineValues: Result =
    InvalidationLog.section("Cause", Seq("reason" -> Seq("first line\nsecond line"))) ====
      expectedLines(
        """Cause
          |  reason:
          |    first line
          |    second line"""
      )

  private val genWord: Gen[String] = Gen.string(Gen.alphaNum, Range.linear(1, 12))

  private val genGroups: Gen[List[(String, List[String])]] =
    Gen.list(
      for
        label <- genWord
        values <- Gen.list(genWord, Range.linear(0, 5))
      yield label -> values,
      Range.linear(0, 5)
    )

  def propValueOrder: Property =
    for
      groups <- genGroups.forAll
      shuffled <- traverse(groups)((label, values) => shuffle(values).map(label -> _)).forAll
    yield InvalidationLog.section("Title", groups) ==== InvalidationLog.section("Title", shuffled)

  def propSectionLines: Property =
    for groups <- genGroups.forAll
    yield
      val rendered = InvalidationLog.section("Title", groups)
      val nonEmpty = groups.filter(_._2.nonEmpty)
      if nonEmpty.isEmpty then rendered ==== ""
      else
        val lines = rendered.linesIterator.toList
        Result.all(
          List(
            lines.size ==== 1 + nonEmpty.size + nonEmpty.map(_._2.size).sum,
            lines.head ==== "Title",
            Result.assert(lines.tail.forall(_.startsWith("  "))).log(rendered),
          )
        )

  private def usedNameScopes: Result =
    Result.all(
      List(
        InvalidationLog.formatUsedName(UsedName("member", Seq(UseScope.Default))) ==== "member",
        InvalidationLog.formatUsedName(
          UsedName("member", Seq(UseScope.Implicit, UseScope.PatMatTarget))
        ) ==== "member [Implicit, PatMatTarget]",
      )
    )

  def propFormatUsedName: Property =
    for
      name <- genWord.forAll
      scopes <- Gen.list(
        Gen.element1(UseScope.Default, UseScope.Implicit, UseScope.PatMatTarget),
        Range.linear(0, 3)
      ).forAll
    yield
      val nonDefault = scopes.filterNot(_ == UseScope.Default).map(_.toString).distinct.sorted
      val expected =
        if nonDefault.isEmpty then name else s"$name [${nonDefault.mkString(", ")}]"
      InvalidationLog.formatUsedName(UsedName(name, scopes)) ==== expected

  private def apiChanges: Result =
    val names = ModifiedNames(
      Set(
        UsedName("zeta", Seq(UseScope.Default)),
        UsedName("alpha", Seq(UseScope.Implicit)),
      )
    )
    Result.all(
      List(
        InvalidationLog.formatApiChange(NamesChange("example.A", names)) ====
          "example.A: modified names alpha [Implicit], zeta",
        InvalidationLog.formatApiChange(APIChangeDueToMacroDefinition("example.Macro")) ====
          "example.Macro: macro definition changed",
        InvalidationLog.formatApiChange(
          APIChangeDueToAnnotationDefinition("example.Annotation")
        ) ==== "example.Annotation: annotation definition changed",
        InvalidationLog.formatApiChange(TraitPrivateMembersModified("example.Trait")) ====
          "example.Trait: private members changed",
      )
    )

  private def relations: Result =
    val relations = Relations.empty.copy(
      names = UsedNames.fromMultiMap(
        Map(
          "B" -> Set(UsedName("zeta", Seq(UseScope.Implicit))),
          "A" -> Set(UsedName("alpha", Seq(UseScope.Default))),
        )
      ),
      productClassName = Relation.empty + ("B", "B$") + ("A", "A"),
    )
    val rendered = relations.toString
    Result.all(
      List(
        contains(
          rendered,
          expectedLines(
            """  used names:
              |    A -> alpha
              |    B -> zeta [Implicit]
              |  product class names:
              |    A -> A
              |    B -> B$"""
          )
        ),
        notContains(rendered, "UsedName("),
        notContains(rendered, "Relation ["),
      )
    )
  end relations

  private def prefixAndGate: Result =
    val concise = new RecordingLogger
    val conciseLog = new InvalidationLog(concise, relationsDebug = false)
    var evaluatedDetail = false
    conciseLog.debug("Cycle 1\n  invalidated classes:\n    A")
    conciseLog.detail {
      evaluatedDetail = true
      "Relations:\n  products: none"
    }
    val detailed = new RecordingLogger
    val detailedLog = new InvalidationLog(detailed, relationsDebug = true)
    detailedLog.detail("Relations:\n  products: none")
    Result.all(
      List(
        concise.messages.toList ==== List(
          "[inv] Cycle 1\n[inv]   invalidated classes:\n[inv]     A"
        ),
        evaluatedDetail ==== false,
        detailed.messages.toList ==== List("[inv] Relations:\n[inv]   products: none"),
        notContains(detailed.messages.mkString, "\u001b["),
      )
    )
  end prefixAndGate

  def propPrefixLines: Property =
    for lines <- Gen.list(genWord, Range.linear(1, 8)).forAll
    yield
      val logger = new RecordingLogger
      new InvalidationLog(logger, relationsDebug = false).debug(lines.mkString("\n"))
      logger.messages.toList ==== List(lines.map("[inv] " + _).mkString("\n"))

  private def lazyDebug: Result =
    val invalidationLog = new InvalidationLog(new DiscardingLogger, relationsDebug = false)
    var evaluated = false
    invalidationLog.debug {
      evaluated = true
      InvalidationLog.section("Cycle", Seq("classes" -> Seq("A")))
    }
    evaluated ==== false

  private def cycleOutcomes: Result =
    Result.all(
      List(
        InvalidationLog.cycleOutcome(
          2,
          Set("B", "A"),
          continue = true,
          isFullCompilation = false
        ) ==== expectedLines(
          """Cycle 2 outcome
            |  next-cycle invalidations:
            |    A
            |    B"""
        ),
        InvalidationLog.cycleOutcome(2, Set("A"), continue = false, isFullCompilation = false) ====
          expectedLines(
            """Cycle 2 outcome
              |  invalidations not scheduled for another cycle:
              |    A"""
          ),
        InvalidationLog.cycleOutcome(2, Set.empty, continue = false, isFullCompilation = false) ====
          expectedLines(
            """Cycle 2 outcome
              |  no further invalidations"""
          ),
        InvalidationLog.cycleOutcome(1, Set.empty, continue = false, isFullCompilation = true) ====
          expectedLines(
            """Cycle 1 outcome
              |  full compilation complete"""
          ),
      )
    )

  private def internal: Result =
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
    val invalidated = incremental.invalidateInternal(
      relations,
      NamesChange("A", ModifiedNames(Set(UsedName("changed", Seq(UseScope.Default)))))
    )
    Result.all(
      List(
        invalidated ==== Set("A", "B", "C", "D", "E", "F"),
        contains(
          logger.messages.mkString("\n"),
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
        ),
        notContains(logger.messages.mkString, "Set("),
        notContains(logger.messages.mkString, "UsedName("),
      )
    )
  end internal

  private def external: Result =
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
    val byNames = incremental.invalidateExternal(
      relations,
      NamesChange("External", ModifiedNames(Set(UsedName("changed", Seq(UseScope.Default)))))
    )
    val byMacro =
      incremental.invalidateExternal(relations, APIChangeDueToMacroDefinition("External"))
    val messages = logger.messages.mkString("\n")
    Result.all(
      List(
        byNames ==== Set("B", "C", "D", "E", "G", "H"),
        byMacro ==== Set("B", "C", "D", "E", "G", "H"),
        contains(messages, "[inv] External API change: External: modified names changed"),
        contains(messages, "[inv] External API change: External: macro definition changed"),
        contains(messages, "[inv]   transitive inheritance:"),
        contains(messages, "[inv]   local inheritance:"),
        contains(messages, "[inv]   member reference:"),
        contains(messages, "[inv]   macro expansion:"),
      )
    )
  end external

  private def collisions: Result =
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
    val collided = incremental.invalidateAfterInternalCompilation(
      Analysis.empty.copy(relations = collisionRelations),
      new APIChanges(Nil),
      Set.empty,
      invalidateTransitively = false,
      _ => true
    )
    val inheritanceRelations = Relations.empty.addInternalSrcDeps(
      sourceA,
      Seq(InternalDependency.of("B", "A", DependencyContext.DependencyByInheritance))
    )
    val transitive = incremental.invalidateAfterInternalCompilation(
      Analysis.empty.copy(relations = inheritanceRelations),
      new APIChanges(
        Seq(NamesChange("A", ModifiedNames(Set(UsedName("changed", Seq(UseScope.Default))))))
      ),
      Set("A"),
      invalidateTransitively = true,
      _ => true
    )
    val messages = logger.messages.mkString("\n")
    Result.all(
      List(
        collided ==== Set("A", "B"),
        transitive ==== Set("A", "B"),
        contains(messages, "[inv] Generated class-file collision"),
        contains(messages, "[inv] Brute-force transitive invalidation"),
        Result.assert(logger.messages.forall(_.startsWith("[inv] "))).log(messages),
      )
    )
  end collisions

  private def initialStates: Result =
    val logger = new RecordingLogger
    val incremental = new TestIncremental(logger)
    val noChanges = InitialChanges(emptyChanges, Set.empty, Set.empty, new APIChanges(Nil))
    val fresh = incremental.invalidateInitial(Relations.empty, noChanges)
    val source = VirtualFileRef.of("/src/A.scala")
    val existingRelations = Relations.empty.addProducts(
      source,
      Seq(VirtualFileRef.of("/classes/A.class"))
    )
    val existing = incremental.invalidateInitial(existingRelations, noChanges)
    Result.all(
      List(
        fresh ==== (Set.empty, Set.empty),
        existing ==== (Set.empty, Set.empty),
        logger.messages.toList ==== List(
          "[inv] Initial changes\n[inv]   full compilation",
          "[inv] Initial changes\n[inv]   no changes",
        ),
      )
    )
  end initialStates

  /** Triple-quoted literals inherit the checkout's line endings (CRLF on Windows CI). */
  private def expectedLines(s: String): String = s.stripMargin.replace("\r\n", "\n")

  private final class TestIncremental(logger: Logger, relationsDebug: Boolean = false)
      extends IncrementalNameHashingCommon(
        logger,
        IncOptions.of().withRelationsDebug(relationsDebug),
        RunProfiler.empty
      ):
    def invalidateInternal(relations: Relations, change: APIChange): Set[String] =
      invalidateClassesInternally(relations, change, _ => true)

    def invalidateExternal(relations: Relations, change: APIChange): Set[String] =
      invalidateClassesExternally(relations, change, _ => true)

  private val emptyChanges = new Changes[VirtualFileRef]:
    override def getAdded = Collections.emptySet[VirtualFileRef]
    override def getRemoved = Collections.emptySet[VirtualFileRef]
    override def getChanged = Collections.emptySet[VirtualFileRef]
    override def getUnmodified = Collections.emptySet[VirtualFileRef]
    override def isEmpty: java.lang.Boolean = java.lang.Boolean.TRUE

  private final class RecordingLogger extends Logger:
    val messages: ArrayBuffer[String] = ArrayBuffer.empty

    override def trace(t: => Throwable): Unit = ()
    override def success(message: => String): Unit = ()
    override def log(level: Level.Value, message: => String): Unit =
      if level == Level.Debug || level == Level.Info then messages += message

  private final class DiscardingLogger extends Logger:
    override def trace(t: => Throwable): Unit = ()
    override def success(message: => String): Unit = ()
    override def log(level: Level.Value, message: => String): Unit = ()
end InvalidationLogSpec
