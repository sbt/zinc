/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io._

import sbt.internal.util.Relation
import xsbti.T2
import xsbti.api._
import xsbti.compile._
import javax.xml.bind.DatatypeConverter

import sbt.util.InterfaceUtil

// A text-based serialization format for Analysis objects.
// This code has been tuned for high performance, and therefore has non-idiomatic areas.
// Please refrain from making changes that significantly degrade read/write performance on large analysis files.
object TextAnalysisFormat extends TextAnalysisFormat(AnalysisMappers.default)

class TextAnalysisFormat(override val mappers: AnalysisMappers) extends FormatCommons with RelationsTextFormat {
  // Some types are not required for external inspection/manipulation of the analysis file,
  // and are complex to serialize as text. So we serialize them as base64-encoded sbinary-serialized blobs.
  // TODO: This is a big performance hit. Figure out a more efficient way to serialize API objects?
  import sbinary.DefaultProtocol._
  import sbinary.Format
  import xsbti.{ Position, Problem, Severity }
  import sbt.util.Logger.{ m2o, position, problem }

  private implicit val compilationF: Format[Compilation] = xsbt.api.CompilationFormat
  private implicit val nameHashesFormat: Format[NameHashes] = xsbt.api.NameHashesFormat
  private implicit val companionsFomrat: Format[Companions] = xsbt.api.CompanionsFormat
  private implicit def problemFormat: Format[Problem] = asProduct4(problem _)(p => (p.category, p.position, p.message, p.severity))
  private implicit def positionFormat: Format[Position] =
    asProduct7(position _)(p => (m2o(p.line), p.lineContent, m2o(p.offset), m2o(p.pointer), m2o(p.pointerSpace), m2o(p.sourcePath), m2o(p.sourceFile)))
  private implicit val severityFormat: Format[Severity] =
    wrap[Severity, Byte](_.ordinal.toByte, b => Severity.values.apply(b.toInt))
  private implicit val integerFormat: Format[Integer] = wrap[Integer, Int](_.toInt, Integer.valueOf)
  private implicit val analyzedClassFormat: Format[AnalyzedClass] = xsbt.api.AnalyzedClassFormats.analyzedClassFormat
  private implicit def infoFormat: Format[SourceInfo] =
    wrap[SourceInfo, (Seq[Problem], Seq[Problem])](si => (si.reportedProblems, si.unreportedProblems), { case (a, b) => SourceInfos.makeInfo(a, b) })
  private implicit def fileHashFormat: Format[FileHash] = asProduct2((file: File, hash: Int) => new FileHash(file, hash))(h => (h.file, h.hash))
  private implicit def seqFormat[T](implicit optionFormat: Format[T]): Format[Seq[T]] = viaSeq[Seq[T], T](x => x)
  private def t2[A1, A2](a1: A1, a2: A2): T2[A1, A2] = InterfaceUtil.t2(a1 -> a2)

  // Companions portion of the API info is written in a separate entry later.
  def write(out: Writer, analysis: CompileAnalysis, setup: MiniSetup): Unit = {
    val analysis0 = analysis match { case analysis: Analysis => analysis }
    VersionF.write(out)
    // We start with writing compile setup which contains value of the `nameHashing`
    // flag that is needed to properly deserialize relations
    FormatTimer.time("write setup") { MiniSetupF.write(out, setup) }
    // Next we write relations because that's the part of greatest interest to external readers,
    // who can abort reading early once they're read them.
    FormatTimer.time("write relations") { RelationsF.write(out, analysis0.relations) }
    FormatTimer.time("write stamps") { StampsF.write(out, analysis0.stamps) }
    FormatTimer.time("write apis") { APIsF.write(out, analysis0.apis) }
    FormatTimer.time("write sourceinfos") { SourceInfosF.write(out, analysis0.infos) }
    FormatTimer.time("write compilations") { CompilationsF.write(out, analysis0.compilations) }
    out.flush()
  }

  // Writes the "api" portion of xsbti.api.AnalyzedClass.
  def writeCompanionMap(out: Writer, apis: APIs): Unit = {
    VersionF.write(out)
    CompanionsF.write(out, apis)
    out.flush()
  }

