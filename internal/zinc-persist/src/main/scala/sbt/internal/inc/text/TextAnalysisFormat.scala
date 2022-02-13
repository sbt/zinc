/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc.text

import java.io._
import java.nio.file.{ Path, Paths }

import sbt.internal.inc._
import sbt.util.InterfaceUtil
import sbt.util.InterfaceUtil.{ jo2o, position, problem }
import xsbti.{ T2, UseScope, VirtualFileRef }
import xsbti.api._
import xsbti.compile._
import xsbti.compile.analysis.{ ReadWriteMappers, SourceInfo, Stamp }
import scala.collection.Seq

// A text-based serialization format for Analysis objects.
// This code has been tuned for high performance, and therefore has non-idiomatic areas.
// Please refrain from making changes that significantly degrade read/write performance on large analysis files.
object TextAnalysisFormat extends TextAnalysisFormat(ReadWriteMappers.getEmptyMappers)

class TextAnalysisFormat(val mappers: ReadWriteMappers)
    extends FormatCommons
    with RelationsTextFormat {

  private final val readMapper = mappers.getReadMapper
  private final val writeMapper = mappers.getWriteMapper

  // Some types are not required for external inspection/manipulation of the analysis file,
  // and are complex to serialize as text. So we serialize them as base64-encoded sbinary-serialized blobs.
  // TODO: This is a big performance hit. Figure out a more efficient way to serialize API objects?
  import sbinary.DefaultProtocol._
  import sbinary.Format
  import xsbti.{ Position, Problem, Severity }

  private implicit val compilationF: Format[Compilation] = CompilationFormat
  private implicit val nameHashesFormat: Format[NameHash] = {
    def read(name: String, scopeName: String, hash: Int) =
      NameHash.of(name, UseScope.valueOf(scopeName), hash)
    asProduct3(read)(a => (a.name(), a.scope().name(), a.hash()))
  }
  private implicit val companionsFomrat: Format[Companions] = CompanionsFormat
  private implicit def problemFormat: Format[Problem] =
    asProduct5(problem)(p => (p.category, p.position, p.message, p.severity, jo2o(p.rendered)))
  private implicit def positionFormat: Format[Position] =
    asProduct13(position)(p =>
      (
        jo2o(p.line),
        p.lineContent,
        jo2o(p.offset),
        jo2o(p.pointer),
        jo2o(p.pointerSpace),
        jo2o(p.sourcePath),
        jo2o(p.sourceFile),
        jo2o(p.startOffset),
        jo2o(p.endOffset),
        jo2o(p.startLine),
        jo2o(p.startColumn),
        jo2o(p.endLine),
        jo2o(p.endColumn)
      )
    )
  private implicit val severityFormat: Format[Severity] =
    wrap[Severity, Byte](_.ordinal.toByte, b => Severity.values.apply(b.toInt))
  private implicit val integerFormat: Format[Integer] =
    wrap[Integer, Int](_.toInt, Integer.valueOf)
  private implicit val analyzedClassFormat: Format[AnalyzedClass] =
    AnalyzedClassFormats.analyzedClassFormat
  private implicit def infoFormat: Format[SourceInfo] =
    wrap[SourceInfo, (Seq[Problem], Seq[Problem], Seq[String])](
      si => (si.getReportedProblems, si.getUnreportedProblems, si.getMainClasses),
      { case (a, b, c) =>
        SourceInfos.makeInfo(a.toVector, b.toVector, c.toVector)
      }
    )
  private implicit val pathFormat: Format[Path] =
    wrap[Path, String](_.toString, s => Paths.get(s))
  private implicit def fileHashFormat: Format[FileHash] =
    asProduct2((file: Path, hash: Int) => FileHash.of(file, hash))(h => (h.file, h.hash))
  private implicit def seqFormat[T](implicit optionFormat: Format[T]): Format[Seq[T]] =
    viaSeq[Seq[T], T](x => x)
  private def t2[A1, A2](a1: A1, a2: A2): T2[A1, A2] = InterfaceUtil.t2(a1 -> a2)

  // Companions portion of the API info is written in a separate entry later.
  def write(out: Writer, analysis: CompileAnalysis, setup: MiniSetup): Unit = {
    val analysis0 = analysis match { case analysis: Analysis => analysis }
    VersionF.write(out)
    // We start with writing compile setup
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
    val relations = FormatTimer.time("read relations") { RelationsF.read(in) }
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
        throw new ReadException(
          "File uses format version %s, but we are compatible with version %s only."
            .format(version, currentVersion)
        )
      }
    }
  }

  override def productsMapper = Mapper(
    (str: String) => readMapper.mapProductFile(Mapper.forFileV.read(str)),
    (file: VirtualFileRef) => Mapper.forFileV.write(writeMapper.mapProductFile(file))
  )

  override def sourcesMapper = Mapper(
    (str: String) => readMapper.mapSourceFile(Mapper.forFileV.read(str)),
    (file: VirtualFileRef) => Mapper.forFileV.write(writeMapper.mapSourceFile(file))
  )

  override def binariesMapper = Mapper(
    (str: String) => readMapper.mapBinaryFile(Mapper.forFileV.read(str)),
    (file: VirtualFileRef) => Mapper.forFileV.write(writeMapper.mapBinaryFile(file))
  )

  private[this] object StampsF {
    object Headers {
      val products = "product stamps"
      val sources = "source stamps"
      val binaries = "binary stamps"
    }

    final val productsStampsMapper = ContextAwareMapper[VirtualFileRef, Stamp](
      (f: VirtualFileRef, str: String) =>
        readMapper.mapProductStamp(f, Mapper.forStampV.read(f, str)),
      (f: VirtualFileRef, stamp: Stamp) =>
        Mapper.forStampV.write(f, writeMapper.mapProductStamp(f, stamp))
    )

    final val sourcesStampsMapper = ContextAwareMapper[VirtualFileRef, Stamp](
      (f: VirtualFileRef, str: String) =>
        readMapper.mapSourceStamp(f, Mapper.forStampV.read(f, str)),
      (f: VirtualFileRef, stamp: Stamp) =>
        Mapper.forStampV.write(f, writeMapper.mapSourceStamp(f, stamp))
    )

    final val binariesStampsMapper = ContextAwareMapper[VirtualFileRef, Stamp](
      (f: VirtualFileRef, str: String) =>
        readMapper.mapBinaryStamp(f, Mapper.forStampV.read(f, str)),
      (f: VirtualFileRef, stamp: Stamp) =>
        Mapper.forStampV.write(f, writeMapper.mapBinaryStamp(f, stamp))
    )

    def write(out: Writer, stamps: Stamps): Unit = {
      def doWriteMap(
          header: String,
          m: Map[File, Stamp],
          keyMapper: Mapper[File],
          valueMapper: ContextAwareMapper[File, Stamp]
      ) = {
        val pairsToWrite = m.map(kv => (kv._1, valueMapper.write(kv._1, m(kv._1))))
        writePairs(out)(header, pairsToWrite.toSeq, keyMapper.write, identity[String])
      }
      def doWriteMapV(
          header: String,
          m: Map[VirtualFileRef, Stamp],
          keyMapper: Mapper[VirtualFileRef],
          valueMapper: ContextAwareMapper[VirtualFileRef, Stamp]
      ) = {
        val pairsToWrite = m.map(kv => (kv._1, valueMapper.write(kv._1, m(kv._1))))
        writePairs(out)(header, pairsToWrite.toSeq, keyMapper.write, identity[String])
      }

      doWriteMapV(Headers.products, stamps.products, productsMapper, productsStampsMapper)
      doWriteMapV(Headers.sources, stamps.sources, sourcesMapper, sourcesStampsMapper)
      doWriteMapV(Headers.binaries, stamps.libraries, binariesMapper, binariesStampsMapper)
    }

    def read(in: BufferedReader): Stamps = {
      import scala.collection.immutable.TreeMap
      def doReadMap(
          expectedHeader: String,
          keyMapper: Mapper[File],
          valueMapper: ContextAwareMapper[File, Stamp]
      ): TreeMap[File, Stamp] = {
        TreeMap(readMappedPairs(in)(expectedHeader, keyMapper.read, valueMapper.read).toSeq: _*)
      }
      def doReadMapV(
          expectedHeader: String,
          keyMapper: Mapper[VirtualFileRef],
          valueMapper: ContextAwareMapper[VirtualFileRef, Stamp]
      ): TreeMap[VirtualFileRef, Stamp] = {
        import VirtualFileUtil._
        TreeMap(readMappedPairs(in)(expectedHeader, keyMapper.read, valueMapper.read).toSeq: _*)
      }

      val products = doReadMapV(Headers.products, productsMapper, productsStampsMapper)
      val sources = doReadMapV(Headers.sources, sourcesMapper, sourcesStampsMapper)
      val libraries = doReadMapV(Headers.binaries, binariesMapper, binariesStampsMapper)

      Stamps(products, sources, libraries)
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
      writeMap(out)(
        Headers.internal,
        apis.internal,
        identity[String],
        analyzedClassToString,
        inlineVals = false
      )
      writeMap(out)(
        Headers.external,
        apis.external,
        identity[String],
        analyzedClassToString,
        inlineVals = false
      )
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

    def write(
        out: Writer,
        internal: Map[String, Companions],
        external: Map[String, Companions]
    ): Unit = {
      writeMap(out)(
        Headers.internal,
        internal,
        identity[String],
        companionsToString,
        inlineVals = false
      )
      writeMap(out)(
        Headers.external,
        external,
        identity[String],
        companionsToString,
        inlineVals = false
      )
    }

    def read(in: BufferedReader): (Map[String, Companions], Map[String, Companions]) = {
      val internal = readMap(in)(Headers.internal, identity[String], stringToCompanions)
      val external = readMap(in)(Headers.external, identity[String], stringToCompanions)
      (internal, external)
    }
  }

  private[this] object SourceInfosF {
    import VirtualFileUtil._
    object Headers {
      val infos = "source infos"
    }

    val stringToSourceInfo = ObjectStringifier.stringToObj[SourceInfo] _
    val sourceInfoToString = ObjectStringifier.objToString[SourceInfo] _

    def write(out: Writer, infos: SourceInfos): Unit =
      writeMap(out)(
        Headers.infos,
        infos.allInfos,
        sourcesMapper.write,
        sourceInfoToString,
        inlineVals = false
      )
    def read(in: BufferedReader): SourceInfos =
      SourceInfos.of(readMap(in)(Headers.infos, sourcesMapper.read, stringToSourceInfo))
  }

  // Path is no serializable, so this is currently stubbed.
  private[this] object CompilationsF {
    object Headers {
      val compilations = "compilations"
    }

    val stringToCompilation = ObjectStringifier.stringToObj[Compilation] _
    val compilationToString = ObjectStringifier.objToString[Compilation] _

    def write(out: Writer, compilations: Compilations): Unit =
      writeSeq(out)(Headers.compilations, Nil, compilationToString)

    def read(in: BufferedReader): Compilations = Compilations.of(Nil)
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
      val skipApiStoring = "skip Api storing"
      val extra = "extra"
    }

    private[this] val singleOutputMode = "single"
    private[this] val multipleOutputMode = "multiple"

    val stringToFileHash = ObjectStringifier.stringToObj[FileHash] _
    val fileHashToString = ObjectStringifier.objToString[FileHash] _

    final val sourceDirMapper = Mapper(
      (str: String) => readMapper.mapSourceDir(Mapper.forPath.read(str)),
      (file: Path) => Mapper.forPath.write(writeMapper.mapSourceDir(file))
    )

    final val outputDirMapper = Mapper(
      (str: String) => readMapper.mapOutputDir(Mapper.forPath.read(str)),
      (file: Path) => Mapper.forPath.write(writeMapper.mapOutputDir(file))
    )

    final val soptionsMapper = Mapper(
      (serialized: String) => readMapper.mapScalacOption(serialized),
      (option: String) => writeMapper.mapScalacOption(option)
    )

    final val joptionsMapper = Mapper(
      (serialized: String) => readMapper.mapJavacOption(serialized),
      (option: String) => writeMapper.mapJavacOption(option)
    )

    def write(out: Writer, setup0: MiniSetup): Unit = {
      val setup = writeMapper.mapMiniSetup(setup0)
      val mode = singleOutputMode
      // just to be compatible with multipleOutputMode
      val outputAsMap = Map(Analysis.dummyOutputPath -> Analysis.dummyOutputPath)
      val mappedClasspathHash = setup.options.classpathHash
        .map(fh => fh.withFile(writeMapper.mapClasspathEntry(fh.file)))
      writeSeq(out)(Headers.outputMode, mode :: Nil, identity[String])
      writeMap(out)(Headers.outputDir, outputAsMap, sourceDirMapper.write, outputDirMapper.write)
      writeSeq(out)(Headers.classpathHash, mappedClasspathHash, fileHashToString)
      writeSeq(out)(Headers.compileOptions, setup.options.scalacOptions, soptionsMapper.write)
      writeSeq(out)(Headers.javacOptions, setup.options.javacOptions, joptionsMapper.write)
      writeSeq(out)(Headers.compilerVersion, setup.compilerVersion :: Nil, identity[String])
      writeSeq(out)(Headers.compileOrder, setup.order.name :: Nil, identity[String])
      writeSeq(out)(Headers.skipApiStoring, setup.storeApis() :: Nil, (b: Boolean) => b.toString)
      writePairs[String, String](out)(
        Headers.extra,
        setup.extra.toList map { x =>
          (x.get1, x.get2)
        },
        identity[String],
        identity[String]
      )
    }

    def read(in: BufferedReader): MiniSetup = {
      def s2b(s: String): Boolean = s.toBoolean
      val outputDirMode = readSeq(in)(Headers.outputMode, identity[String]).headOption
      val outputAsMap = readMap(in)(Headers.outputDir, sourceDirMapper.read, outputDirMapper.read)
      val classpathHash0 = readSeq(in)(Headers.classpathHash, stringToFileHash)
      val classpathHash = classpathHash0.map(h => h.withFile(readMapper.mapClasspathEntry(h.file)))
      val compileOptions = readSeq(in)(Headers.compileOptions, soptionsMapper.read)
      val javacOptions = readSeq(in)(Headers.javacOptions, joptionsMapper.read)
      val compilerVersion = readSeq(in)(Headers.compilerVersion, identity[String]).head
      val compileOrder = readSeq(in)(Headers.compileOrder, identity[String]).head
      val skipApiStoring = readSeq(in)(Headers.skipApiStoring, s2b).head
      val extra = readPairs(in)(Headers.extra, identity[String], identity[String]) map {
        case (a, b) => t2[String, String](a, b)
      }

      val output = outputDirMode match {
        case Some(s) =>
          s match {
            case `singleOutputMode` => CompileOutput(outputAsMap.values.head)
            case `multipleOutputMode` =>
              val groups = outputAsMap.iterator.map { case (src: Path, out: Path) =>
                CompileOutput.outputGroup(src, out)
              }
              CompileOutput(groups.toArray)
            case str: String => throw new ReadException("Unrecognized output mode: " + str)
          }
        case None => throw new ReadException("No output mode specified")
      }

      val original = MiniSetup.of(
        output, // note: this is a dummy value
        MiniOptions.of(classpathHash.toArray, compileOptions.toArray, javacOptions.toArray),
        compilerVersion,
        xsbti.compile.CompileOrder.valueOf(compileOrder),
        skipApiStoring,
        extra.toArray
      )

      readMapper.mapMiniSetup(original)
    }
  }

  private[this] object ObjectStringifier {
    def objToString[T](o: T)(implicit fmt: sbinary.Format[T]) = {
      val baos = new ByteArrayOutputStream()
      val out = new sbinary.JavaOutput(baos)
      FormatTimer.aggregate("sbinary write") {
        try {
          fmt.writes(out, o)
        } finally {
          baos.close()
        }
      }
      val bytes = FormatTimer.aggregate("byte copy") { baos.toByteArray }
      FormatTimer.aggregate("bytes -> base64") { Base64.factory().encode(bytes) }
    }

    def stringToObj[T](s: String)(implicit fmt: sbinary.Format[T]) = {
      val bytes = FormatTimer.aggregate("base64 -> bytes") { Base64.factory().decode(s) }
      val in = new sbinary.JavaInput(new ByteArrayInputStream(bytes))
      FormatTimer.aggregate("sbinary read") { fmt.reads(in) }
    }
  }

}
