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

// import sbt.internal.prof.Zprof
import xsbti.UseScope._
import xsbti.VirtualFileRef
import xsbti.compile.{ APIChange => XAPIChange }
import xsbti.compile.{ InitialChanges => XInitialChanges }
import xsbti.compile.{ InvalidationProfiler => XInvalidationProfiler }
import xsbti.compile.{ RunProfiler => XRunProfiler }

import scala.jdk.CollectionConverters._
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
  // def profileRun(): RunProfiler
  // def registerRun(run: Zprof.ZincRun): Unit
}

object InvalidationProfiler {
  final val empty: InvalidationProfiler = new InvalidationProfiler {
    // override def profileRun(): RunProfiler = RunProfiler.empty
    // override def registerRun(run: Zprof.ZincRun): Unit = ()
  }
}

/*
class ZincInvalidationProfiler extends InvalidationProfiler with XInvalidationProfiler { profiler =>
  /* The string table contains any kind of repeated string that is likely to occur
 * in the protobuf profiling data. This includes used names, class names, source
 * files and class files (their paths), as well as other repeated strings. This is
 * done to keep the memory overhead of the profiler to a minimum. */
  private final val stringTable: ArrayBuffer[String] = new ArrayBuffer[String](1000)
  private final val stringTableIndices: mutable.HashMap[String, Int] =
    new mutable.HashMap[String, Int]

  def profileRun: AdaptedRunProfiler = new ZincProfilerImplementation

  private final var runs: List[Zprof.ZincRun] = Nil

  def registerRun(run: Zprof.ZincRun): Unit = runs ::= run

  /**
 * Returns an immutable zprof profile that can be serialized.
 *
 * It is recommended to only perform this operation when we are
 * going to persist the profiled protobuf data to disk. Do not
 * call this function after every compiler iteration as you will
 * write a symbol table in every persisted protobuf file. It's
 * better to persist this file periodically after several runs
 * so that the overhead to disk is not high.
 *
 * @return An immutable zprof profile that can be persisted via protobuf.
 */
  def toProfile: Zprof.Profile =
    Zprof.Profile.newBuilder
      .addAllRuns(runs.asJava)
      .addAllStringTable(stringTable.toList.asJava)
      .build

  private[inc] class ZincProfilerImplementation
      extends AdaptedRunProfiler(new ZincXRunProfilerImplementation)

  private[inc] final class ZincXRunProfilerImplementation extends XRunProfiler {
    private def memo(string: String): Int = { // was "toStringTableIndices"
      stringTableIndices.getOrElseUpdate(
        string, {
          stringTable += string
          stringTable.length - 1
        }
      )
    }

    private var compilationStartNanos: Long = 0L
    private var compilationDurationNanos: Long = 0L
    private var initialChanges: Zprof.InitialChanges = Zprof.InitialChanges.getDefaultInstance
    private var invalidationEvents: List[Zprof.InvalidationEvent] = Nil
    private var cycleInvalidations: List[Zprof.CycleInvalidation] = Nil

    def timeCompilation(startNanos: Long, durationNanos: Long): Unit = {
      compilationStartNanos = startNanos
      compilationDurationNanos = durationNanos
    }

    private def toApiChanges(changes: Array[XAPIChange]) = {
      def toUsedNames(names: Iterable[UsedName]) = {
        listMap(names) { name =>
          val scopes = listMap(name.scopes.asScala) {
            case Default      => Zprof.Scope.newBuilder.setKind(memo("default")).build
            case Implicit     => Zprof.Scope.newBuilder.setKind(memo("implicit")).build
            case PatMatTarget => Zprof.Scope.newBuilder.setKind(memo("patmat target")).build
          }
          Zprof.UsedName.newBuilder
            .setName(memo(name.name))
            .addAllScopes(scopes)
            .build
        }
      }

      listMap(changes) {
        case change: APIChangeDueToMacroDefinition =>
          Zprof.ApiChange.newBuilder
            .setModifiedClass(memo(change.modifiedClass))
            .setReason("API change due to macro definition.")
            .build
        case change: APIChangeDueToAnnotationDefinition =>
          Zprof.ApiChange.newBuilder
            .setModifiedClass(memo(change.modifiedClass))
            .setReason("API change due to annotation definition.")
            .build
        case change: TraitPrivateMembersModified =>
          Zprof.ApiChange.newBuilder
            .setModifiedClass(memo(change.modifiedClass))
            .setReason("API change due to existence of private trait members in modified class.")
            .build
        case NamesChange(modifiedClass, modifiedNames) =>
          val usedNames = toUsedNames(modifiedNames.names)
          Zprof.ApiChange.newBuilder
            .setModifiedClass(memo(modifiedClass))
            .setReason("Standard API name change in modified class.")
            .addAllUsedNames(usedNames)
            .build
      }
    }

    def registerInitial(changes: XInitialChanges): Unit = {
      val fileChanges = changes.getInternalSrc
      val profChanges = Zprof.Changes.newBuilder
        .addAllAdded(listMap(fileChanges.getAdded.asScala)(f => memo(f.id)))
        .addAllRemoved(listMap(fileChanges.getRemoved.asScala)(f => memo(f.id)))
        .addAllModified(listMap(fileChanges.getChanged.asScala)(f => memo(f.id)))
        .build
      initialChanges = Zprof.InitialChanges.newBuilder
        .setChanges(profChanges)
        .addAllRemovedProducts(listMap(changes.getRemovedProducts.asScala)(f => memo(f.id)))
        .addAllBinaryDependencies(listMap(changes.getLibraryDeps.asScala)(f => memo(f.id)))
        .addAllExternalChanges(toApiChanges(changes.getExternal))
        .build
    }

    def registerEvent(
        kind: String,
        inputs: Array[String],
        outputs: Array[String],
        reason: String
    ): Unit = {
      invalidationEvents ::= Zprof.InvalidationEvent.newBuilder
        .setKind(kind)
        .addAllInputs(listMap(inputs)(memo))
        .addAllOutputs(listMap(outputs)(memo))
        .setReason(reason)
        .build
    }

    def registerCycle(
        invalidatedClasses: Array[String],
        invalidatedPackageObjects: Array[String],
        initialSources: Array[VirtualFileRef],
        invalidatedSources: Array[VirtualFileRef],
        recompiledClasses: Array[String],
        changesAfterRecompilation: Array[XAPIChange],
        nextInvalidations: Array[String],
        shouldCompileIncrementally: Boolean
    ): Unit = {
      cycleInvalidations ::= Zprof.CycleInvalidation.newBuilder
        .addAllInvalidated(listMap(invalidatedClasses)(memo))
        .addAllInvalidatedByPackageObjects(listMap(invalidatedPackageObjects)(memo))
        .addAllInitialSources(listMap(initialSources)(f => memo(f.id)))
        .addAllInvalidatedSources(listMap(invalidatedSources)(f => memo(f.id)))
        .addAllRecompiledClasses(listMap(recompiledClasses)(memo))
        .addAllChangesAfterRecompilation(toApiChanges(changesAfterRecompilation))
        .addAllNextInvalidations(listMap(nextInvalidations)(memo))
        .setStartTimeNanos(compilationStartNanos)
        .setCompilationDurationNanos(compilationDurationNanos)
        .addAllEvents(invalidationEvents.asJava)
        .setShouldCompileIncrementally(shouldCompileIncrementally)
        .build
    }

    def toRun: Zprof.ZincRun =
      Zprof.ZincRun.newBuilder
        .setInitial(initialChanges)
        .addAllCycles(cycleInvalidations.asJava)
        .build

    def registerRun(): Unit = profiler.registerRun(toRun)

    private def listMap[A, B](xs: Iterable[A])(f: A => B) = xs.iterator.map(f).toList.asJava
  }
}
 */

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
  final val empty = new AdaptedRunProfiler(XRunProfiler.EMPTY.INSTANCE)
}

