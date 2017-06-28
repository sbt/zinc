package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc.schema
import sbt.internal.inc.Compilations
import sbt.internal.inc.{ ConcreteMultipleOutput, ConcreteSingleOutput, SimpleOutputGroup }
import xsbti.Position
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

  def fromPosition(position: schema.Position): Position = {
    import CommonData.{ MissingString, MissingInt }
    def fromString(value: String): Option[String] =
      if (value == MissingString) None else Some(value)
    def fromInt(value: Int): Option[Integer] =
      if (value == MissingInt) None else Some(value)
    sbt.util.InterfaceUtil.position(
      line0 = fromInt(position.line),
      content = position.lineContent,
      offset0 = fromInt(position.offset),
      pointer0 = fromInt(position.pointer),
      pointerSpace0 = fromString(position.pointerSpace),
      sourcePath0 = fromString(position.sourcePath),
      sourceFile0 = fromString(position.sourceFilepath).map(new File(_))
    )
  }
}
