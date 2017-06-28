package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc.schema
import sbt.internal.inc.Compilations
import sbt.internal.inc.{ ConcreteMultipleOutput, ConcreteSingleOutput, SimpleOutputGroup }
import xsbti.compile.{ Output, OutputGroup }
import xsbti.compile.analysis.Compilation

object ProtobufReaders {
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
        sys.error("Expected non-empty output from protobuf.")
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
}
