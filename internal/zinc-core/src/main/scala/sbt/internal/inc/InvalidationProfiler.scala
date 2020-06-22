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

package sbt.internal.inc

import sbt.internal.prof.Zprof
import xsbti.{ UseScope, VirtualFileRef }
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Defines a profiler interface that translates to the profiling protobuf schema.
 *
 * The protobuf schema has been mildly inspired from pprof:
 * https://github.com/google/pprof/blob/master/proto/profile.proto
 *
 * A profiler interface should only be used by project, and not globally, as
 * this class is not thread safe.
 */
abstract class InvalidationProfiler {
  def profileRun: RunProfiler
  def registerRun(run: Zprof.ZincRun): Unit
}

object InvalidationProfiler {
  final val empty: InvalidationProfiler = new InvalidationProfiler {
    override def profileRun: RunProfiler = RunProfiler.empty
    override def registerRun(run: Zprof.ZincRun): Unit = ()
  }
}

class ZincInvalidationProfiler extends InvalidationProfiler {
  private final var lastKnownIndex: Int = -1
  /* The string table contains any kind of repeated string that is likely to occur
   * in the protobuf profiling data. This includes used names, class names, source
   * files and class files (their paths), as well as other repeated strings. This is
   * done to keep the memory overhead of the profiler to a minimum. */
  private final val stringTable: ArrayBuffer[String] = new ArrayBuffer[String](1000)

  /* Maps strings to indices. The indices are long because we're overprotecting ourselves
   * in case the string table grows gigantic. This should not happen, but as the profiling
   * scheme of pprof does it and it's not cumbersome to implement it, we replicate the same design. */
  private final val stringTableIndices: mutable.HashMap[String, Int] =
    new mutable.HashMap[String, Int]

  def profileRun: RunProfiler = new ZincProfilerImplementation

  private final var runs: List[Zprof.ZincRun] = Nil
  def registerRun(run: Zprof.ZincRun): Unit = {
    runs = run :: runs
    ()
  }

  /**
   * Returns an immutable zprof profile that can be serialized.
   *
   * It is recommended to only perform this operation when we are
   * going to persist the profiled protobuf data to disk. Do not
   * call this function after every compiler iteration as you will
   * write a symbol table in every persisted protobuf file. It's
   * better to persist this file periodically after several runs
   * so that the overhead in disk is not high.
   *
   * @return An immutable zprof profile that can be persisted via protobuf.
   */
  def toProfile: Zprof.Profile =
    Zprof.Profile.newBuilder
      .addAllRuns(runs.asJava)
      .addAllStringTable(stringTable.toList.asJava)
      .build

