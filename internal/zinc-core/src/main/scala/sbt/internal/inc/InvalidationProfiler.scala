package sbt.internal.inc

import java.io.File

import xsbti.UseScope

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
  def registerRun(run: zprof.ZincRun): Unit
}

object InvalidationProfiler {
  final val empty: InvalidationProfiler = new InvalidationProfiler {
    override def profileRun: RunProfiler = RunProfiler.empty
    override def registerRun(run: zprof.ZincRun): Unit = ()
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

  private final var runs: List[zprof.ZincRun] = Nil
  def registerRun(run: zprof.ZincRun): Unit = {
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
  def toProfile: zprof.Profile = zprof.Profile(
    runs = runs,
    stringTable = stringTable
  )

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

    private def toStringTableIndices(strings: Iterable[String]): Iterable[Int] =
      strings.map(toStringTableIndex(_))

    private final var compilationStartNanos: Long = 0L
    private final var compilationDurationNanos: Long = 0L
    def timeCompilation(startNanos: Long, durationNanos: Long): Unit = {
      compilationStartNanos = startNanos
      compilationDurationNanos = durationNanos
    }

    private def toPathStrings(files: Iterable[File]): Iterable[String] =
      files.map(_.getAbsolutePath)

    def toApiChanges(changes: APIChanges): Iterable[zprof.ApiChange] = {
      def toUsedNames(names: Iterable[UsedName]): Iterable[zprof.UsedName] = {
        import scala.collection.JavaConverters._
        names.map { name =>
          val scopes = name.scopes.asScala.map {
            case UseScope.Default      => zprof.Scope(toStringTableIndex("default"))
            case UseScope.Implicit     => zprof.Scope(toStringTableIndex("implicit"))
            case UseScope.PatMatTarget => zprof.Scope(toStringTableIndex("patmat target"))
          }
          zprof.UsedName(toStringTableIndex(name.name), scopes.toList)
        }
      }

      changes.apiChanges.map {
        case change: APIChangeDueToMacroDefinition =>
          zprof.ApiChange(
            toStringTableIndex(change.modifiedClass),
            "API change due to macro definition."
          )
        case change: TraitPrivateMembersModified =>
          zprof.ApiChange(
            toStringTableIndex(change.modifiedClass),
            s"API change due to existence of private trait members in modified class."
          )
        case NamesChange(modifiedClass, modifiedNames) =>
          val usedNames = toUsedNames(modifiedNames.names).toList
          zprof.ApiChange(
            toStringTableIndex(modifiedClass),
            s"Standard API name change in modified class.",
            usedNames = usedNames
          )
      }
    }

    private final var initial: Option[zprof.InitialChanges] = None
    def registerInitial(changes: InitialChanges): Unit = {
      import scala.collection.JavaConverters._
      val fileChanges = changes.internalSrc
      val profChanges = zprof.Changes(
        added = toStringTableIndices(toPathStrings(fileChanges.getAdded.asScala)).toList,
        removed = toStringTableIndices(toPathStrings(fileChanges.getRemoved.asScala)).toList,
        modified = toStringTableIndices(toPathStrings(fileChanges.getChanged.asScala)).toList
      )
      initial = Some(
        zprof.InitialChanges(
          changes = Some(profChanges),
          removedProducts = toStringTableIndices(toPathStrings(changes.removedProducts)).toList,
          binaryDependencies = toStringTableIndices(toPathStrings(changes.binaryDeps)).toList,
          externalChanges = toApiChanges(changes.external).toList
        )
      )
    }

    private final var currentEvents: List[zprof.InvalidationEvent] = Nil
    def registerEvent(
        kind: String,
        inputs: Iterable[String],
        outputs: Iterable[String],
        reason: String
    ): Unit = {
      val event = zprof.InvalidationEvent(
        kind = kind,
        inputs = toStringTableIndices(inputs).toList,
        outputs = toStringTableIndices(outputs).toList,
        reason = reason
      )

      currentEvents = event :: currentEvents
    }

    private final var cycles: List[zprof.CycleInvalidation] = Nil
    def registerCycle(
        invalidatedClasses: Iterable[String],
        invalidatedPackageObjects: Iterable[String],
        initialSources: Iterable[File],
        invalidatedSources: Iterable[File],
        recompiledClasses: Iterable[String],
        changesAfterRecompilation: APIChanges,
        nextInvalidations: Iterable[String],
        shouldCompileIncrementally: Boolean
    ): Unit = {
      val newCycle = zprof.CycleInvalidation(
        invalidated = toStringTableIndices(invalidatedClasses).toList,
        invalidatedByPackageObjects = toStringTableIndices(invalidatedPackageObjects).toList,
        initialSources = toStringTableIndices(toPathStrings(initialSources)).toList,
        invalidatedSources = toStringTableIndices(toPathStrings(invalidatedSources)).toList,
        recompiledClasses = toStringTableIndices(recompiledClasses).toList,
        changesAfterRecompilation = toApiChanges(changesAfterRecompilation).toList,
        nextInvalidations = toStringTableIndices(nextInvalidations).toList,
        startTimeNanos = compilationStartNanos,
        compilationDurationNanos = compilationDurationNanos,
        events = currentEvents,
        shouldCompileIncrementally = shouldCompileIncrementally
      )

      cycles = newCycle :: cycles
      ()
    }
  }
}

/**
 * Defines the interface of a profiler. This interface is used in the guts of
 * [[IncrementalCommon]] and [[IncrementalNameHashing]]. A profiler of a run
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
      initialSources: Iterable[File],
      invalidatedSources: Iterable[File],
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
        initialSources: Iterable[File],
        invalidatedSources: Iterable[File],
        recompiledClasses: Iterable[String],
        changesAfterRecompilation: APIChanges,
        nextInvalidations: Iterable[String],
        shouldCompileIncrementally: Boolean
    ): Unit = ()
  }
}

trait InvalidationProfilerUtils {
  // Define this so that we can provide default labels for events in protobuf-generate companion
  implicit class InvalidationEventXCompanion(invalidationEvent: zprof.InvalidationEvent.type) {
    final val LocalInheritanceKind = "local inheritance"
    final val InheritanceKind = "inheritance"
    final val MemberReferenceKind = "member reference"
  }
}

// So that others users from outside [[IncrementalCommon]] can use the labels
object InvalidationProfilerUtils extends InvalidationProfilerUtils