sealed class AdaptedRunProfiler(val profiler: XRunProfiler)
    extends RunProfiler
    with XRunProfiler.DelegatingRunProfiler {
  override def registerInitial(changes: InitialChanges): Unit = profiler.registerInitial(changes)

  override def registerEvent(
      kind: String,
      inputs: Iterable[String],
      outputs: Iterable[String],
      reason: String
  ): Unit = profiler.registerEvent(kind, inputs.toArray, outputs.toArray, reason)

  override def registerCycle(
      invalidatedClasses: Iterable[String],
      invalidatedPackageObjects: Iterable[String],
      initialSources: Iterable[VirtualFileRef],
      invalidatedSources: Iterable[VirtualFileRef],
      recompiledClasses: Iterable[String],
      changesAfterRecompilation: APIChanges,
      nextInvalidations: Iterable[String],
      shouldCompileIncrementally: Boolean
  ): Unit = profiler.registerCycle(
    invalidatedClasses.toArray,
    invalidatedPackageObjects.toArray,
    initialSources.toArray,
    invalidatedSources.toArray,
    recompiledClasses.toArray,
    changesAfterRecompilation.apiChanges.toArray[XAPIChange],
    nextInvalidations.toArray,
    shouldCompileIncrementally,
  )
}

trait InvalidationProfilerUtils {
  final val LocalInheritanceKind = "local inheritance"
  final val InheritanceKind = "inheritance"
  final val MemberReferenceKind = "member reference"
  final val MacroExpansionKind = "macro expansion"
}

/*
// So that others users from outside [[IncrementalCommon]] can use the labels
object InvalidationProfilerUtils extends InvalidationProfilerUtils
 */
