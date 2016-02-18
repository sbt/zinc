package sbt
package inc

import java.io.{ BufferedReader, File, StringReader, StringWriter }
import sbt.inc.Analysis.NonLocalProduct
import xsbti.DependencyContext

import scala.math.abs
import org.scalacheck._
import Gen._
import Prop._

object TextAnalysisFormatTest extends Properties("TextAnalysisFormat") {

  val nameHashing = true
  val dummyOutput = new xsbti.compile.SingleOutput { def outputDirectory: java.io.File = new java.io.File("dummy") }
  val commonSetup = new CompileSetup(dummyOutput, new CompileOptions(Nil, Nil), "2.10.4", xsbti.compile.CompileOrder.Mixed, nameHashing)
  val commonHeader = """format version: 5
                    |output mode:
                    |1 items
                    |0 -> single
                    |output directories:
                    |1 items
                    |output dir -> dummy
                    |compile options:
                    |0 items
                    |javac options:
                    |0 items
                    |compiler version:
                    |1 items
                    |0 -> 2.10.4
                    |compile order:
                    |1 items
                    |0 -> Mixed
                    |name hashing:
                    |1 items
                    |0 -> true""".stripMargin

  property("Write and read empty Analysis") = {

    val writer = new StringWriter
    val analysis = Analysis.empty(nameHashing)
    TextAnalysisFormat.write(writer, analysis, commonSetup)

    val result = writer.toString

    result.startsWith(commonHeader)
    val reader = new BufferedReader(new StringReader(result))

    val (readAnalysis, readSetup) = TextAnalysisFormat.read(reader)

    analysis == readAnalysis

  }

  property("Write and read simple Analysis") = {

    import TestCaseGenerators._

    def f(s: String) = new File(s)
    val aScala = f("A.scala")
    val bScala = f("B.scala")
    val aClass = genClass("A").sample.get
    val cClass = genClass("C").sample.get
    val exists = new Exists(true)
    val sourceInfos = SourceInfos.makeInfo(Nil, Nil)

    var analysis = Analysis.empty(nameHashing)
    val products = NonLocalProduct("A", "A", f("A.class"), exists) ::
      NonLocalProduct("A$", "A$", f("A$.class"), exists) :: Nil
    val classes = ("A" -> "A") :: ("A$" -> "A$") :: Nil
    val binaryDeps = (f("x.jar"), "x", exists) :: Nil
    val externalDeps = ExternalDependency("A", "C", cClass, DependencyContext.DependencyByMemberRef) :: Nil
    analysis = analysis.addSource(aScala, Seq(aClass), exists, sourceInfos, products, Nil, Nil, externalDeps, binaryDeps)

    val writer = new StringWriter

    TextAnalysisFormat.write(writer, analysis, commonSetup)

    val result = writer.toString

    result.startsWith(commonHeader)
    val reader = new BufferedReader(new StringReader(result))

    val (readAnalysis, readSetup) = TextAnalysisFormat.read(reader)

    compare(analysis, readAnalysis)

  }

  property("Write and read complex Analysis") =
    forAllNoShrink(TestCaseGenerators.genAnalysis(nameHashing)) { analysis: Analysis =>
      val writer = new StringWriter

      TextAnalysisFormat.write(writer, analysis, commonSetup)

      val result = writer.toString

      result.startsWith(commonHeader)
      val reader = new BufferedReader(new StringReader(result))

      val (readAnalysis, readSetup) = TextAnalysisFormat.read(reader)

      compare(analysis, readAnalysis)
    }

  // Compare two analyses with useful labelling when they aren't equal.
  private[this] def compare(left: Analysis, right: Analysis): Prop =
    s" LEFT: $left" |:
      s"RIGHT: $right" |:
      s"STAMPS EQUAL: ${left.stamps == right.stamps}" |:
      s"APIS EQUAL: ${left.apis == right.apis}" |:
      s"RELATIONS EQUAL: ${left.relations == right.relations}" |:
      "UNEQUAL" |:
      (left == right)
}