  // Companions portion of the API info is read from a separate file lazily.
  def read(in: BufferedReader, companionsStore: CompanionsStore): (CompileAnalysis, MiniSetup) = {
    VersionF.read(in)
    val setup = FormatTimer.time("read setup") { MiniSetupF.read(in) }
    val relations = FormatTimer.time("read relations") { RelationsF.read(in, setup.nameHashing) }
    val stamps = FormatTimer.time("read stamps") { StampsF.read(in) }
    val apis = FormatTimer.time("read apis") {
      APIsF.read(in, if (setup.storeApis) Some(companionsStore) else None)
    }
    val infos = FormatTimer.time("read sourceinfos") { SourceInfosF.read(in) }
    val compilations = FormatTimer.time("read compilations") { CompilationsF.read(in) }

    (Analysis.Empty.copy(stamps, apis, relations, infos, compilations), setup)
  }

  def readCompanionMap(in: BufferedReader): (Map[String, Companions], Map[String, Companions]) = {
    VersionF.read(in)
    CompanionsF.read(in)
  }

  private[this] object VersionF {
    val currentVersion = "6"

    def write(out: Writer): Unit = {
      out.write("format version: %s\n".format(currentVersion))
    }

    private val versionPattern = """format version: (\w+)""".r
    def read(in: BufferedReader): Unit = {
      in.readLine() match {
        case versionPattern(version) => validateVersion(version)
        case s: String               => throw new ReadException("\"format version: <version>\"", s)
        case null                    => throw new EOFException
      }
    }

    def validateVersion(version: String): Unit = {
      // TODO: Support backwards compatibility?
      if (version != currentVersion) {
        throw new ReadException("File uses format version %s, but we are compatible with version %s only.".format(version, currentVersion))
      }
    }
  }

  private[this] object StampsF {
    object Headers {
      val products = "product stamps"
      val sources = "source stamps"
      val binaries = "binary stamps"
    }

    def write(out: Writer, stamps: Stamps): Unit = {
      def doWriteMap[V](header: String, m: Map[File, V], keyMapper: Mapper[File], valueMapper: ContextAwareMapper[File, V]) = {
        val pairsToWrite = m.keys.toSeq.sorted map (k => (k, valueMapper.write(k, m(k))))
        writePairs(out)(header, pairsToWrite, keyMapper.write, identity[String])
      }

      doWriteMap(Headers.products, stamps.products, mappers.productMapper, mappers.productStampMapper)
      doWriteMap(Headers.sources, stamps.sources, mappers.sourceMapper, mappers.sourceStampMapper)
      doWriteMap(Headers.binaries, stamps.binaries, mappers.binaryMapper, mappers.binaryStampMapper)
    }

    def read(in: BufferedReader): Stamps = {
      def doReadMap[V](expectedHeader: String, keyMapper: Mapper[File], valueMapper: ContextAwareMapper[File, V]) =
        readMappedPairs(in)(expectedHeader, keyMapper.read, valueMapper.read).toMap

      val products = doReadMap(Headers.products, mappers.productMapper, mappers.productStampMapper)
      val sources = doReadMap(Headers.sources, mappers.sourceMapper, mappers.sourceStampMapper)
      val binaries = doReadMap(Headers.binaries, mappers.binaryMapper, mappers.binaryStampMapper)

      Stamps(products, sources, binaries)
    }
  }

  private[this] object APIsF {
    object Headers {
      val internal = "internal apis"
      val external = "external apis"
    }

    val stringToAnalyzedClass = ObjectStringifier.stringToObj[AnalyzedClass] _
    val analyzedClassToString = ObjectStringifier.objToString[AnalyzedClass] _

    def write(out: Writer, apis: APIs): Unit = {
      writeMap(out)(Headers.internal, apis.internal, identity[String], analyzedClassToString, inlineVals = false)
      writeMap(out)(Headers.external, apis.external, identity[String], analyzedClassToString, inlineVals = false)
      FormatTimer.close("bytes -> base64")
      FormatTimer.close("byte copy")
      FormatTimer.close("sbinary write")
    }

    @inline final def lzy[T](t: => T) = SafeLazyProxy(t)
    def read(in: BufferedReader, companionsStore: Option[CompanionsStore]): APIs = {
      val internal = readMap(in)(Headers.internal, identity[String], stringToAnalyzedClass)
      val external = readMap(in)(Headers.external, identity[String], stringToAnalyzedClass)
      FormatTimer.close("base64 -> bytes")
      FormatTimer.close("sbinary read")
      companionsStore match {
        case Some(companionsStore) =>
          val companions: Lazy[(Map[String, Companions], Map[String, Companions])] =
            lzy(companionsStore.getUncaught())

          APIs(
            internal map { case (k, v) => k -> v.withApi(lzy(companions.get._1(k))) },
            external map { case (k, v) => k -> v.withApi(lzy(companions.get._2(k))) }
          )
        case _ => APIs(internal, external)
      }

    }
  }

