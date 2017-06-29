package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc.{ Compilation, Compilations, Hash, LastModified, Stamps, schema }
import xsbti.{ Position, Problem, Severity, T2 }
import xsbti.compile.analysis.{ SourceInfo, Stamp }
import sbt.internal.inc.converters.Feedback.{ Writers => WritersFeedback }
import xsbti.compile.{
  CompileOrder,
  FileHash,
  MiniOptions,
  MiniSetup,
  MultipleOutput,
  Output,
  OutputGroup,
  SingleOutput
}

object ProtobufWriters {
  def toStampType(stamp: Stamp): schema.Stamps.StampType = {
    val s0 = schema.Stamps.StampType()
    stamp match {
      case hash: Hash       => s0.withHash(schema.Hash(hash = hash.hexHash))
      case lm: LastModified => s0.withLastModified(schema.LastModified(millis = lm.value))
      case _: Stamp         => s0
    }
  }

  def toStamps(stamps: Stamps): schema.Stamps = {
    def toSchemaMap(data: Map[File, Stamp]): Map[String, schema.Stamps.StampType] =
      data.map(kv => kv._1.getAbsolutePath -> toStampType(kv._2))

    val binaryStamps = toSchemaMap(stamps.binaries)
    val sourceStamps = toSchemaMap(stamps.sources)
    val productStamps = toSchemaMap(stamps.products)
    schema.Stamps(
      binaryStamps = binaryStamps,
      sourceStamps = sourceStamps,
      productStamps = productStamps
    )
  }

  def toOutputGroup(outputGroup: OutputGroup): schema.OutputGroup = {
    val sourcePath = outputGroup.getSourceDirectory.getAbsolutePath
    val targetPath = outputGroup.getOutputDirectory.getAbsolutePath
    schema.OutputGroup(source = sourcePath, target = targetPath)
  }

  def toCompilationOutput(output: Output): schema.Compilation.Output = {
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
      case unknown => sys.error(WritersFeedback.ExpectedNonEmptyOutput)
    }
  }

  def toCompilation(compilation: Compilation): schema.Compilation = {
    val startTime = compilation.getStartTime
    val output = toCompilationOutput(compilation.getOutput)
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

  def toFileHash(fileHash: FileHash): schema.FileHash = {
    schema.FileHash(
      path = fileHash.file.getAbsolutePath,
      hash = fileHash.hash
    )
  }

  def toMiniOptions(miniOptions: MiniOptions): schema.MiniOptions = {
    val classpathHash = miniOptions.classpathHash.map(toFileHash)
    val javacOptions = miniOptions.javacOptions()
    val scalacOptions = miniOptions.scalacOptions()
    schema.MiniOptions(
      classpathHash = classpathHash,
      javacOptions = javacOptions,
      scalacOptions = scalacOptions
    )
  }

  def toCompileOrder(compileOrder: CompileOrder): schema.CompileOrder = {
    compileOrder match {
      case CompileOrder.Mixed         => schema.CompileOrder.MIXED
      case CompileOrder.JavaThenScala => schema.CompileOrder.JAVATHENSCALA
      case CompileOrder.ScalaThenJava => schema.CompileOrder.SCALATHENJAVA
    }
  }

  def toStringTuple(tuple: T2[String, String]): schema.Tuple = {
    schema.Tuple(first = tuple.get1(), second = tuple.get2())
  }

  def toMiniSetupOutput(output: Output): schema.MiniSetup.Output = {
    import schema.MiniSetup.{ Output => CompilationOutput }
    output match {
      case single0: SingleOutput =>
        val targetPath = single0.getOutputDirectory.getAbsolutePath
        val single = schema.SingleOutput(target = targetPath)
        CompilationOutput.SingleOutput(single)
      case multiple0: MultipleOutput =>
        val groups = multiple0.getOutputGroups.iterator.map(toOutputGroup).toList
        val multiple = schema.MultipleOutput(outputGroups = groups)
        CompilationOutput.MultipleOutput(multiple)
      case unknown => sys.error(WritersFeedback.ExpectedNonEmptyOutput)
    }
  }

  def toMiniSetup(miniSetup: MiniSetup): schema.MiniSetup = {
    val output = toMiniSetupOutput(miniSetup.output())
    val miniOptions = Some(toMiniOptions(miniSetup.options()))
    val compilerVersion = miniSetup.compilerVersion()
    val compileOrder = toCompileOrder(miniSetup.order())
    val storeApis = miniSetup.storeApis()
    val extra = miniSetup.extra().map(toStringTuple)
    schema.MiniSetup(
      output = output,
      miniOptions = miniOptions,
      compilerVersion = compilerVersion,
      compileOrder = compileOrder,
      storeApis = storeApis,
      extra = extra
    )
  }
}
