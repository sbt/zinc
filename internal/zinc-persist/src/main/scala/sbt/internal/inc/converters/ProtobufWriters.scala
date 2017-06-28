package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc.{ Compilation, Compilations, Hash, LastModified, Mapper, schema }
import xsbti.Position
import xsbti.compile.analysis.Stamp
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
}