  private[this] object CompanionsF {
    object Headers {
      val internal = "internal companions"
      val external = "external companions"
    }

    val stringToCompanions = ObjectStringifier.stringToObj[Companions] _
    val companionsToString = ObjectStringifier.objToString[Companions] _

    def write(out: Writer, apis: APIs): Unit = {
      val internal = apis.internal map { case (k, v) => k -> v.api }
      val external = apis.external map { case (k, v) => k -> v.api }
      write(out, internal, external)
    }

    def write(out: Writer, internal: Map[String, Companions], external: Map[String, Companions]): Unit = {
      writeMap(out)(Headers.internal, internal, identity[String], companionsToString, inlineVals = false)
      writeMap(out)(Headers.external, external, identity[String], companionsToString, inlineVals = false)
    }

    def read(in: BufferedReader): (Map[String, Companions], Map[String, Companions]) = {
      val internal = readMap(in)(Headers.internal, identity[String], stringToCompanions)
      val external = readMap(in)(Headers.external, identity[String], stringToCompanions)
      (internal, external)
    }
  }

  private[this] object SourceInfosF {
    object Headers {
      val infos = "source infos"
    }

    val stringToSourceInfo = ObjectStringifier.stringToObj[SourceInfo] _
    val sourceInfoToString = ObjectStringifier.objToString[SourceInfo] _

    def write(out: Writer, infos: SourceInfos): Unit =
      writeMap(out)(Headers.infos, infos.allInfos, mappers.sourceMapper.write, sourceInfoToString, inlineVals = false)
    def read(in: BufferedReader): SourceInfos =
      SourceInfos.make(readMap(in)(Headers.infos, mappers.sourceMapper.read, stringToSourceInfo))
  }

  private[this] object CompilationsF {
    object Headers {
      val compilations = "compilations"
    }

    val stringToCompilation = ObjectStringifier.stringToObj[Compilation] _
    val compilationToString = ObjectStringifier.objToString[Compilation] _

    def write(out: Writer, compilations: Compilations): Unit =
      writeSeq(out)(Headers.compilations, compilations.allCompilations, compilationToString)

    def read(in: BufferedReader): Compilations = Compilations.make(
      readSeq[Compilation](in)(Headers.compilations, stringToCompilation)
    )
  }

