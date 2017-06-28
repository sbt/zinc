package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc.{ Compilation, Compilations, Hash, LastModified, Mapper, schema }
import xsbti.{ Position, Problem, Severity }
import xsbti.compile.analysis.{ SourceInfo, Stamp }
import xsbti.compile.{ MultipleOutput, Output, OutputGroup, SingleOutput }

object ProtobufWriters {

  def toSchema(stamp: Stamp): schema.StampType = {
    val s0 = schema.StampType()
    stamp match {
      case hash: Hash       => s0.withHash(schema.Hash(hash = hash.hexHash))
      case lm: LastModified => s0.withLastModified(schema.LastModified(millis = lm.value))
      case _: Stamp         => s0
    }
  }

  def toSchemaMap(data: Map[File, Stamp],
                  fileMapper: Mapper[File]): Map[String, schema.StampType] =
    data.map(kv => fileMapper.write(kv._1) -> toSchema(kv._2))

  def toOutputGroup(outputGroup: OutputGroup): schema.OutputGroup = {
    val sourcePath = outputGroup.getSourceDirectory.getAbsolutePath
    val targetPath = outputGroup.getOutputDirectory.getAbsolutePath
    schema.OutputGroup(source = sourcePath, target = targetPath)
  }

  def toOutput(output: Output): schema.Compilation.Output = {
    import schema.Compilation.{ Output => CompilationOutput }
    output match {
      case single0: SingleOutput =>
        val targetPath = single0.getOutputDirectory.getAbsolutePath
        val single = schema.SingleOutput(target = targetPath)
        CompilationOutput.SingleOutput(single)
      case multiple0: MultipleOutput =>
        val groups = multiple0.getOutputGroups.iterator.map(toOutputGroup).toList
        val multiple = schema.MultipleOutput(outputGroups = groups)
        CompilationOutput.MultipleOutput(multiple)
      case unknown =>
        sys.error("Expected `Output` to be either `SingleOutput` or `MultipleOutput`.")
    }
  }

  def toCompilation(compilation: Compilation): schema.Compilation = {
    val startTime = compilation.getStartTime
    val output = toOutput(compilation.getOutput)
    schema.Compilation(startTime = startTime, output = output)
  }

  def toCompilations(compilations0: Compilations): schema.Compilations = {
    val compilations = compilations0.allCompilations.map(toCompilation)
    schema.Compilations(compilations = compilations)
  }

  import CommonData.{ MissingString, MissingInt }
  import sbt.internal.inc.JavaInterfaceUtil._
  def toPosition(position: Position): schema.Position = {
    schema.Position(
      line = position.line.toOption.fold(MissingInt)(_.toInt),
      offset = position.offset.toOption.fold(MissingInt)(_.toInt),
      lineContent = position.lineContent,
      pointer = position.pointer.toOption.fold(MissingInt)(_.toInt),
      pointerSpace = position.pointerSpace.toOption.getOrElse(MissingString),
      sourcePath = position.sourcePath.toOption.getOrElse(MissingString),
      sourceFilepath = position.sourceFile.toOption.fold(MissingString)(_.getAbsolutePath)
    )
  }

  def toSeverity(severity: Severity): schema.Severity = {
    severity match {
      case Severity.Info  => schema.Severity.INFO
      case Severity.Warn  => schema.Severity.WARN
      case Severity.Error => schema.Severity.ERROR
    }
  }

  def toProblem(problem: Problem): schema.Problem = {
    val category = problem.category()
    val message = problem.message()
    val position = Option(toPosition(problem.position()))
    val severity = toSeverity(problem.severity())
    schema.Problem(
      category = category,
      message = message,
      position = position,
      severity = severity
    )
  }

  def toSourceInfo(sourceInfo: SourceInfo): schema.SourceInfo = {
    val mainClasses = sourceInfo.getMainClasses
    val reportedProblems = sourceInfo.getReportedProblems.map(toProblem).toSeq
    val unreportedProblems = sourceInfo.getUnreportedProblems.map(toProblem).toSeq
    schema.SourceInfo(
      reportedProblems = reportedProblems,
      unreportedProblems = unreportedProblems,
      mainClasses = mainClasses
    )
  }
}
