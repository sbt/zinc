package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc.{
  Compilations,
  ConcreteMultipleOutput,
  ConcreteSingleOutput,
  SimpleOutputGroup,
  SourceInfos,
  Stamps,
  schema
}
import sbt.util.InterfaceUtil
import xsbti.{ Position, Problem, Severity }
import xsbti.compile.{ Output, OutputGroup }
import xsbti.compile.analysis.{ Compilation, SourceInfo, Stamp }

object ProtobufReaders {
  def fromStampType(stampType: schema.Stamps.StampType): Stamp = {
    import sbt.internal.inc.{ EmptyStamp, LastModified, Hash }
    stampType.`type` match {
      case schema.Stamps.StampType.Type.Empty            => EmptyStamp
      case schema.Stamps.StampType.Type.Hash(h)          => new Hash(h.hash)
      case schema.Stamps.StampType.Type.LastModified(lm) => new LastModified(lm.millis)
      // ^ NOTE: Double check that we recompute millis when reading this in certain conditions
    }
  }

  def fromStamps(stamps: schema.Stamps): Stamps = {
    def fromSchemaMap(stamps: Map[String, schema.Stamps.StampType]): Map[File, Stamp] =
      stamps.map(kv => new File(kv._1) -> fromStampType(kv._2))
    val binaries = fromSchemaMap(stamps.binaryStamps)
    val sources = fromSchemaMap(stamps.sourceStamps)
    val products = fromSchemaMap(stamps.productStamps)
    Stamps(
      binaries = binaries,
      sources = sources,
      products = products
    )
  }

  def fromOutputGroup(outputGroup: schema.OutputGroup): OutputGroup = {
    val source = new File(outputGroup.source)
    val target = new File(outputGroup.target)
    SimpleOutputGroup(source, target)
  }

  def fromOutput(output: schema.Compilation.Output): Output = {
    output match {
      case schema.Compilation.Output.SingleOutput(single) =>
        val target = new File(single.target)
        new ConcreteSingleOutput(target)
      case schema.Compilation.Output.MultipleOutput(multiple) =>
        val groups = multiple.outputGroups.iterator.map(fromOutputGroup).toArray
        new ConcreteMultipleOutput(groups)
      case schema.Compilation.Output.Empty =>
        sys.error(SerializationFeedback.ExpectedNonEmptyOutput)
    }
  }

  def fromCompilation(compilation: schema.Compilation): Compilation = {
    val output = fromOutput(compilation.output)
    new sbt.internal.inc.Compilation(compilation.startTime, output)
  }

  def fromCompilations(compilations0: schema.Compilations): Compilations = {
    val compilations = compilations0.compilations.map(fromCompilation).toList
    val castedCompilations = compilations.map { case c: sbt.internal.inc.Compilation => c }
    Compilations.make(castedCompilations)
  }

  def fromPosition(position: schema.Position): Position = {
    import CommonData.{ MissingString, MissingInt }
    def fromString(value: String): Option[String] =
      if (value == MissingString) None else Some(value)
    def fromInt(value: Int): Option[Integer] =
      if (value == MissingInt) None else Some(value)
    InterfaceUtil.position(
      line0 = fromInt(position.line),
      content = position.lineContent,
      offset0 = fromInt(position.offset),
      pointer0 = fromInt(position.pointer),
      pointerSpace0 = fromString(position.pointerSpace),
      sourcePath0 = fromString(position.sourcePath),
      sourceFile0 = fromString(position.sourceFilepath).map(new File(_))
    )
  }

  def fromSeverity(severity: schema.Severity): Severity = {
    severity match {
      case schema.Severity.INFO  => Severity.Info
      case schema.Severity.WARN  => Severity.Warn
      case schema.Severity.ERROR => Severity.Error
    }
  }

  def fromProblem(problem: schema.Problem): Problem = {
    val category = problem.category
    val message = problem.message
    val severity = fromSeverity(problem.severity)
    val position = problem.position
      .map(fromPosition)
      .getOrElse(sys.error(SerializationFeedback.ExpectedPositionInProblem))
    InterfaceUtil.problem(category, position, message, severity)
  }

  def fromSourceInfo(sourceInfo: schema.SourceInfo): SourceInfo = {
    val mainClasses = sourceInfo.mainClasses
    val reportedProblems = sourceInfo.reportedProblems.map(fromProblem)
    val unreportedProblems = sourceInfo.unreportedProblems.map(fromProblem)
    SourceInfos.makeInfo(reported = reportedProblems,
                         unreported = unreportedProblems,
                         mainClasses = mainClasses)
  }
}