  private[this] object MiniSetupF {
    object Headers {
      val outputMode = "output mode"
      val outputDir = "output directories"
      val classpathHash = "classpath hash"
      val compileOptions = "compile options"
      val javacOptions = "javac options"
      val compilerVersion = "compiler version"
      val compileOrder = "compile order"
      val nameHashing = "name hashing"
      val skipApiStoring = "skip Api storing"
      val extra = "extra"
    }

    private[this] val singleOutputMode = "single"
    private[this] val multipleOutputMode = "multiple"

    val stringToFileHash = ObjectStringifier.stringToObj[FileHash] _
    val fileHashToString = ObjectStringifier.objToString[FileHash] _

    def write(out: Writer, setup: MiniSetup): Unit = {
      val (mode, outputAsMap) = setup.output match {
        case s: SingleOutput =>
          // just to be compatible with multipleOutputMode
          val ignored = s.outputDirectory
          (singleOutputMode, Map(ignored -> s.outputDirectory))
        case m: MultipleOutput => (multipleOutputMode, m.outputGroups.map(x => x.sourceDirectory -> x.outputDirectory).toMap)
      }

      writeSeq(out)(Headers.outputMode, mode :: Nil, identity[String])
      writeMap(out)(Headers.outputDir, outputAsMap, mappers.sourceDirMapper.write, mappers.outputDirMapper.write)
      writeSeq(out)(Headers.classpathHash, setup.options.classpathHash, fileHashToString) // TODO!
      writeSeq(out)(Headers.compileOptions, setup.options.scalacOptions, mappers.scalacOptions.write)
      writeSeq(out)(Headers.javacOptions, setup.options.javacOptions, mappers.javacOptions.write)
      writeSeq(out)(Headers.compilerVersion, setup.compilerVersion :: Nil, identity[String])
      writeSeq(out)(Headers.compileOrder, setup.order.name :: Nil, identity[String])
      writeSeq(out)(Headers.nameHashing, setup.nameHashing :: Nil, (b: Boolean) => b.toString)
      writeSeq(out)(Headers.skipApiStoring, setup.storeApis() :: Nil, (b: Boolean) => b.toString)
      writePairs[String, String](out)(Headers.extra, setup.extra.toList map { x => (x.get1, x.get2) }, identity[String], identity[String])
    }

    def read(in: BufferedReader): MiniSetup = {
      def s2b(s: String): Boolean = s.toBoolean
      val outputDirMode = readSeq(in)(Headers.outputMode, identity[String]).headOption
      val outputAsMap = readMap(in)(Headers.outputDir, mappers.sourceDirMapper.read, mappers.outputDirMapper.read)
      val classpathHash = readSeq(in)(Headers.classpathHash, stringToFileHash) // TODO
      val compileOptions = readSeq(in)(Headers.compileOptions, mappers.scalacOptions.read)
      val javacOptions = readSeq(in)(Headers.javacOptions, mappers.javacOptions.read)
      val compilerVersion = readSeq(in)(Headers.compilerVersion, identity[String]).head
      val compileOrder = readSeq(in)(Headers.compileOrder, identity[String]).head
      val nameHashing = readSeq(in)(Headers.nameHashing, s2b).head
      val skipApiStoring = readSeq(in)(Headers.skipApiStoring, s2b).head
      val extra = readPairs(in)(Headers.extra, identity[String], identity[String]) map { case (a, b) => t2[String, String](a, b) }

      val output = outputDirMode match {
        case Some(s) => s match {
          case `singleOutputMode` => new SingleOutput {
            val outputDirectory = outputAsMap.values.head
          }
          case `multipleOutputMode` => new MultipleOutput {
            val outputGroups: Array[MultipleOutput.OutputGroup] = outputAsMap.toArray.map {
              case (src: File, out: File) => new MultipleOutput.OutputGroup {
                val sourceDirectory = src
                val outputDirectory = out
                override def toString = s"OutputGroup($src -> $out)"
              }
            }
            override def toString = s"MultipleOuput($outputGroups)"
          }
          case str: String => throw new ReadException("Unrecognized output mode: " + str)
        }
        case None => throw new ReadException("No output mode specified")
      }

      new MiniSetup(output, new MiniOptions(classpathHash.toArray, compileOptions.toArray, javacOptions.toArray), compilerVersion,
        xsbti.compile.CompileOrder.valueOf(compileOrder), nameHashing, skipApiStoring, extra.toArray)
    }
  }

  private[this] object ObjectStringifier {
    def objToString[T](o: T)(implicit fmt: sbinary.Format[T]) = {
      val baos = new ByteArrayOutputStream()
      val out = new sbinary.JavaOutput(baos)
      FormatTimer.aggregate("sbinary write") { try { fmt.writes(out, o) } finally { baos.close() } }
      val bytes = FormatTimer.aggregate("byte copy") { baos.toByteArray }
      FormatTimer.aggregate("bytes -> base64") { DatatypeConverter.printBase64Binary(bytes) }
    }

    def stringToObj[T](s: String)(implicit fmt: sbinary.Format[T]) = {
      val bytes = FormatTimer.aggregate("base64 -> bytes") { DatatypeConverter.parseBase64Binary(s) }
      val in = new sbinary.JavaInput(new ByteArrayInputStream(bytes))
      FormatTimer.aggregate("sbinary read") { fmt.reads(in) }
    }
  }

}