  private[inc] class ZincProfilerImplementation extends RunProfiler {
    private def toStringTableIndex(string: String): Int = {
      stringTableIndices.get(string) match {
        case Some(index) =>
          val newIndex = index.toInt
          stringTable.apply(newIndex)
          newIndex
        case None =>
          val newIndex = lastKnownIndex + 1
          // Depending on the size of the index, use the first or second symbol table
          stringTable.insert(newIndex.toInt, string)
          stringTableIndices.put(string, newIndex)
          lastKnownIndex = lastKnownIndex + 1
          newIndex
      }
    }

    private def toStringTableIndices(strings: Iterable[String]): Iterable[java.lang.Integer] =
      strings.map(x => (toStringTableIndex(x): java.lang.Integer))

    private final var compilationStartNanos: Long = 0L
    private final var compilationDurationNanos: Long = 0L
    def timeCompilation(startNanos: Long, durationNanos: Long): Unit = {
      compilationStartNanos = startNanos
      compilationDurationNanos = durationNanos
    }

    private def toPathStrings(files: Iterable[VirtualFileRef]): Iterable[String] =
      files.map(_.id)

    def toApiChanges(changes: APIChanges): Iterable[Zprof.ApiChange] = {
      def toUsedNames(names: Iterable[UsedName]): Iterable[Zprof.UsedName] = {
        import scala.collection.JavaConverters._
        names.map { name =>
          val scopes = name.scopes.asScala.map {
            case UseScope.Default =>
              Zprof.Scope.newBuilder.setKind(toStringTableIndex("default")).build
            case UseScope.Implicit =>
              Zprof.Scope.newBuilder.setKind(toStringTableIndex("implicit")).build
            case UseScope.PatMatTarget =>
              Zprof.Scope.newBuilder.setKind(toStringTableIndex("patmat target")).build
          }
          Zprof.UsedName.newBuilder
            .setName(toStringTableIndex(name.name))
            .addAllScopes(scopes.toList.asJava)
            .build
        }
      }

      changes.apiChanges.map {
        case change: APIChangeDueToMacroDefinition =>
          Zprof.ApiChange.newBuilder
            .setModifiedClass(toStringTableIndex(change.modifiedClass))
            .setReason("API change due to macro definition.")
            .build
        case change: TraitPrivateMembersModified =>
          Zprof.ApiChange.newBuilder
            .setModifiedClass(toStringTableIndex(change.modifiedClass))
            .setReason(s"API change due to existence of private trait members in modified class.")
            .build
        case NamesChange(modifiedClass, modifiedNames) =>
          val usedNames = toUsedNames(modifiedNames.names).toList
          Zprof.ApiChange.newBuilder
            .setModifiedClass(toStringTableIndex(modifiedClass))
            .setReason(s"Standard API name change in modified class.")
            .addAllUsedNames(usedNames.asJava)
            .build
      }
    }

    def registerInitial(changes: InitialChanges): Unit = {
      val fileChanges = changes.internalSrc
      val profChanges = Zprof.Changes.newBuilder
        .addAllAdded(
          toStringTableIndices(toPathStrings(fileChanges.getAdded.asScala)).toList.asJava
        )
        .addAllRemoved(
          toStringTableIndices(toPathStrings(fileChanges.getRemoved.asScala)).toList.asJava
        )
        .addAllModified(
          toStringTableIndices(toPathStrings(fileChanges.getChanged.asScala)).toList.asJava
        )
        .build
      Zprof.InitialChanges.newBuilder
        .setChanges(profChanges)
        .addAllRemovedProducts(
          toStringTableIndices(toPathStrings(changes.removedProducts)).toList.asJava
        )
        .addAllBinaryDependencies(
          toStringTableIndices(toPathStrings(changes.libraryDeps)).toList.asJava
        )
        .addAllExternalChanges(toApiChanges(changes.external).toList.asJava)
        .build
      ()
    }

    private final var currentEvents: List[Zprof.InvalidationEvent] = Nil
    def registerEvent(
        kind: String,
        inputs: Iterable[String],
        outputs: Iterable[String],
        reason: String
    ): Unit = {
      val event = Zprof.InvalidationEvent.newBuilder
        .setKind(kind)
        .addAllInputs(toStringTableIndices(inputs).toList.asJava)
        .addAllOutputs(toStringTableIndices(outputs).toList.asJava)
        .setReason(reason)
        .build
      currentEvents = event :: currentEvents
    }

    private final var cycles: List[Zprof.CycleInvalidation] = Nil
    def registerCycle(
        invalidatedClasses: Iterable[String],
        invalidatedPackageObjects: Iterable[String],
        initialSources: Iterable[VirtualFileRef],
        invalidatedSources: Iterable[VirtualFileRef],
        recompiledClasses: Iterable[String],
        changesAfterRecompilation: APIChanges,
        nextInvalidations: Iterable[String],
        shouldCompileIncrementally: Boolean
    ): Unit = {
      val newCycle = Zprof.CycleInvalidation.newBuilder
        .addAllInvalidated(toStringTableIndices(invalidatedClasses).toList.asJava)
        .addAllInvalidatedByPackageObjects(
          toStringTableIndices(invalidatedPackageObjects).toList.asJava
        )
        .addAllInitialSources(toStringTableIndices(toPathStrings(initialSources)).toList.asJava)
        .addAllInvalidatedSources(
          toStringTableIndices(toPathStrings(invalidatedSources)).toList.asJava
        )
        .addAllRecompiledClasses(toStringTableIndices(recompiledClasses).toList.asJava)
        .addAllChangesAfterRecompilation(toApiChanges(changesAfterRecompilation).toList.asJava)
        .addAllNextInvalidations(toStringTableIndices(nextInvalidations).toList.asJava)
        .setStartTimeNanos(compilationStartNanos)
        .setCompilationDurationNanos(compilationDurationNanos)
        .addAllEvents(currentEvents.asJava)
        .setShouldCompileIncrementally(shouldCompileIncrementally)
        .build

      cycles = newCycle :: cycles
      ()
    }
  }
}

/**
 * Defines the interface of a profiler. This interface is used in the guts of
 * `IncrementalCommon` and `IncrementalNameHashing`. A profiler of a run
 * is instantiated afresh in `Incremental.compile` and then added to the profiler
 * instance managed by the client.
 */
abstract class RunProfiler {
  def timeCompilation(
      startNanos: Long,
      durationNanos: Long
  ): Unit

  def registerInitial(
      changes: InitialChanges
  ): Unit

  def registerEvent(
      kind: String,
      inputs: Iterable[String],
      outputs: Iterable[String],
      reason: String
  ): Unit

  def registerCycle(
      invalidatedClasses: Iterable[String],
      invalidatedPackageObjects: Iterable[String],
      initialSources: Iterable[VirtualFileRef],
      invalidatedSources: Iterable[VirtualFileRef],
      recompiledClasses: Iterable[String],
      changesAfterRecompilation: APIChanges,
      nextInvalidations: Iterable[String],
      shouldCompileIncrementally: Boolean
  ): Unit
}

object RunProfiler {
  final val empty = new RunProfiler {
    def timeCompilation(startNanos: Long, durationNanos: Long): Unit = ()
    def registerInitial(changes: InitialChanges): Unit = ()

    def registerEvent(
        kind: String,
        inputs: Iterable[String],
        outputs: Iterable[String],
        reason: String
    ): Unit = ()
    def registerCycle(
        invalidatedClasses: Iterable[String],
        invalidatedPackageObjects: Iterable[String],
        initialSources: Iterable[VirtualFileRef],
        invalidatedSources: Iterable[VirtualFileRef],
        recompiledClasses: Iterable[String],
        changesAfterRecompilation: APIChanges,
        nextInvalidations: Iterable[String],
        shouldCompileIncrementally: Boolean
    ): Unit = ()
  }
}

trait InvalidationProfilerUtils {
  final val LocalInheritanceKind = "local inheritance"
  final val InheritanceKind = "inheritance"
  final val MemberReferenceKind = "member reference"
}

// So that others users from outside [[IncrementalCommon]] can use the labels
object InvalidationProfilerUtils extends InvalidationProfilerUtils
